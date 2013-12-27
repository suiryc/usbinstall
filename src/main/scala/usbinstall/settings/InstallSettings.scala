package usbinstall.settings

import usbinstall.device.DeviceInfo
import usbinstall.util.XProperty


object InstallSettings {

  val device: XProperty[Option[DeviceInfo]] =
    new XProperty(None)

}
