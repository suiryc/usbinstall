package usbinstall

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.layout.AnchorPane
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.io.{AllPassFileFilter, PathFinder}
import suiryc.scala.sys.linux.{Device, LoopbackDevice, NetworkBlockDevice}
import usbinstall.controllers.{ChoosePartitionsController, ChooseProfileController, InstallController}
import usbinstall.settings.InstallSettings


object Panes {

  var devices: Map[String, Device] = Map.empty

  def refreshDevices(): Unit = {
    devices = (PathFinder("/") / "sys" / "block" * AllPassFileFilter).get().map { block =>
      Device(block)
    }.filter { device =>
      device.ueventProps.get("DRIVER") match {
        case Some("sd") =>
          // Usually HDD/SSD, including USB ones, and sometimes memory cards
          true

        case Some("mmcblk") =>
          // Sometimes used for memory card
          true

        case _ =>
          // Also allow non-void writable loopback or network block devices.
          // Useful when testing installation on raw disk file or virtual disk
          // handled by qemu-nbd tool.
          if (device.isInstanceOf[LoopbackDevice] || device.isInstanceOf[NetworkBlockDevice])
            device.size.either.fold(_ => false, v => (v > 0) && !device.readOnly)
          else
            false
      }
    }.toList.foldLeft(Map.empty[String, Device]) { (devices, device) =>
      devices + (device.dev.toString -> device)
    }
  }

  refreshDevices()

  protected def initPane(pane: StepPane, root: Parent, controller: Option[Any] = None): (StepPane, Option[Any]) = {
    pane.getChildren.setAll(root)
    AnchorPane.setTopAnchor(root, 0.0)
    AnchorPane.setRightAnchor(root, 0.0)
    AnchorPane.setBottomAnchor(root, 0.0)
    AnchorPane.setLeftAnchor(root, 0.0)

    (pane, controller)
  }

  def chooseProfile(): (StepPane, Option[Any]) = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/chooseProfile.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[ChooseProfileController]()

    val pane: StepPane = new AnchorPane with StepPane {
      override val previous: NoButton.type = NoButton

      override val next: NextButton = new NextButton(this, {
        InstallSettings.profile.get.exists { _ =>
          Stages.chooseDevice()
          true
        }
      }) {
        disable = InstallSettings.profile.getValue.isEmpty

        subscriptions ::= InstallSettings.profile.listen { v =>
          disable = v.isEmpty
        }
      }
    }

    initPane(pane, root, Some(controller))
  }

  def chooseDevice(): (StepPane, Option[Any]) = {
    val root = FXMLLoader.load[Parent](getClass.getResource("/fxml/chooseDevice.fxml"))
    val pane: StepPane = new AnchorPane with StepPane {
      override val previous = new PreviousButton(this, {
        Stages.chooseProfile()
        true
      })

      override val next: NextButton = new NextButton(this, {
        InstallSettings.device.get.exists { _ =>
          Stages.choosePartitions()
          true
        }
      }) {
        disable = Option(InstallSettings.device.getValue).isEmpty

        subscriptions ::= InstallSettings.device.listen { v =>
          disable = Option(v).isEmpty
        }
      }
    }

    initPane(pane, root)
  }

  def choosePartitions(): (StepPane, Option[Any]) = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/choosePartitions.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[ChoosePartitionsController]()

    val pane: StepPane = new AnchorPane with StepPane {
      subscriptionHolders ::= controller

      override val previous = new PreviousButton(this, {
        Stages.chooseDevice()
        true
      })

      override val next: NextButton = new NextButton(this, {
        Stages.install()
        true
      }) {
        disable = true
      }
    }
    controller.setStepPane(pane)

    initPane(pane, root, Some(controller))
  }


  def install(): (StepPane, Option[Any]) = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/install.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[InstallController]()

    val pane: StepPane = new AnchorPane with StepPane {
      subscriptionHolders ::= controller

      override val previous: PreviousButton = new PreviousButton(this, {
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

    initPane(pane, root, Some(controller))
  }

}
