package usbinstall

import grizzled.slf4j.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scalafx.geometry.Insets
import scalafx.scene.control.Label
import scalafx.scene.layout.{GridPane, Priority, VBox}
import scalafxml.core.macros.sfxml
import suiryc.scala.concurrent.{Cancellable, CancellableFuture, Cancelled}
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.LogArea
import usbinstall.os.{OSInstall, OSKind}
import usbinstall.settings.{InstallSettings, Settings}
import usbinstall.util.DebugStage


@sfxml
class InstallController(
  private val vbox: VBox,
  private val grid: GridPane,
  private val step: Label,
  private val action: Label,
  private val stepPane: StepPane
) extends HasCancel with Logging
{

  val activityArea = new LogArea {
    margin = Insets(0, 10, 10, 10)
    vgrow = Priority.ALWAYS
  }
  vbox.content.add(activityArea)

  val appender = USBInstall.newAppender(List(DebugStage.logAreaWriter(activityArea)))
  USBInstall.addAppender(appender)
  val ui = new InstallUI(step, action, activityArea)

  val cancellableFuture = CancellableFuture(installTask(_))
  cancellableFuture.future.onComplete {
    case Failure(e) =>
      error(s"Task failed", e)
      USBInstall.detachAppender(appender)

    case Success(_) =>
      info(s"Task succeeded")
      USBInstall.detachAppender(appender)
      /* XXX - detach 'cancellable' rename button to 'Done' to exit application */
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

  override def onCancel() {
    /* Note: we are in the JavaFX thread */
    ui.activity("Cancelling ...")
    stepPane.next.disable.value = true
    cancellableFuture.cancel()
  }

}
