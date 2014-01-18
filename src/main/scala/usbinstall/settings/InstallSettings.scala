package usbinstall.settings

import java.nio.file.Files
import suiryc.scala.javafx.beans.property.PropertyEx
import suiryc.scala.sys.linux.Device


object InstallSettings {

  val device: PropertyEx[Option[Device]] =
    new PropertyEx(None)

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
