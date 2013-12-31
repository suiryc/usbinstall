package usbinstall.settings

import java.nio.file.Files
import usbinstall.device.DeviceInfo
import usbinstall.util.XProperty


object InstallSettings {

  val device: XProperty[Option[DeviceInfo]] =
    new XProperty(None)

  lazy val pathMountISO = {
    val file = Files.createTempDirectory("usbinstall.iso-").toFile()
    file.deleteOnExit()
    file
  }

  lazy val pathMountPartition = {
    val file = Files.createTempDirectory("usbinstall.part-").toFile()
    file.deleteOnExit()
    file
  }

}
