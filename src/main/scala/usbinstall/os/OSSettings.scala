package usbinstall.os

import java.io.File
import java.nio.file.{Path, Paths}
import scala.util.matching.Regex
import scalafx.beans.property.ObjectProperty
import suiryc.scala.javafx.beans.property.PersistentProperty
import suiryc.scala.misc.EnumerationEx
import suiryc.scala.settings.{BaseSettings, PersistentSetting}
import suiryc.scala.sys.linux.{Device, DevicePartition}
import usbinstall.Panes


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
  val syslinuxVersion: Option[Int]
)(implicit settings: BaseSettings)
{

  implicit val osInstallStatus: OSInstallStatus.type = OSInstallStatus

  val format: PersistentProperty[Boolean] =
    PersistentProperty(PersistentSetting.forBoolean("settings.format", true))

  val installStatus: PersistentProperty[OSInstallStatus.Value] =
    PersistentProperty(PersistentSetting.forEnumerationEx("settings.status", OSInstallStatus.Install))

  protected val partitionSetting: PersistentProperty[String] =
    PersistentProperty(PersistentSetting.forString("settings.partition", null))

  val partition: ObjectProperty[Option[DevicePartition]] = {
    val partition = partitionSetting.setting.option flatMap { dev =>
      val initial = DevicePartition(Paths.get(dev))
      Panes.devices.get(initial.device.dev.toString) flatMap { device =>
        device.partitions.find(_.partNumber == initial.partNumber)
      }
    }

    ObjectProperty(partition)
  }

  partition.onChange { (_, _, newValue) =>
    partitionSetting() = newValue.map(_.dev.toString).orNull
  }

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

  def reset() {
    format.reset()
    installStatus.reset()
  }

  override def toString =
    s"OSSettings(kind=$kind, label=$label, format=${format()}, installStatus=${installStatus()}, partition=${partition().map(_.dev)})"

}
