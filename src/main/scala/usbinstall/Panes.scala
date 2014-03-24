package usbinstall

import grizzled.slf4j.Logging
import javafx.{scene => jfxs}
import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, ReadOnlyBooleanProperty}
import scalafx.scene.layout.{AnchorPane, Pane}
import scalafx.event.subscriptions.Subscription
import scalafxml.core.{
  ExplicitDependencies,
  NoDependencyResolver,
  FXMLLoader,
  FXMLView
}
import suiryc.scala.io.{PathFinder, AllPassFileFilter}
import suiryc.scala.sys.linux.Device
import usbinstall.settings.{InstallSettings, Settings}


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

  class CancelButton(pane: StepPane, f: => Boolean)
    extends StepButton(pane, f, "Cancel")

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


  def install =
    new AnchorPane with StepPane {
      val root = FXMLView(getClass.getResource("install.fxml"),
        new ExplicitDependencies(Map(
          "previous" -> previous,
          "cancel" -> next
        )))

      content = root
      AnchorPane.setAnchors(root, 0, 0, 0, 0)

      override val previous = new PreviousButton(this, {
        Stages.choosePartitions()
        true
      }) {
        disable.value = true
      }

      override val next = new CancelButton(this, {
        true
      })
    }

}
