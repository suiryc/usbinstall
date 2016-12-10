package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{Label, ListView}
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

  def resetDeviceInfo() {
    vendor.setText("")
    model.setText("")
    size.setText("")
  }

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    refreshDevices()

    devices.getSelectionModel.selectedItemProperty.listen { newValue =>
      Panes.devices.get(newValue) match {
        case oDevice @ Some(device) =>
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
                headerText = Some(s"Device: ${device.dev}"),
                ex = Some(ex)
              )
          }
          ()

        case _ =>
          devices.getSelectionModel.select(-1)
          resetDeviceInfo()
      }
    }
    ()
  }

  def onRefresh(event: ActionEvent) {
    Panes.refreshDevices()
    refreshDevices()
  }

  private def refreshDevices() {
    devices.getItems.setAll(Panes.devices.keys.toList.map(_.toString).sorted:_*)
    // Note: we need to reset the setting, because assigning the same value
    // is not seen as a value change.
    InstallSettings.device.set(None)
  }

}
