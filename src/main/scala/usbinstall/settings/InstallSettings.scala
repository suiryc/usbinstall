package usbinstall.settings

import java.nio.file.{Files, Path}
import javafx.beans.property.{ObjectProperty, SimpleObjectProperty}
import suiryc.scala.sys.linux.Device


object InstallSettings {

  val device: ObjectProperty[Option[Device]] =
    new SimpleObjectProperty(None)

  protected def tempDirectory(root: Option[Path], prefix: String, deleteOnExit: Boolean): Path = {
    val path = root.fold {
      Files.createTempDirectory(prefix)
    } { root =>
      Files.createTempDirectory(root, prefix)
    }
    if (deleteOnExit) path.toFile.deleteOnExit()
    path
  }

  def tempDirectory(prefix: String): Path =
    tempDirectory(Some(pathTemp), prefix, deleteOnExit = false)

  lazy val pathTemp = tempDirectory(None, "usbinstall.tmp-", deleteOnExit = true)

  lazy val pathMountISO = tempDirectory(None, "usbinstall.iso-", deleteOnExit = true)

  lazy val pathMountPartition = tempDirectory(None, "usbinstall.part-", deleteOnExit = true)

}
