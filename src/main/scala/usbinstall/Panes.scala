package usbinstall

import grizzled.slf4j.Logging
import javafx.{scene => jfxs}
import scalafx.Includes._
import scalafx.scene.layout.AnchorPane
import scalafxml.core.{
  ExplicitDependencies,
  NoDependencyResolver,
  FXMLLoader,
  FXMLView
}
import suiryc.scala.io.{PathFinder, AllPassFileFilter}
import suiryc.scala.sys.linux.{Device, NetworkBlockDevice}
import usbinstall.settings.{InstallSettings, Settings}


object Panes
  extends Logging
{

  val devices = (PathFinder("/") / "sys" / "block" * AllPassFileFilter).get().map { block =>
    Device(block)
  }.filter { device =>
    device.ueventProps.get("DRIVER") match {
      case Some("sd") =>
        true

      case _ =>
        if (device.isInstanceOf[NetworkBlockDevice])
          device.size.either.fold(_ => false, v => (v > 0) && !device.readOnly)
        else
          false
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

        subscriptions ::= InstallSettings.device.onChange { (_, _, device) =>
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
          subscriptions ::= settings.installStatus.onChange(updateDisable)
          subscriptions ::= settings.partition.onChange(updateDisable)
          subscriptions ::= settings.iso.onChange(updateDisable)
        }
      }
    }


  def install =
    new AnchorPane with StepPane {
      /* Note: for some reason we need to tell 'loader' type. Otherwise
       * compilation fails with 'recursive value loader needs type' message.
       */
      val loader: FXMLLoader = new FXMLLoader(getClass.getResource("install.fxml"),
        new ExplicitDependencies(Map("stepPane" -> this)))
      loader.load()

      val root = loader.getRoot[jfxs.Parent]
      val controller = loader.getController[HasCancel]

      content = root
      AnchorPane.setAnchors(root, 0, 0, 0, 0)

      override val previous = new PreviousButton(this, {
        Stages.choosePartitions()
        true
      }) {
        disable.value = true
      }

      override val next = new CancelButton(this, {
        controller.onCancel()
        false
      })
    }

}
