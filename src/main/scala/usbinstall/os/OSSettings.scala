package usbinstall.os

import java.nio.file.{Path, Paths}
import javafx.beans.property.{ObjectProperty, SimpleObjectProperty}
import scala.util.matching.Regex
import suiryc.scala.javafx.beans.property.ConfigEntryProperty
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.settings.{ConfigEntry, PortableSettings, SettingSnapshot, SettingsSnapshot}
import suiryc.scala.sys.linux.DevicePartition
import usbinstall.Panes
import usbinstall.settings.EFISettings


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

object OSPartitionAction extends Enumeration {
  val None = Value
  val Format = Value
  val Copy = Value
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

object PartitionFilesystem extends Enumeration {

  trait extX
  trait MS

  val ext2 = new Val with extX
  val fat16 = new Val with MS
  val fat32 = new Val with MS
  val ntfs = new Val with MS
}

class OSSettings(
  settings: PortableSettings,
  prefix: Seq[String],
  val kind: OSKind.Value,
  val label: String,
  val size: Long,
  val isoPattern: Option[Regex],
  val partitionLabel: String,
  val partitionFilesystem: PartitionFilesystem.Value,
  val syslinuxRoot: Option[String],
  val syslinuxLabel: Option[String],
  val syslinuxVersion: Option[String],
  val efiSettings: EFISettings
) {

  import usbinstall.settings.Settings._

  // Note: default values (when applicable) have been set in the
  // underlying configuration.
  val select: ConfigEntryProperty[Boolean] =
    ConfigEntryProperty(ConfigEntry.from[Boolean](settings, prefix ++ Seq(KEY_SETTINGS, "select")))

  val partitionAction: ConfigEntryProperty[OSPartitionAction.Value] =
    ConfigEntryProperty(ConfigEntry.from(settings, OSPartitionAction, prefix ++ Seq(KEY_SETTINGS, "partitionAction")))

  val setup: ConfigEntryProperty[Boolean] =
    ConfigEntryProperty(ConfigEntry.from[Boolean](settings, prefix ++ Seq(KEY_SETTINGS, "setup")))

  val bootloader: ConfigEntryProperty[Boolean] =
    ConfigEntryProperty(ConfigEntry.from[Boolean](settings, prefix ++ Seq(KEY_SETTINGS, "bootloader")))

  protected val partitionSetting: ConfigEntry[String] =
    ConfigEntry.from(settings, prefix ++ Seq(KEY_SETTINGS, "partition"))

  val persistent: ConfigEntry[Boolean] =
    ConfigEntry.from[Boolean](settings, prefix ++ Seq(KEY_SETTINGS, "persistence"))

  val partition: ObjectProperty[Option[DevicePartition]] =
    new SimpleObjectProperty(getDevicePartition(partitionSetting.opt))

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
    partitionSetting.set(newValue.map(_.dev.toString).orNull)
  }

  val iso: ObjectProperty[Option[Path]] =
    new SimpleObjectProperty(None)

  var efiBootloader: Option[Path] =
    None

  def isSelected: Boolean = select.get

  def isPartitionInstall: Boolean = isSelected && (partitionAction.get != OSPartitionAction.None)

  def isPartitionFormat: Boolean = isSelected && (partitionAction.get == OSPartitionAction.Format)

  def isPartitionErase: Boolean = isSelected && (partitionAction.get == OSPartitionAction.Copy)

  def isSetup: Boolean = isSelected && setup.get

  def isBootloader: Boolean = isSelected && bootloader.get

  def syslinuxFile: String = partitionFilesystem match {
    case _: PartitionFilesystem.extX => "extlinux.conf"
    case _: PartitionFilesystem.MS => "syslinux.cfg"
  }

  def snapshot(snapshot: SettingsSnapshot) {
    snapshot.add(
      SettingSnapshot(select),
      SettingSnapshot(partitionAction),
      SettingSnapshot(setup),
      SettingSnapshot(bootloader),
      SettingSnapshot(partition)
    )
  }

  override def toString =
    s"OSSettings(kind=$kind, label=$label, select=${
      select.get
    }, partitionAction=${
      partitionAction.get
    }, setup=${
      setup.get
    }, bootloader=${
      bootloader.get
    }, partition=${
      partition.get.map(_.dev)
    })"

}
