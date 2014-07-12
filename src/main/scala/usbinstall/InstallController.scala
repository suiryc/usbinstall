package usbinstall

import grizzled.slf4j.Logging
import java.net.URL
import java.util.ResourceBundle
import javafx.fxml.{FXML, Initializable}
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.layout.{GridPane, Priority, VBox}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import suiryc.scala.concurrent.{Cancellable, CancellableFuture, Cancelled}
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.LogArea
import usbinstall.os.{OSInstall, OSKind}
import usbinstall.settings.{InstallSettings, Settings}
import usbinstall.util.DebugStage


class InstallController
  extends Initializable
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

  protected var stepPane: StepPane = _

  /* Note: we need to wait for 'initialize' to get the JavaFX controls. */

  protected var ui: InstallUI = _

  protected var cancellableFuture: CancellableFuture[Unit] = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    val appender = USBInstall.newAppender(List(DebugStage.logAreaWriter(activityArea)))
    USBInstall.addAppender(appender)

    ui = new InstallUI(step, action, activityArea)

    cancellableFuture = CancellableFuture(installTask(_))
    cancellableFuture.future.onComplete {
      case Failure(e) =>
        error(s"Task failed", e)
        USBInstall.detachAppender(appender)
        JFXSystem.schedule(stepPane.previous.disable = false)

      case Success(_) =>
        info(s"Task succeeded")
        USBInstall.detachAppender(appender)
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

  override def setStepPane(stepPane: StepPane) {
    this.stepPane = stepPane
  }

  private def installTask(cancellable: Cancellable) {

    def checkCancelled() =
      cancellable.check {
        JFXSystem.schedule(activityArea.appendLine("Cancelled"))
      }

    ui.activity(s"Temp path[${InstallSettings.pathTemp}]")
    ui.activity(s"ISO mount path[${InstallSettings.pathMountISO}]")
    ui.activity(s"Partition mount path[${InstallSettings.pathMountPartition}]")

    /* XXX - handle issues (skip/stop) */
    val (notsyslinux, syslinux) = Settings.core.oses.partition(_.kind != OSKind.Syslinux)
    val oses = notsyslinux ::: syslinux
    oses foreach { settings =>
      try {
        val os = OSInstall(settings, ui, checkCancelled)

        OSInstall.install(os)
      }
      catch {
        case e @ Cancelled =>
          throw e

        case e: Throwable =>
          error(s"Failed to install ${settings.label}: ${e.getMessage}", e)
          throw e
      }
    }
  }

  private def checkCancelled(cancellable: Cancellable) =
    cancellable.check {
      JFXSystem.schedule(activityArea.appendLine("Cancelled"))
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
