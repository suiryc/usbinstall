package usbinstall.controllers

import grizzled.slf4j.Logging
import java.net.URL
import java.util.ResourceBundle
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.geometry.Insets
import javafx.scene.{Parent, Scene}
import javafx.scene.control.{Label, Tab, TabPane}
import javafx.scene.layout.{AnchorPane, GridPane, Priority, VBox}
import javafx.stage.{Modality, Stage, Window}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import suiryc.scala.concurrent.{Cancellable, CancellableFuture, Cancelled}
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.Subscription
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.javafx.stage.{Stages => sfxStages}
import suiryc.scala.log.ThresholdLogLinePatternWriter
import usbinstall.{
  HasEventSubscriptions,
  InstallationException,
  InstallUI,
  Stages,
  StepPane,
  UseStepPane,
  USBInstall
}
import usbinstall.os.{OSInstall, OSKind}
import usbinstall.settings.{ErrorAction, InstallSettings, Settings}


class InstallController
  extends Initializable
  with UseStepPane
  with SettingsClearedListener
  with HasEventSubscriptions
  with Logging
{

  @FXML
  protected var vbox: VBox = _

  @FXML
  protected var grid: GridPane = _

  @FXML
  protected var step: Label = _

  @FXML
  protected var action: Label = _

  @FXML
  protected var activityArea: LogArea = _

  @FXML
  protected var logPanes: TabPane = _

  @FXML
  protected var installTab: Tab = _

  protected var stepPane: StepPane = _

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */

  /* Note: we need to wait for 'initialize' to get the JavaFX controls. */

  protected var ui: InstallUI = _

  protected var cancellableFuture: CancellableFuture[List[String]] = _

  protected var installLogWriter: ThresholdLogLinePatternWriter = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    installLogWriter = activityArea.msgWriter
    installLogWriter.setPattern(Settings.core.logInstallPattern)
    USBInstall.addLogWriter(installLogWriter)
    installLogWriter.setThreshold(Settings.core.logInstallThreshold().level)
    subscriptions ::= Settings.core.logInstallThreshold.listen { v =>
      installLogWriter.setThreshold(v.level)
    }

    ui = new InstallUI(step, action, activityArea, None)

    subscriptions ::= USBInstall.stage.widthProperty().listen { width =>
      logPanes.setMaxWidth(width.asInstanceOf[Double])
    }
  }

  override def canClearSettings() = false

  override def settingsCleared(source: Window) {
    Stages.errorStage(Option(source), "Settings cleared", Some("Something unexpected happened"),
      "Settings have been cleared while it should not be possible.")
  }

  private def taskDone() {
    USBInstall.removeLogWriter(installLogWriter)

    /* First enable 'Previous' and disable 'Cancel' */
    JFXSystem.schedule {
      stepPane.previous.disable = false
      stepPane.next.disable = true
    }
    /* Then replace 'Cancel' by 'Done' */
    JFXSystem.schedule {
      stepPane.next.label = "Done"
      stepPane.next.onTrigger = () => {
        onDone()
        true
      }
      stepPane.next.disable = false
    }
  }

  private def taskFailed(ex: Throwable) {
    val (log, notified) = ex match {
      case _: Cancelled =>
        /* Activity area already notified */
        Stages.warningStage(None, "Installation failed", None, "Installation was cancelled")
        (false, true)

      case InstallationException(_, _, notified) =>
        (true, notified)

      case _ =>
        (true, false)
    }

    if (log)
      error(s"Installation failed", ex)
    if (!notified)
      Stages.errorStage(None, "Installation failed", None, ex)

    taskDone()
  }

  override def setStepPane(stepPane: StepPane) {
    this.stepPane = stepPane

    /* Note: since we access stepPane upon completion, we need to set it first
     * and cannot start installing upon 'initialize'.
     * In case an error message needs to be shown immediately, it is best to
     * wait for this stage to be shown before starting installing.
     */
    def install() {
      cancellableFuture = CancellableFuture(installTask(_))
      cancellableFuture.future.onComplete {
        case Failure(ex) =>
          taskFailed(ex)

        case Success(failedOSes) =>
          info(s"Task ended")
          taskDone()

          if (failedOSes.isEmpty) {
            Stages.infoStage(None, "Installation done", None, "Installation ended without errors")
          }
          else {
            Stages.warningStage(None, "Installation done", None,
              s"Installation ended.\n\nThe following elements failed:\n${failedOSes.mkString(", ")}")
          }
      }
    }

    USBInstall.stage.showingProperty().listen2 { (subscription, showing) =>
      /* Note: the stage content is created before hiding the previous one, so
       * we get hiding first, then showing.
       */
      if (showing) {
        if (USBInstall.stage.getScene() eq vbox.getScene()) {
          install()
        }
        else {
          /* Will probably never happen, but we don't want to install if the
           * stage scene is not the expected one.
           */
          Stages.warningStage(None, "Unexpected situation", None,
            "Displayed window does not appear to be the expected one (installation)!")
        }
        subscription.unsubscribe()
      }
    }
  }

  private def installTask(cancellable: Cancellable): List[String] = {

    def checkCancelled() =
      cancellable.check {
        activityArea.write("Cancelled")
        ui.activity("Cancelled")
      }

    def switchLogWriter(previous: ThresholdLogLinePatternWriter, next: ThresholdLogLinePatternWriter) {
      if (!(next eq previous)) {
        USBInstall.addLogWriter(next)
        USBInstall.removeLogWriter(previous)
      }
    }

    ui.activity(s"Temp path[${InstallSettings.pathTemp}]")
    ui.activity(s"ISO mount path[${InstallSettings.pathMountISO}]")
    ui.activity(s"Partition mount path[${InstallSettings.pathMountPartition}]")

    val (notsyslinux, syslinux) = Settings.core.oses.partition(_.kind != OSKind.Syslinux)
    val oses = notsyslinux ::: syslinux
    val (previousTab, previousLogWriter, failedOses) =
      oses.foldLeft[(Tab, ThresholdLogLinePatternWriter, List[String])](installTab, installLogWriter, Nil) { (previous, settings) =>
      val (previousTab, previousLogWriter, previousFailedOSes) = previous

      if (settings.enabled) {
        val osActivity = new LogArea()
        osActivity.setWrapText(true)
        ui.osActivity = Some(osActivity)

        val osLogWriter = osActivity.msgWriter
        osLogWriter.setPattern(Settings.core.logInstallPattern)
        osLogWriter.setThreshold(Settings.core.logInstallThreshold().level)
        subscriptions ::= Settings.core.logInstallThreshold.listen { v =>
          osLogWriter.setThreshold(v.level)
        }
        switchLogWriter(previousLogWriter, osLogWriter)

        val osTab = new Tab(settings.label)
        JFXSystem.schedule {
          val pane = new AnchorPane(osActivity)
          AnchorPane.setTopAnchor(osActivity, 10)
          AnchorPane.setRightAnchor(osActivity, 10)
          AnchorPane.setBottomAnchor(osActivity, 10)
          AnchorPane.setLeftAnchor(osActivity, 10)

          osTab.setContent(pane)
          logPanes.getTabs().add(osTab)
          /* Only select new tab if previous one is still selected */
          if (logPanes.getSelectionModel().getSelectedItem() eq previousTab)
            logPanes.getSelectionModel().select(osTab)
        }

        def resetAppender() {
          switchLogWriter(osLogWriter, installLogWriter)
        }

        val next = try {
          val os = OSInstall(settings, ui, checkCancelled)

          OSInstall.install(os)
          (osTab, osLogWriter, previousFailedOSes)
        }
        catch {
          case e: Cancelled =>
            resetAppender()
            throw e

          case e: Throwable =>
            error(s"Failed to install ${settings.label}: ${e.getMessage}", e)
            resetAppender()

            def doSkip() =
              (osTab, osLogWriter, previousFailedOSes :+ settings.label)

            Settings.core.componentInstallError() match {
              case ErrorAction.Ask =>
                Stages.errorStage(None, "Installation failed", Some(s"Failed to install ${settings.label}"), e)
                val action = JFXSystem.await(askOnFailure())
                if (action != ErrorAction.Skip)
                  throw new InstallationException(s"Failed to install ${settings.label}", e, true)
                doSkip()

              case ErrorAction.Stop =>
                throw new InstallationException(s"Failed to install ${settings.label}", e)

              case ErrorAction.Skip =>
                /* Nothing to do except go to next OS */
                doSkip()
            }
        }
        finally {
          ui.osActivity = None
        }

        next
      }
      else previous
    }

    switchLogWriter(previousLogWriter, installLogWriter)

    /* Only get back to initial tab if previous one is still selected */
    if (logPanes.getSelectionModel().getSelectedItem() eq previousTab) JFXSystem.schedule {
      logPanes.getSelectionModel().select(installTab)
    }

    failedOses
  }

  private def askOnFailure(): ErrorAction.Value = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/installFailure.fxml"))
    val options = loader.load[Parent]()
    val controller = loader.getController[InstallFailureController]()

    val stage = new Stage
    stage.setTitle("Installation failure")
    stage.setScene(new Scene(options))
    stage.initModality(Modality.WINDOW_MODAL)
    stage.initOwner(vbox.getScene().getWindow())
    /* Track dimension as soon as shown, and unlisten once done */
    val subscription = stage.showingProperty().listen { showing =>
      if (showing) sfxStages.trackMinimumDimensions(stage)
    }
    stage.showAndWait()
    subscription.unsubscribe()

    val action = controller.getAction()
    if (controller.getAsDefault())
      Settings.core.componentInstallError() = action

    action
  }

  def onCancel() {
    /* Note: we are in the JavaFX thread */
    ui.activity("Cancelling ...")
    activityArea.write("Cancelling ...")
    stepPane.next.disable = true
    Option(cancellableFuture).fold {
      taskFailed(Cancelled())
    } {
      _.cancel()
    }
  }

  def onDone() {
    import javafx.stage.WindowEvent
    /* Note: we are in the JavaFX thread */
    USBInstall.stage.fireEvent(new WindowEvent(null, WindowEvent.WINDOW_CLOSE_REQUEST))
  }

}
