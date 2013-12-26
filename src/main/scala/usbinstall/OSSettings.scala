package usbinstall

import java.io.File
import scala.util.matching.Regex
import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}


object OSInstallStatus extends Enumeration {
  val NotInstalled = Value
  val Installed = Value
  val Install = Value
}

class SyslinuxEntry(
  val version: Int,
  val label: String
)

object PartitionFormat extends Enumeration {
  val ext2 = Value
  val fat32 = Value
  val ntfs = Value
}

class OSSettings(
  val kind: String,
  val label: String,
  val size: Long,
  val isoPattern: Option[Regex],
  val partitionLabel: String,
  val partitionFormat: PartitionFormat.Value,
  val syslinuxLabel: Option[String],
  val syslinuxVersion: Option[Int],
  val xInstallStatus: OSInstallStatus.Value
) {
  import XProperty._

  val format: XProperty[Boolean] =
    XProperty(true)

  val installStatus: XProperty[OSInstallStatus.Value] =
    XProperty(xInstallStatus)

  val partition: XProperty[Option[PartitionInfo]] =
    XProperty(None)

  val iso: XProperty[Option[File]] =
    XProperty(None)

  override def toString =
    s"OSSettings(kind=$kind, label=$label, format=$format, installStatus=$installStatus, partition=${partition().map(_.dev)})"

}
