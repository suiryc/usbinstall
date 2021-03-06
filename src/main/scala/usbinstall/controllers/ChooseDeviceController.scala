package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{Label, ListView}
import scala.annotation.unused
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.misc.Units
import usbinstall.settings.InstallSettings
import usbinstall.{Panes, USBInstall}


class ChooseDeviceController extends Initializable {

  @FXML
  protected var devices: ListView[String] = _

  @FXML
  protected var vendor: Label = _

  @FXML
  protected var model: Label = _

  @FXML
  protected var size: Label = _

  def resetDeviceInfo(): Unit = {
    vendor.setText("")
    model.setText("")
    size.setText("")
  }

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {
    val profile = InstallSettings.profile.get.get

    refreshDevices()

    devices.getSelectionModel.selectedItemProperty.listen { newValue =>
      Panes.devices.get(newValue) match {
        case oDevice @ Some(device) =>
          profile.device.set(device)
          InstallSettings.device.set(oDevice)
          vendor.setText(device.vendorOption.getOrElse(device.name))
          model.setText(device.modelOption.getOrElse(device.name))
          device.size.either match {
            case Right(v) =>
              size.setText(Units.storage.toHumanReadable(v))

            case Left(ex) =>
              size.setText("<unknown>")
              Dialogs.error(
                owner = Some(USBInstall.stage),
                title = Some("Cannot get device info"),
                contentText = Some(s"Device: ${device.dev}"),
                ex = Some(ex)
              )
          }
          ()

        case _ =>
          devices.getSelectionModel.select(-1)
          resetDeviceInfo()
      }
    }

    profile.device.uuid.opt.flatMap { uuid =>
      Panes.devices.values.find(_.uuid.contains(uuid)).map(_.dev.toString)
    }.orElse {
      profile.device.dev.opt.filter(Panes.devices.contains)
    }.foreach { deviceName =>
      devices.getSelectionModel.select(deviceName)
    }

    ()
  }

  def onRefresh(@unused vent: ActionEvent): Unit = {
    Panes.refreshDevices()
    refreshDevices()
  }

  private def refreshDevices(): Unit = {
    devices.getItems.setAll(Panes.devices.keys.toList.map(_.toString).sorted:_*)
    // Note: we need to reset the setting, because assigning the same value
    // is not seen as a value change.
    InstallSettings.device.set(None)
  }

}
