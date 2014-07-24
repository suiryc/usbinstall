package usbinstall

import grizzled.slf4j.Logging
import java.net.URL
import java.util.ResourceBundle
import javafx.fxml.{FXML, Initializable}
import javafx.geometry.Insets
import javafx.scene.control.{Label, Tab, TabPane}
import javafx.scene.layout.{AnchorPane, GridPane, Priority, VBox}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import suiryc.scala.concurrent.{Cancellable, CancellableFuture, Cancelled}
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.Subscription
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.log.ThresholdLogLinePatternWriter
import usbinstall.os.{OSInstall, OSKind}
import usbinstall.settings.{InstallSettings, Settings}


class InstallController
  extends Initializable
  with HasEventSubscriptions
  with UseStepPane
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

  protected var cancellableFuture: CancellableFuture[Unit] = _

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

  override def setStepPane(stepPane: StepPane) {
    this.stepPane = stepPane

    /* Note: since we access stepPane upon completion, we need to set it first
     * and cannot start installing upon 'initialize'.
     */
    cancellableFuture = CancellableFuture(installTask(_))
    cancellableFuture.future.onComplete {
      case Failure(e) =>
        error(s"Task failed", e)
        USBInstall.removeLogWriter(installLogWriter)
        JFXSystem.schedule(stepPane.previous.disable = false)

      case Success(_) =>
        info(s"Task succeeded")
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
  }

  private def installTask(cancellable: Cancellable) {

    def checkCancelled() =
      cancellable.check {
        activityArea.write("Cancelled")
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

    /* XXX - handle issues (skip/stop) */
    val (notsyslinux, syslinux) = Settings.core.oses.partition(_.kind != OSKind.Syslinux)
    val oses = notsyslinux ::: syslinux
    val (previousTab, previousLogWriter) = oses.foldLeft[(Tab, ThresholdLogLinePatternWriter)](installTab, installLogWriter) { (previous, settings) =>
      val (previousTab, previousLogWriter) = previous
      val next = if (settings.enabled) {
        val osActivity = new LogArea()
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

        Some(osTab, osLogWriter)
      } else None

      def resetAppender() {
        switchLogWriter(next map(_._2) getOrElse(previousLogWriter), installLogWriter)
      }

      try {
        val os = OSInstall(settings, ui, checkCancelled)

        OSInstall.install(os)
      }
      catch {
        case e @ Cancelled =>
          resetAppender()
          throw e

        case e: Throwable =>
          error(s"Failed to install ${settings.label}: ${e.getMessage}", e)
          resetAppender()
          throw e
      }
      finally {
        ui.osActivity = None
      }

      next getOrElse(previous)
    }

    switchLogWriter(previousLogWriter, installLogWriter)

    /* Only get back to initial tab if previous one is still selected */
    if (logPanes.getSelectionModel().getSelectedItem() eq previousTab) JFXSystem.schedule {
      logPanes.getSelectionModel().select(installTab)
    }
  }

  def onCancel() {
    /* Note: we are in the JavaFX thread */
    ui.activity("Cancelling ...")
    stepPane.next.disable = true
    cancellableFuture.cancel()
  }

  def onDone() {
    import javafx.stage.WindowEvent
    /* Note: we are in the JavaFX thread */
    USBInstall.stage.fireEvent(new WindowEvent(null, WindowEvent.WINDOW_CLOSE_REQUEST))
  }

}
