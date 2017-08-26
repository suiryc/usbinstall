package usbinstall.os

import java.nio.file.{Path, Paths}
import javafx.beans.property.{ObjectProperty, SimpleObjectProperty}
import scala.util.matching.Regex
import suiryc.scala.javafx.beans.property.PersistentProperty
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.settings.{
  BaseSettings,
  PersistentSetting,
  SettingSnapshot,
  SettingsSnapshot
}
import suiryc.scala.sys.linux.DevicePartition
import usbinstall.Panes


object OSKind extends Enumeration {

  val Windows = Value
  val Syslinux = Value
  val Generic = Value
  val SystemRescueCD = Value
  val Ubuntu = Value
  val Fedora = Value
  val CentOS = Value
  val ArchLinux = Value

  def efiIcon(v: Value): String = v match {
    case Windows => "os_win.png"
    case Syslinux => "os_linux.png"
    case Generic => "os_linux.png"
    case SystemRescueCD => "os_linux.png"
    case Ubuntu => "os_ubuntu.png"
    case Fedora => "os_fedora.png"
    case CentOS => "os_centos.png"
    case ArchLinux => "os_arch.png"
    case _ => "os_unknown.png"
  }

}

object OSInstallStatus extends Enumeration {
  val NotInstalled = Value
  val Installed = Value
  val Install = Value
}

class SyslinuxEntry(
  val version: Int,
  val label: String
)

object SyslinuxComponentKind extends Enumeration {
  val Image = Value
  val Grub4DOS = Value
}

class SyslinuxComponent(
  val kind: SyslinuxComponentKind.Value,
  val label: String,
  val image: Option[Path]
) {

  def syslinuxLabel: String = label.replaceAll("[^a-zA-Z0-9_]", "_")

}

object PartitionFormat extends Enumeration {

  trait extX
  trait MS

  val ext2 = new Val with extX
  val fat32 = new Val with MS
  val ntfs = new Val with MS
}

class OSSettings(
  val settings: BaseSettings,
  val kind: OSKind.Value,
  val label: String,
  val size: Long,
  val isoPattern: Option[Regex],
  val partitionLabel: String,
  val partitionFormat: PartitionFormat.Value,
  val syslinuxLabel: Option[String],
  val syslinuxVersion: Option[String],
  val efiLoader: Option[String]
) {

  import PersistentSetting._

  val format: PersistentProperty[Boolean] =
    PersistentProperty(PersistentSetting.from(settings, "settings.format", default = true))

  val installStatus: PersistentProperty[OSInstallStatus.Value] =
    PersistentProperty(PersistentSetting.from(settings, "settings.status", OSInstallStatus, OSInstallStatus.Install))

  protected val partitionSetting: PersistentProperty[String] =
    PersistentProperty(PersistentSetting.from(settings, "settings.partition", null))

  val persistent: PersistentProperty[Boolean] =
    PersistentProperty(PersistentSetting.from(settings, "settings.persistence", default = false))

  val partition: ObjectProperty[Option[DevicePartition]] =
    new SimpleObjectProperty(getDevicePartition(partitionSetting.setting.option))

  protected def getDevicePartition(v: Option[String]): Option[DevicePartition] =
    v.flatMap { dev =>
      val path = Paths.get(dev)
      if (path.toFile.exists) {
        DevicePartition.option(path).flatMap { initial =>
          Panes.devices.get(initial.device.dev.toString).flatMap { device =>
            device.partitions.find(_.partNumber == initial.partNumber)
          }
        }
      }
      else None
    }

  partition.listen { newValue =>
    partitionSetting() = newValue.map(_.dev.toString).orNull
  }

  val iso: ObjectProperty[Option[Path]] =
    new SimpleObjectProperty(None)

  var efiBootloader: Option[Path] =
    None

  def enabled: Boolean = installStatus() != OSInstallStatus.NotInstalled

  def install: Boolean = installStatus() == OSInstallStatus.Install

  def installable: Boolean = enabled && partition.get.isDefined &&
    (isoPattern.isEmpty || iso.get.isDefined)

  def formatable: Boolean = install && format() && installable

  def erasable: Boolean = install && !format() && installable

  def syslinuxFile: String = partitionFormat match {
    case _: PartitionFormat.extX => "extlinux.conf"
    case _: PartitionFormat.MS => "syslinux.cfg"
  }

  def reset() {
    format.reset()
    installStatus.reset()
    partition.setValue(getDevicePartition(Option(partitionSetting.setting())))
    persistent.reset()
  }

  def snapshot(snapshot: SettingsSnapshot) {
    snapshot.add(
      SettingSnapshot(format),
      SettingSnapshot(installStatus),
      SettingSnapshot(partition)
    )
  }

  override def toString =
    s"OSSettings(kind=$kind, label=$label, format=${format()}, installStatus=${installStatus()}, partition=${partition.get.map(_.dev)})"

}
