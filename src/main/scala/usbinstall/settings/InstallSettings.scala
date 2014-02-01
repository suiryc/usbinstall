package usbinstall.settings

import java.io.File
import java.nio.file.Files
import suiryc.scala.javafx.beans.property.PropertyEx
import suiryc.scala.sys.linux.Device


object InstallSettings {

  val device: PropertyEx[Option[Device]] =
    new PropertyEx(None)

  protected def tempDirectory(prefix: String): File = {
    val file = Files.createTempDirectory(prefix).toFile()
    file.deleteOnExit()
    file
  }

  lazy val pathTemp = tempDirectory("usbinstall.tmp-")

  lazy val pathMountISO = tempDirectory("usbinstall.iso-")

  lazy val pathMountPartition = tempDirectory("usbinstall.part-")

}
