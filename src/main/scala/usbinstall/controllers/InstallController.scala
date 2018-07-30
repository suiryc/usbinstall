package usbinstall.controllers

import com.typesafe.scalalogging.StrictLogging
import java.net.URL
import java.util.ResourceBundle
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.scene.{Parent, Scene}
import javafx.scene.control.{Label, Tab, TabPane, TextArea}
import javafx.scene.layout.{AnchorPane, GridPane, VBox}
import javafx.stage.{Modality, Stage, Window}
import scala.util.{Failure, Success}
import suiryc.scala.concurrent.{Cancellable, CancellableFuture, Cancelled}
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.{Dialogs, LogArea}
import suiryc.scala.javafx.stage.{Stages ⇒ sfxStages}
import suiryc.scala.log.ThresholdLogLinePatternWriter
import usbinstall.{HasEventSubscriptions, InstallUI, InstallationException, StepPane, USBInstall, UseStepPane}
import usbinstall.os.{OSInstall, OSKind}
import usbinstall.settings.{ErrorAction, InstallSettings, Settings}


class InstallController
  extends Initializable
  with UseStepPane
  with SettingsClearedListener
  with HasEventSubscriptions
  with StrictLogging
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
  protected var activityArea: TextArea = _

  @FXML
  protected var logPanes: TabPane = _

  @FXML
  protected var installTab: Tab = _

  protected var activityLogArea: LogArea = _

  protected var stepPane: StepPane = _

  // Note: subscriptions on external object need to be cancelled for
  // pane/scene to be GCed.

  // Note: we need to wait for 'initialize' to get the JavaFX controls.

  protected var ui: InstallUI = _

  protected var cancellableFuture: CancellableFuture[List[String]] = _

  protected var installLogWriter: ThresholdLogLinePatternWriter = _

  private val profile = InstallSettings.profile.get.get

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    activityLogArea = LogArea(activityArea)
    installLogWriter = activityLogArea.msgWriter
    installLogWriter.setPattern(Settings.core.logInstallPattern)
    USBInstall.addLogWriter(installLogWriter)
    installLogWriter.setThreshold(Settings.core.logInstallThreshold().level)
    subscriptions ::= Settings.core.logInstallThreshold.listen { v =>
      installLogWriter.setThreshold(v.level)
    }

    ui = new InstallUI(step, action, activityLogArea, None)

    subscriptions ::= USBInstall.stage.widthProperty().listen { width =>
      logPanes.setMaxWidth(width.asInstanceOf[Double])
    }
  }

  override def canClearSettings = false

  override def settingsCleared(source: Window) {
    Dialogs.error(
      owner = Some(source),
      title = Some("Settings cleared"),
      headerText = Some("Something unexpected happened"),
      contentText = Some("Settings have been cleared while it should not be possible.")
    )
    ()
  }

  private def taskDone() {
    USBInstall.removeLogWriter(installLogWriter)

    // First enable 'Previous' and disable 'Cancel'
    JFXSystem.schedule {
      stepPane.previous.disable = false
      stepPane.next.disable = true
    }
    // Then replace 'Cancel' by 'Done'
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
        // Activity area already notified
        Dialogs.warning(
          owner = Some(USBInstall.stage),
          title = Some("Installation failed"),
          contentText = Some("Installation was cancelled")
        )
        (false, true)

      case InstallationException(_, _, n) =>
        (true, n)

      case _ =>
        (true, false)
    }

    if (log)
      logger.error(s"Installation failed", ex)
    if (!notified) {
      Dialogs.error(
        owner = Some(USBInstall.stage),
        title = Some("Installation failed"),
        ex = Some(ex)
      )
    }

    taskDone()
  }

  override def setStepPane(stepPane: StepPane) {
    this.stepPane = stepPane

    // Note: since we access stepPane upon completion, we need to set it first
    // and cannot start installing upon 'initialize'.
    // In case an error message needs to be shown immediately, it is best to
    // wait for this stage to be shown before starting installing.
    def install() {
      import scala.concurrent.ExecutionContext.Implicits.global
      cancellableFuture = CancellableFuture(installTask)
      cancellableFuture.future.onComplete {
        case Failure(ex) =>
          taskFailed(ex)

        case Success(failedOSes) =>
          logger.info(s"Task ended")
          taskDone()

          if (failedOSes.isEmpty) {
            Dialogs.information(
              owner = Some(USBInstall.stage),
              title = Some("Installation done"),
              contentText = Some("Installation ended without errors")
            )
          }
          else {
            Dialogs.warning(
              owner = Some(USBInstall.stage),
              title = Some("Installation done"),
              contentText = Some(s"Installation ended.\n\nThe following elements failed:\n${failedOSes.mkString(", ")}")
            )
          }
      }
    }

    USBInstall.stage.showingProperty().listen2 { (subscription, showing) =>
      // Note: the stage content is created before hiding the previous one, so
      // we get hiding first, then showing.
      if (showing) {
        if (USBInstall.stage.getScene eq vbox.getScene) {
          install()
        }
        else {
          // Will probably never happen, but we don't want to install if the
          // stage scene is not the expected one.
          Dialogs.warning(
            owner = Some(USBInstall.stage),
            title = Some("Unexpected situation"),
            contentText = Some("Displayed window does not appear to be the expected one (installation)!")
          )
        }
        subscription.cancel()
      }
    }
    ()
  }

  private def installTask(cancellable: Cancellable): List[String] = {

    def checkCancelled(): Unit =
      cancellable.check {
        activityLogArea.write("Cancelled")
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

    val (notsyslinux, syslinux) = profile.oses.partition(_.kind != OSKind.Syslinux)
    val oses = notsyslinux ::: syslinux
    val (previousTab, previousLogWriter, failedOses) =
      oses.foldLeft[(Tab, ThresholdLogLinePatternWriter, List[String])]((installTab, installLogWriter, Nil)) { (previous, settings) =>
      val (previousTab, previousLogWriter, previousFailedOSes) = previous

      if (settings.isSelected) {
        val osActivity = new TextArea()
        osActivity.setWrapText(true)
        val osLogArea = LogArea(osActivity)
        ui.osActivity = Some(osLogArea)

        val osLogWriter = osLogArea.msgWriter
        osLogWriter.setPattern(Settings.core.logInstallPattern)
        osLogWriter.setThreshold(Settings.core.logInstallThreshold().level)
        subscriptions ::= Settings.core.logInstallThreshold.listen { v =>
          osLogWriter.setThreshold(v.level)
        }
        switchLogWriter(previousLogWriter, osLogWriter)

        val osTab = new Tab(settings.label)
        JFXSystem.schedule {
          val pane = new AnchorPane(osActivity)
          AnchorPane.setTopAnchor(osActivity, 10.0)
          AnchorPane.setRightAnchor(osActivity, 10.0)
          AnchorPane.setBottomAnchor(osActivity, 10.0)
          AnchorPane.setLeftAnchor(osActivity, 10.0)

          osTab.setContent(pane)
          logPanes.getTabs.add(osTab)
          // Only select new tab if previous one is still selected
          if (logPanes.getSelectionModel.getSelectedItem eq previousTab)
            logPanes.getSelectionModel.select(osTab)
        }

        def resetAppender() {
          switchLogWriter(osLogWriter, installLogWriter)
        }

        val next = try {
          val os = OSInstall(settings, ui, () => checkCancelled())

          OSInstall.install(profile, os)
          (osTab, osLogWriter, previousFailedOSes)
        } catch {
          case ex: Cancelled =>
            resetAppender()
            throw ex

          case ex: Exception =>
            logger.error(s"Failed to install ${settings.label}: ${ex.getMessage}", ex)
            resetAppender()

            def doSkip() =
              (osTab, osLogWriter, previousFailedOSes :+ settings.label)

            Settings.core.componentInstallError() match {
              case ErrorAction.Ask =>
                Dialogs.error(
                  owner = Some(USBInstall.stage),
                  title = Some("Installation failed"),
                  contentText = Some(s"Failed to install ${settings.label}"),
                  ex = Some(ex)
                )
                val action = JFXSystem.await(askOnFailure())
                if (action != ErrorAction.Skip)
                  throw InstallationException(s"Failed to install ${settings.label}", ex, notified = true)
                doSkip()

              case ErrorAction.Stop =>
                throw InstallationException(s"Failed to install ${settings.label}", ex)

              case ErrorAction.Skip =>
                // Nothing to do except go to next OS
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

    // Only get back to initial tab if previous one is still selected
    if (logPanes.getSelectionModel.getSelectedItem eq previousTab) JFXSystem.schedule {
      logPanes.getSelectionModel.select(installTab)
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
    sfxStages.initOwner(stage, vbox.getScene.getWindow)

    sfxStages.onStageReady(stage, first = false) {
      sfxStages.setMinimumDimensions(stage)
    }(JFXSystem.dispatcher)
    stage.showAndWait()

    val action = controller.getAction
    if (controller.getAsDefault)
      Settings.core.componentInstallError() = action

    action
  }

  def onCancel() {
    // Note: we are in the JavaFX thread
    ui.activity("Cancelling ...")
    activityLogArea.write("Cancelling ...")
    stepPane.next.disable = true
    Option(cancellableFuture).fold {
      taskFailed(Cancelled())
    } {
      _.cancel()
    }
  }

  def onDone() {
    import javafx.stage.WindowEvent
    // Note: we are in the JavaFX thread
    USBInstall.stage.fireEvent(new WindowEvent(null, WindowEvent.WINDOW_CLOSE_REQUEST))
  }

}
