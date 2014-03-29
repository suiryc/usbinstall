package usbinstall.os

import java.io.File
import java.nio.file.Path
import scala.util.matching.Regex
import scalafx.beans.property.ObjectProperty
import suiryc.scala.misc.EnumerationEx
import suiryc.scala.sys.linux.DevicePartition


object OSKind extends EnumerationEx {
  val Win7_8 = Value("Win7&8")
  val syslinux = Value
  val GPartedLive = Value
  val SystemRescueCD = Value
  val Ubuntu = Value
  val Redhat = Value
  val ArchLinux = Value
  val Kali = Value
}

object OSInstallStatus extends EnumerationEx {
  val NotInstalled = Value
  val Installed = Value
  val Install = Value
}

class SyslinuxEntry(
  val version: Int,
  val label: String
)

object PartitionFormat extends EnumerationEx {

  trait extX
  trait MS

  val ext2 = new Val with extX
  val fat32 = new Val with MS
  val ntfs = new Val with MS
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

  val format: ObjectProperty[Boolean] =
    ObjectProperty(true)

  val installStatus: ObjectProperty[OSInstallStatus.Value] =
    ObjectProperty(xInstallStatus)

  val partition: ObjectProperty[Option[DevicePartition]] =
    ObjectProperty(None)

  val iso: ObjectProperty[Option[File]] =
    ObjectProperty(None)

  var efiBootloader: Option[Path] =
    None

  def enabled = installStatus() != OSInstallStatus.NotInstalled

  def install = installStatus() == OSInstallStatus.Install

  def installable = enabled && partition().isDefined &&
    (!isoPattern.isDefined || iso().isDefined)

  def formatable = install &&
    format() && installable

  def syslinuxFile = partitionFormat match {
    case _: PartitionFormat.extX => "extlinux.conf"
    case _: PartitionFormat.MS => "syslinux.cfg"
  }

  override def toString =
    s"OSSettings(kind=$kind, label=$label, format=$format, installStatus=$installStatus, partition=${partition().map(_.dev)})"

}
