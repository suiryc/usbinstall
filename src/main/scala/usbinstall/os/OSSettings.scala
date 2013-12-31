package usbinstall.os

import java.io.File
import scala.util.matching.Regex
import usbinstall.util.{XEnumeration, XProperty}
import usbinstall.device.PartitionInfo


object OSKind extends XEnumeration {
  val Win7_8 = Value("Win7&8")
  val syslinux = Value
  val GPartedLive = Value
  val SystemRescueCD = Value
  val Ubuntu = Value
  val Redhat = Value
  val ArchLinux = Value
  val Kali = Value
}

object OSInstallStatus extends XEnumeration {
  val NotInstalled = Value
  val Installed = Value
  val Install = Value
}

class SyslinuxEntry(
  val version: Int,
  val label: String
)

object PartitionFormat extends XEnumeration {
  val ext2 = Value
  val fat32 = Value
  val ntfs = Value
}

class OSSettings(
  val kind: OSKind.Value,
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

  def enabled = installStatus() != OSInstallStatus.NotInstalled

  def installable = enabled && partition().isDefined &&
    (!isoPattern.isDefined || iso().isDefined)

  def formatable = (installStatus() == OSInstallStatus.Install) &&
    format() && installable

  def syslinuxFile = partitionFormat match {
    case PartitionFormat.ext2 => "extlinux.conf"
    case PartitionFormat.ntfs => "syslinux.cfg"
    case PartitionFormat.fat32 => "syslinux.cfg"
  }

  override def toString =
    s"OSSettings(kind=$kind, label=$label, format=$format, installStatus=$installStatus, partition=${partition().map(_.dev)})"

}
