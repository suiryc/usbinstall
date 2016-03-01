package usbinstall.settings

import com.typesafe.config.{Config, ConfigFactory}
import java.util.prefs.Preferences
import scala.collection.JavaConversions._
import suiryc.scala.RichEnumeration
import suiryc.scala.io.{DirectoryFileFilter, PathFinder, PathsEx}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.javafx.beans.property.PersistentProperty
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.log.LogLevel
import suiryc.scala.settings.{
  BaseConfig,
  BaseSettings,
  PersistentSetting,
  SettingSnapshot,
  SettingsSnapshot
}
import suiryc.scala.misc.Units
import usbinstall.USBInstall
import usbinstall.os.{
  OSKind,
  OSSettings,
  PartitionFormat,
  SyslinuxComponent,
  SyslinuxComponentKind
}


object Settings {

  protected val confPath = "usbinstall"

  /** Core settings. */
  val core = new Settings(ConfigFactory.load().getConfig(confPath),
    Preferences.userRoot.node("suiryc.usbinstall").node(confPath))

  def load() {
    // Settings are automatically loaded by accessing this object for the
    // first time.
  }

}

class Settings(
  config: Config,
  prefs: Preferences
) extends BaseSettings(config, prefs)
{

  import BaseConfig._

  implicit private val settings: BaseSettings = this
  implicit private val errorActionEnum = ErrorAction
  implicit private val logThresholdEnum = LogLevel

  val logDebugPattern = config.getString("log.debug.pattern")
  val logInstallPattern = config.getString("log.install.pattern")

  val oses = config.getConfigList("oses").toList map { config =>
    val kind = config.getString("kind")
    val label = option[String]("label", config).getOrElse(kind)

    implicit val settings = new BaseSettings(config, prefs.node("oses").node(label.replace('/', '_')))

    new OSSettings(
      OSKind.byName(kind),
      label,
      Units.storage.fromHumanReadable(config.getString("size")),
      option[String]("iso.pattern", config) map { _.r },
      config.getString("partition.label"),
      PartitionFormat.byName(config.getString("partition.format")),
      option[String]("syslinux.label", config),
      option[String]("syslinux.version", config)
    )
  }

  val isoPath = config.getStringList("iso.path").toList map { path =>
    PathsEx.get(path)
  }

  val isos = isoPath.flatMap { path =>
    ((path:PathFinder) **(""".*\.iso""".r, DirectoryFileFilter, true, Some(2))).get map(_.toPath)
  }.sortBy { _.toString }.reverse

  val toolsPath = config.getStringList("tools.path").toList map { path =>
    PathsEx.get(path)
  }

  protected val syslinuxExtra = config.getConfig("syslinux.extra")

  val syslinuxExtraImagesPath = syslinuxExtra.getStringList("images.path").toList map { path =>
    PathsEx.get(path)
  }

  lazy val syslinuxExtraComponents = syslinuxExtra.getConfigList("components").toList map { config =>
    val kind = option[String]("kind", config).getOrElse("image")
    val label = option[String]("label", config).getOrElse(kind)
    val image = option[String]("image", config).flatMap { name =>
      val r = syslinuxExtraImagesPath.map { path =>
        path.resolve(name)
      }.find(_.toFile.exists)

      if (r.isEmpty) {
        Dialogs.error(
          owner = Some(USBInstall.stage),
          title = Some("Missing component image"),
          headerText = Some(label),
          contentText = Some(s"Image[$name] not found in configured path")
        )
      }

      r
    }

    new SyslinuxComponent(
      SyslinuxComponentKind.byName(kind),
      label,
      image
    )
  }

  val rEFIndPath = PathsEx.get(config.getString("refind.path"))

  val logDebugThreshold =
    PersistentProperty(PersistentSetting.from("logDebugThreshold", LogLevel.DEBUG))

  val logInstallThreshold =
    PersistentProperty(PersistentSetting.from("logInstallThreshold", LogLevel.INFO))

  val componentInstallError =
    PersistentProperty(PersistentSetting.from("componentInstallError", ErrorAction.Ask))

  def reset() {
    logDebugThreshold.reset()
    logInstallThreshold.reset()
    componentInstallError.reset()

    for (os <- oses) {
      os.reset()
    }
  }

  def snapshot(snapshot: SettingsSnapshot) {
    snapshot.add(
      SettingSnapshot(logDebugThreshold),
      SettingSnapshot(logInstallThreshold),
      SettingSnapshot(componentInstallError)
    )

    for (os <- oses) {
      os.snapshot(snapshot)
    }
  }

}


object ErrorAction extends Enumeration {
  val Ask = Value
  val Stop = Value
  val Skip = Value
}
