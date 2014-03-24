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


@sfxml
class InstallController(
  private val vbox: VBox,
  private val grid: GridPane,
  private val action: Label,
  private val step: Label,
  private val previous: Panes.PreviousButton,
  private val cancel: Panes.CancelButton
) extends Logging
{

  println(Settings.core.oses)

  val activityArea = new LogArea {
    margin = Insets(0, 10, 10, 10)
    vgrow = Priority.ALWAYS
  }
  vbox.content.add(activityArea)

  val cancellableFuture = CancellableFuture(installTask(_))
  cancellableFuture.future.onComplete {
    case Failure(e) =>
      error(s"Task failed", e)

    case Success(_) =>
      info(s"Task succeeded")
  }

  /* XXX - link cancel button (or property) to cancellable */

  def installTask(cancellable: Cancellable) {

    checkCancelled(cancellable)

    /* XXX - access lazy vals (mount points) */
    /* XXX - loop on oses to prepare/... */
    /* XXX - catch issues */
    Settings.core.oses foreach { settings =>
      try {
        if (settings.kind == OSKind.GPartedLive) {
          val os = OSInstall(settings)

          checkCancelled(cancellable)
          JFXSystem.schedule(activityArea.appendLine("Sleeping before acting ..."))
          Thread.sleep(5000)
          checkCancelled(cancellable)
          JFXSystem.schedule(activityArea.appendLine("Acting ..."))
          OSInstall.prepare(os)
          checkCancelled(cancellable)
          JFXSystem.schedule(activityArea.appendLine("Sleeping after acting ..."))
          /*OSInstall.install(os)
          OSInstall.postInstall(os)*/
          Thread.sleep(5000)
          checkCancelled(cancellable)
          JFXSystem.schedule(activityArea.appendLine(s"Done ${settings.kind}"))
        }
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

  def checkCancelled(cancellable: Cancellable) =
    cancellable.check {
      JFXSystem.schedule(activityArea.appendLine("Cancelled"))
    }

}
