package usbinstall

import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.{
  Label,
  ListView,
  SelectionMode
}
import scalafxml.core.macros.sfxml
import usbinstall.settings.{InstallSettings, Settings}
import suiryc.scala.misc.Units


@sfxml
class ChooseDeviceController(
  private val devices: ListView[String],
  private val vendor: Label,
  private val model: Label,
  private val size: Label
) {

  /* Note: ScalaFXML macro does not work if class has parentheses-less methods,
   * unless private (excluded from macro processing). Error otherwise:
   *   exception during macro expansion: Multiple parameter lists are not supported
   */
  private def resetDeviceInfo() = {
    vendor.text = ""
    model.text = ""
    size.text = ""
  }

  devices.items = ObservableBuffer(Panes.devices.keys.toList.map(_.toString).sorted)
  devices.selectionModel().selectionMode = SelectionMode.SINGLE
  /* Note: we need to reset the setting, because assigning the same value
   * is not seen as a value change. */
  InstallSettings.device() = None

  devices.selectionModel().selectedItem.onChange { (_, _, newValue) =>
    Panes.devices.get(newValue) match {
      case oDevice @ Some(device) =>
        InstallSettings.device() = oDevice
        Settings.core.oses foreach { os =>
          if (os.partition().exists(_.device != device))
            os.partition() = None
        }
        vendor.text = device.vendor
        model.text = device.model
        device.size.either match {
          case Right(v) =>
            size.text = Units.storage.toHumanReadable(v)

          case Left(e) =>
            size.text = "<unknown>"
            Stages.errorStage("Cannot get device info", Some(s"Device: ${device.dev}"), e)
        }

      case _ =>
        devices.selectionModel().select(-1)
        resetDeviceInfo()
    }
  }

}
