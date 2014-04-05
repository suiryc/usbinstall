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
import usbinstall.settings.Settings
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

  println(Settings.core.oses)

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
  }

  /* XXX - link cancel button (or property) to cancellable */

  private def installTask(cancellable: Cancellable) {

    checkCancelled(cancellable)

    /* XXX - access lazy vals (mount points) */
    /* XXX - loop on oses to prepare/... */
    /* XXX - catch issues */
    ui.none()
    Settings.core.oses foreach { settings =>
      try {
        checkCancelled(cancellable)
        if (settings.kind == OSKind.GPartedLive) {
          val os = OSInstall(settings, ui)

          OSInstall.prepare(os)
          ui.none()
          checkCancelled(cancellable)
          OSInstall.install(os)
          ui.none()
          OSInstall.postInstall(os)
          ui.none()
        }
      }
      catch {
        case e @ Cancelled =>
          throw e

        case e: Throwable =>
          error(s"Failed to install ${settings.label}: ${e.getMessage}", e)
          throw e
      }
      ui.none()
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
