package usbinstall

import grizzled.slf4j.Logging
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.layout.AnchorPane
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
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

  protected def initPane(pane: StepPane, root: Parent) = {
    pane.getChildren().setAll(root)
    AnchorPane.setTopAnchor(root, 0)
    AnchorPane.setRightAnchor(root, 0)
    AnchorPane.setBottomAnchor(root, 0)
    AnchorPane.setLeftAnchor(root, 0)

    pane
  }

  def chooseDevice = {
    val root = FXMLLoader.load[Parent](getClass.getResource("chooseDevice.fxml"))
    val pane = new AnchorPane with StepPane {
      override val previous = NoButton

      override val next = new NextButton(this, {
        InstallSettings.device.get map { device =>
          Stages.choosePartitions()
          true
        } getOrElse(false)
      }) {
        disable = true

        subscriptions ::= InstallSettings.device.listen { device =>
          Option(device) match {
            case Some(_) =>
              disable = false

            case _ =>
              disable = true
          }
        }
      }
    }

    initPane(pane, root)
  }

  def choosePartitions = {
    val loader = new FXMLLoader(getClass.getResource("choosePartitions.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[ChoosePartitionsController]()

    val pane = new AnchorPane with StepPane {
      subscriptionHolders ::= controller

      override val previous = new PreviousButton(this, {
        Stages.chooseDevice()
        true
      })

      override val next = new NextButton(this, {
        Stages.install()
        true
      }) {
        disable = true
      }
    }
    controller.setStepPane(pane)

    initPane(pane, root)
  }


  def install() = {
    val loader = new FXMLLoader(getClass.getResource("install.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[InstallController]()

    val pane = new AnchorPane with StepPane {
      subscriptionHolders ::= controller

      override val previous = new PreviousButton(this, {
        Stages.choosePartitions()
        true
      }) {
        disable = true
      }

      override val next = new CancelButton(this, {
        controller.onCancel()
        false
      })
    }
    controller.setStepPane(pane)

    initPane(pane, root)
  }

}
