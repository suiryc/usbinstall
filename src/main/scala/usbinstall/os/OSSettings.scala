package usbinstall.os

import java.nio.file.Path
import javafx.beans.property.{ObjectProperty, SimpleObjectProperty}
import scala.util.matching.Regex
import suiryc.scala.javafx.beans.property.ConfigEntryProperty
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.settings.{BaseConfig, ConfigEntry, PortableSettings}
import suiryc.scala.sys.linux.{Device, DevicePartition}
import usbinstall.settings.{EFISettings, InstallSettings, ProfileSettings}


object OSKind extends Enumeration {

  val Windows, Syslinux, Generic, SystemRescueCD, Ubuntu, Fedora, CentOS, ArchLinux = Value

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
  val None, Format, Copy = Value
}

class SyslinuxEntry(
  val version: Int,
  val label: String
)

object SyslinuxComponentKind extends Enumeration {
  val Image, Grub4DOS = Value
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
  profileSettings: ProfileSettings,
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

  val partition = new PartitionSettings(settings, prefix ++ Seq(KEY_SETTINGS, "partition"), profileSettings)

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
      partition.optPart.map(_.dev)
    })"

}

class PartitionSettings(
  settings: PortableSettings,
  prefix: Seq[String],
  profileSettings: ProfileSettings
) {

  import BaseConfig._

  // Legacy settings only used the partition path.
  // Now we remember the partition number instead, and build the path
  // relatively to the profile selected device.
  private lazy val legacyPath_dev = BaseConfig.joinPath(prefix)
  private lazy val legacy_dev: Option[String] = try {
    settings.config.option[String](legacyPath_dev)
  } catch {
    case _: Exception => None
  }

  protected val number: ConfigEntry[Int] =
    ConfigEntry.from(settings, prefix ++ Seq("number"))

  protected var perDevice: Map[Device, ConfigEntry[Int]] = Map.empty

  // Notes:
  // We keep an ObjectProperty because external classes do need to listen to
  // values changes.
  // However, we expose functions to get/set the DevicePartition to prevent
  // other direct accesses (especially setting value) so that we can manage
  // other actual settings more easily.
  val part: ObjectProperty[Option[DevicePartition]] = new SimpleObjectProperty(None)

  // Migrate legacy setting.
  if (number.opt.isEmpty) {
    legacy_dev.foreach { path =>
      // Properly remove legacy path, even if not strictly necessary because
      // the new paths will do it as a side effect.
      settings.withoutPath(legacyPath_dev)
      // We could try to determine the actual DevicePartition from the
      // supposedly selected Device. But the easiest solution is to
      // parse the ending number.
      val idx = path.reverse.takeWhile(_.isDigit).reverse.toInt
      number.set(idx)
      // We also need to set the partition number for this device.
      deviceOpt.foreach { device =>
        set(DevicePartition.option(device, idx))
      }
    }
  }

  protected def deviceOpt: Option[Device] =
    profileSettings.device.dev.opt.map(Device.apply)

  protected def refresh(): Unit = {
    for {
      device <- deviceOpt
      idx <- getNumber(device).opt
    } {
      part.set(DevicePartition.option(device, idx))
    }
  }

  // Initialize PartitionDevice if possible.
  refresh()

  // When selected device is changed, refresh our DevicePartition.
  InstallSettings.device.listen { v =>
    // Unsetting device is ignored.
    if (v.nonEmpty) {
      // Change only matters for our profile.
      if (InstallSettings.profile.get.exists(_.profileName == profileSettings.profileName)) {
        refresh()
      }
    }
  }

  protected def getNumber(device: Device): ConfigEntry[Int] = {
    perDevice.getOrElse(device, {
      device.uuid.toOption.map { uuid =>
        val v = ConfigEntry.from[Int](settings, prefix ++ Seq(uuid))
        perDevice += (device -> v)
        // If the partition for this device is not yet known, initialize it
        // to the latest chosen partition.
        if (v.opt.isEmpty) number.opt.foreach(v.set)
        v
      }.getOrElse(number)
    })
  }

  protected def getNumber(device: Option[Device]): ConfigEntry[Int] = {
    device.map(getNumber).getOrElse(number)
  }

  def optPart: Option[DevicePartition] = part.get

  def set(opt: Option[DevicePartition]): Unit = {
    // Save the per-device and general partition number.
    val values = List(number, getNumber(deviceOpt))
    opt.fold(values.foreach(_.reset())) { partition =>
      values.foreach(_.set(partition.partNumber))
    }
    part.set(opt)
  }

}
