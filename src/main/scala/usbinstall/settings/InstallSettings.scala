package usbinstall.settings

import java.nio.file.{Files, Path}
import scalafx.beans.property.ObjectProperty
import suiryc.scala.sys.linux.Device


object InstallSettings {

  val device: ObjectProperty[Option[Device]] =
    ObjectProperty(None)

  protected def tempDirectory(prefix: String): Path = {
    val path = Files.createTempDirectory(prefix)
    path.toFile.deleteOnExit()
    path
  }

  lazy val pathTemp = tempDirectory("usbinstall.tmp-")

  lazy val pathMountISO = tempDirectory("usbinstall.iso-")

  lazy val pathMountPartition = tempDirectory("usbinstall.part-")

}
