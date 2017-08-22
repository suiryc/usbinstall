package usbinstall.settings

import com.typesafe.config.{Config, ConfigFactory}
import java.io.File
import java.nio.file.Path
import java.util.prefs.Preferences
import scala.collection.JavaConverters._
import suiryc.scala.RichEnumeration
import suiryc.scala.io.{DirectoryFileFilter, PathFinder, PathsEx}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.javafx.beans.property.PersistentProperty
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.log.LogLevel
import suiryc.scala.settings.{BaseConfig, BaseSettings, PersistentSetting, SettingSnapshot, SettingsSnapshot}
import suiryc.scala.misc.Units
import usbinstall.USBInstall
import usbinstall.os.{OSKind, OSSettings, PartitionFormat, SyslinuxComponent, SyslinuxComponentKind}


object Settings {

  private val confPath = "usbinstall"

  val prefsRoot: Preferences = Preferences.userRoot.node("suiryc.usbinstall")

  /** Core settings. */
  val core = new Settings(ConfigFactory.load().getConfig(confPath),
    prefsRoot.node(confPath))

  /** Profiles settings. */
  val profiles: Map[String, ProfileSettings] =
    Option(Thread.currentThread.getContextClassLoader.getResource("profiles")).toList.flatMap { r =>
      val d = new File(r.getFile)
      if (d.isDirectory) {
        d.listFiles.toList.filter { f =>
          f.isFile && f.getName.endsWith(".conf")
        }
      } else {
        Nil
      }
    }.flatMap { f =>
      try {
        val c = ConfigFactory.parseFile(f).resolve()
        if (c.hasPath("name")) {
          val name = c.getString("name")
          val settings = new ProfileSettings(c, prefsRoot.node("profiles").node(name))
          Some(name -> settings)
        } else {
          None
        }
      } catch {
        case ex: Exception =>
          Dialogs.warning(
            owner = Some(USBInstall.stage),
            title = Some("Invalid profile"),
            headerText = Some("Failed to parse profile"),
            contentText = Some(s"File: $f"),
            ex = Some(ex)
          )
          None
      }
    }.toMap

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

  import PersistentSetting._

  val logDebugPattern: String = config.getString("log.debug.pattern")
  val logInstallPattern: String = config.getString("log.install.pattern")

  val logDebugThreshold =
    PersistentProperty(PersistentSetting.from(this, "logDebugThreshold", LogLevel, LogLevel.DEBUG))

  val logInstallThreshold =
    PersistentProperty(PersistentSetting.from(this, "logInstallThreshold", LogLevel, LogLevel.INFO))

  val componentInstallError =
    PersistentProperty(PersistentSetting.from(this, "componentInstallError", ErrorAction, ErrorAction.Ask))

  val profile: PersistentProperty[String] =
    PersistentProperty(PersistentSetting.from(this, "installation.profile", null))

  def reset() {
    logDebugThreshold.reset()
    logInstallThreshold.reset()
    componentInstallError.reset()
  }

  def snapshot(snapshot: SettingsSnapshot) {
    snapshot.add(
      SettingSnapshot(logDebugThreshold),
      SettingSnapshot(logInstallThreshold),
      SettingSnapshot(componentInstallError)
    )
  }

}

class ProfileSettings(
  config: Config,
  prefs: Preferences
) extends BaseSettings(config, prefs)
{

  import BaseConfig._
  import PersistentSetting._

  val profileName: String = config.getString("name")

  val device: PersistentProperty[String] =
    PersistentProperty(PersistentSetting.from(this, "device", null))

  val oses: List[OSSettings] = config.getConfigList("oses").asScala.toList.map { config =>
    val kind = config.getString("kind")
    val label = option[String]("label", config).getOrElse(kind)

    val settings: BaseSettings =
      new BaseSettings(config, prefs.node("oses").node(label.replace('/', '_')))

    new OSSettings(
      settings,
      OSKind.byName(kind),
      label,
      Units.storage.fromHumanReadable(config.getString("size")),
      option[String]("iso.pattern", config).map { _.r },
      config.getString("partition.label"),
      PartitionFormat.byName(config.getString("partition.format")),
      option[String]("syslinux.label", config),
      option[String]("syslinux.version", config)
    )
  }

  val isoPath: List[Path] = config.getStringList("iso.path").asScala.toList.map { path =>
    PathsEx.get(path)
  }

  val isos: List[Path] = isoPath.flatMap { path =>
    ((path:PathFinder) **(""".*\.iso""".r, DirectoryFileFilter, true, Some(2))).get.map(_.toPath)
  }.sortBy { _.toString }.reverse

  val toolsPath: List[Path] = config.getStringList("tools.path").asScala.toList.map { path =>
    PathsEx.get(path)
  }

  private val syslinuxExtra = config.getConfig("syslinux.extra")

  val syslinuxExtraImagesPath: List[Path] = syslinuxExtra.getStringList("images.path").asScala.toList.map { path =>
    PathsEx.get(path)
  }

  lazy val syslinuxExtraComponents: List[SyslinuxComponent] =
    syslinuxExtra.getConfigList("components").asScala.toList.map { config =>
      val kind = option[String]("kind", config).getOrElse("image")
      val label = option[String]("label", config).getOrElse(kind)
      val image = option[String]("image", config).flatMap { imgName =>
        val r = syslinuxExtraImagesPath.map { path =>
          path.resolve(imgName)
        }.find(_.toFile.exists)

        if (r.isEmpty) {
          Dialogs.error(
            owner = Some(USBInstall.stage),
            title = Some("Missing component image"),
            headerText = Some(s"Image not found in configured path"),
            contentText = Some(s"Profile: $profileName\nImage: $imgName")
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

  val rEFIndPath: Path = PathsEx.get(config.getString("refind.path"))

}


object ErrorAction extends Enumeration {
  val Ask = Value
  val Stop = Value
  val Skip = Value
}
