package usbinstall

import grizzled.slf4j.Logging
import javafx.{scene => jfxs}
import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, ReadOnlyBooleanProperty}
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.control.{
  Button,
  CheckBox,
  ComboBox,
  Label,
  ListView,
  SelectionMode
}
import scalafx.scene.layout.{
  AnchorPane,
  ColumnConstraints,
  GridPane,
  HBox,
  Pane,
  RowConstraints,
  StackPane,
  VBox
}
import scalafx.event.subscriptions.Subscription
import scalafxml.core.{NoDependencyResolver, FXMLLoader, FXMLView}
import suiryc.scala.io.{PathFinder, AllPassFileFilter}
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.misc.{RichOptional, Units}
import suiryc.scala.sys.CommandResult
import suiryc.scala.sys.linux.{Device, DevicePartition}
import usbinstall.os.{OSInstall, OSInstallStatus, OSKind, OSSettings}
import usbinstall.settings.{InstallSettings, Settings}
import usbinstall.util.Util
import scalafx.event.ActionEvent


trait HasEventSubscriptions {
  def getSubscriptions(): List[Subscription]
}

object Panes
  extends Logging
{

  abstract class AbstractStepButton(val visible: Boolean, xdisabled: Boolean, val label: String) {
    def triggered: Unit

    val disable = new BooleanProperty()
    def disable_=(v: Boolean) = disable.value = v
    val disabled: ReadOnlyBooleanProperty = disable

    disable.value = xdisabled
  }

  object NoButton extends AbstractStepButton(false, false, "") {
    override def triggered = {}
  }

  class StepButton(pane: StepPane, f: => Boolean, override val label: String)
    extends AbstractStepButton(true, false, label)
  {
    override def triggered = {
      if (f) pane.cancelSubscriptions
    }
  }

  class PreviousButton(pane: StepPane, f: => Boolean)
    extends StepButton(pane, f, "Previous")

  class NextButton(pane: StepPane, f: => Boolean)
    extends StepButton(pane, f, "Next")

  trait StepPane extends Pane {
    /* Note: subscriptions on external object need to be cancelled for
     * pane/scene to be GCed. */
    var subscriptions: List[Subscription] = Nil
    def cancelSubscriptions {
      subscriptions foreach { _.cancel }
      subscriptions = Nil
    }
    val previous: AbstractStepButton
    val next: AbstractStepButton
  }

  val devices = (PathFinder("/") / "sys" / "block" * AllPassFileFilter).get().map { block =>
    Device(block)
  }.filter { device =>
    device.ueventProps.get("DRIVER") match {
      case Some("sd") => true
      case _ => false
    }
  }.toList.foldLeft(Map.empty[String, Device]) { (devices, device) =>
    devices + (device.dev.toString() -> device)
  }

  def chooseDevice =
    new AnchorPane with StepPane {
      padding = Insets(5)

      val root = FXMLView(getClass.getResource("chooseDevice.fxml"),
        NoDependencyResolver)

      content = root
      AnchorPane.setAnchors(root, 0, 0, 0, 0)

      override val previous = NoButton

      override val next = new NextButton(this, {
        InstallSettings.device() map { device =>
          Stages.choosePartitions()
          true
        } getOrElse(false)
      }) {
        disable.value = true

        subscriptions ::= InstallSettings.device.property.onChange { (_, _, device) =>
          Option(device) match {
            case Some(_) =>
              disable.value = false

            case _ =>
              disable.value = true
          }
        }
      }
    }

  def choosePartitions =
    new AnchorPane with StepPane {
      val loader = new FXMLLoader(getClass.getResource("choosePartitions.fxml"),
        NoDependencyResolver)

      loader.load()

      val root = loader.getRoot[jfxs.Parent]
      val controller = loader.getController[HasEventSubscriptions]

      content = root
      AnchorPane.setAnchors(root, 0, 0, 0, 0)

      subscriptions :::= controller.getSubscriptions()

      override val previous = new PreviousButton(this, {
        Stages.chooseDevice()
        true
      })

      override val next = new NextButton(this, {
        Stages.install()
        true
      }) {
        disable.value = true

        private def updateDisable {
          disable.value = Settings.core.oses.exists { settings =>
            settings.enabled && !settings.installable
          }
        }
        updateDisable

        Settings.core.oses foreach { settings =>
          subscriptions ::= settings.installStatus.property.onChange(updateDisable)
          subscriptions ::= settings.partition.property.onChange(updateDisable)
          subscriptions ::= settings.iso.property.onChange(updateDisable)
        }
      }
    }


  def install = {
    println(Settings.core.oses)

    val stepsArea = new LogArea

    val activityArea = new LogArea

    /* Note: for correct behaviour, actions on UI elements must be done inside
     * the JavaFX UI thread.
     */
    import suiryc.scala.javafx.concurrent.JFXExecutor.executor
    scala.concurrent.Future[Unit] {
      println("Test")
      (1 to 40) foreach { i =>
        activityArea.appendLine(s"Test $i")
        stepsArea.prependLine(s"Test $i")
      }
    }

    /* XXX - access lazy vals (mount points) */
    /* XXX - loop on oses to prepare/... */
    /* XXX - catch issues */
    /* XXX - how to proxy steps messages from os install to GUI ? */
    /* XXX - action needs to be done in separate Thread, otherwise GUI is blocked
     * until finished */
    Settings.core.oses foreach { settings =>
      try {
        if (settings.kind == OSKind.GPartedLive) {
          val os = OSInstall(settings)

          OSInstall.prepare(os)
          OSInstall.install(os)
          OSInstall.postInstall(os)
        }
      }
      catch {
        case e: Throwable =>
          error(s"Failed to install ${settings.label}: ${e.getMessage}", e)
      }
    }

    new HBox with StepPane {
      padding = Insets(5)
      spacing = 5
      alignment = Pos.TOP_CENTER
      maxHeight = Double.MaxValue
      content = List(stepsArea, activityArea)

      override val previous = NoButton

      override val next = NoButton

    }
  }

}
