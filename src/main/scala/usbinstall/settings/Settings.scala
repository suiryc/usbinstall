package usbinstall.settings

import com.typesafe.config.{Config, ConfigFactory}
import java.io.File
import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex
import suiryc.scala.RichEnumeration
import suiryc.scala.io.{DirectoryFileFilter, PathFinder, PathsEx}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.javafx.beans.property.ConfigEntryProperty
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.log.LogLevel
import suiryc.scala.settings.{BaseConfig, BaseConfigImplicits, ConfigEntry, PortableSettings, SettingSnapshot, SettingsSnapshot}
import suiryc.scala.misc.{Units, Util}
import usbinstall.USBInstall
import usbinstall.os.{OSKind, OSSettings, PartitionFilesystem, SyslinuxComponent, SyslinuxComponentKind}


object Settings {

  private[usbinstall] val KEY_SUIRYC = "suiryc"
  private[usbinstall] val KEY_USBINSTALL = "usbinstall"

  private[usbinstall] val KEY_SETTINGS = "settings"

  private val KEY_DEBUG = "debug"
  private val KEY_INSTALL = "install"
  private val KEY_LOG = "log"
  private val KEY_PATTERN = "pattern"
  private val KEY_THRESHOLD = "threshold"

  private[usbinstall] val prefix = List(KEY_SUIRYC, KEY_USBINSTALL)

  private val appPath: Path = Util.classLocation[this.type]

  private def processProfile[A](file: File)(f: => Option[A]): Option[A] = {
    try {
      f
    } catch {
      case ex: Exception =>
        Dialogs.warning(
          owner = Some(USBInstall.stage),
          title = Some("Invalid profile"),
          headerText = Some("Failed to parse profile"),
          contentText = Some(s"File: $file"),
          ex = Some(ex)
        )
        None
    }
  }

  private val profilesConfig =
    Option(Thread.currentThread.getContextClassLoader.getResource("profiles")).toList.flatMap { r =>
      val d = new File(r.getFile)
      if (d.isDirectory) {
        d.listFiles.toList.filter { f =>
          f.isFile && f.getName.endsWith(".conf")
        }
      } else {
        Nil
      }
    }.flatMap { file =>
      processProfile(file) {
        val profileConfig = ConfigFactory.parseFile(file).resolve()
        if (profileConfig.hasPath("name")) {
          val name = profileConfig.getString("name")
          Some((name, file, profileConfig))
        } else {
          None
        }
      }
    }

  /** Core settings. */
  val core = new Settings(appPath.resolve("application.conf"))

  /** Profiles settings. */
  val profiles: Map[String, ProfileSettings] =
    profilesConfig.flatMap {
      case (name, file, profileConfig) =>
        processProfile(file) {
          val settings = new ProfileSettings(
            core.settings,
            prefix ++ Seq("profiles", name),
            profileConfig
          )
          Some(name -> settings)
        }
    }.toMap

  def load(): Unit = {
    // Settings are automatically loaded by accessing this object for the
    // first time.
  }

}

class Settings(path: Path) extends BaseConfigImplicits {

  import Settings._

  // Prepare a fallback configuration containing all the profiles oses
  // settings.
  private val osDefaults =
    s"""
       |$KEY_SETTINGS {
       |  select = true
       |  partitionAction = "Format"
       |  setup = true
       |  bootloader = true
       |  persistence = false
       |}
    """.stripMargin
  private val fallback = profilesConfig.foldLeft(ConfigFactory.empty) { case (c1, (name, _, profileConfig)) =>
    import BaseConfig._
    profileConfig.getConfigList("oses").asScala.toList.foldLeft(c1) { case (c2, osConfig) =>
      val kind = osConfig.getString("kind")
      val label = osConfig.option[String]("label", osConfig).getOrElse(kind)
      val path = BaseConfig.joinPath(prefix ++ Seq("profiles", name, "oses", label))
      c2.withFallback(osConfig.withFallback(ConfigFactory.parseString(osDefaults)).atPath(path))
    }
  }

  private[usbinstall] val settings = PortableSettings(path, fallback, prefix)

  val logDebugPattern: String =
    ConfigEntry.from[String](settings, prefix ++ Seq(KEY_LOG, KEY_DEBUG, KEY_PATTERN)).get
  val logInstallPattern: String =
    ConfigEntry.from[String](settings, prefix ++ Seq(KEY_LOG, KEY_INSTALL, KEY_PATTERN)).get

  val logDebugThreshold: ConfigEntryProperty[LogLevel.Value] =
    ConfigEntryProperty(ConfigEntry.from(settings, LogLevel, prefix ++ Seq(KEY_LOG, KEY_DEBUG, KEY_THRESHOLD))).asInstanceOf[ConfigEntryProperty[LogLevel.Value]]

  val logInstallThreshold: ConfigEntryProperty[LogLevel.Value] =
    ConfigEntryProperty(ConfigEntry.from(settings, LogLevel, prefix ++ Seq(KEY_LOG, KEY_INSTALL, KEY_THRESHOLD))).asInstanceOf[ConfigEntryProperty[LogLevel.Value]]

  val componentInstallError: ConfigEntry[ErrorAction.Value] =
    ConfigEntry.from(settings, ErrorAction, prefix ++ Seq("component-install-error"))

  val profile: ConfigEntry[String] =
    ConfigEntry.from(settings, prefix ++ Seq("installation", "profile"))

  def snapshot(snapshot: SettingsSnapshot): Unit = {
    snapshot.add(
      SettingSnapshot(logDebugThreshold),
      SettingSnapshot(logInstallThreshold),
      SettingSnapshot(componentInstallError)
    )
  }

}

class ProfileSettings(
  settings: PortableSettings,
  prefix: Seq[String],
  config: Config
) extends BaseConfig(config)
{

  val profileName: String = config.getString("name")

  val device: ConfigEntry[String] =
    ConfigEntry.from(settings, prefix ++ Seq("installation", "device"))

  val oses: List[OSSettings] = config.getConfigList("oses").asScala.toList.map { config =>
    val kind = config.getString("kind")
    val label = option[String]("label", config).getOrElse(kind)

    new OSSettings(
      settings,
      prefix ++ Seq("oses", label),
      OSKind.byName(kind),
      label,
      Units.storage.fromHumanReadable(config.getString("size")),
      option[String]("iso.pattern", config).map { _.r },
      config.getString("partition.label"),
      PartitionFilesystem.byName(config.getString("partition.filesystem")),
      option[String]("syslinux.root", config),
      option[String]("syslinux.label", config),
      option[String]("syslinux.version", config),
      new EFISettings(option[Config]("efi", config).getOrElse(ConfigFactory.empty()))
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

  val syslinuxSettings = new SyslinuxSettings(this, config.getConfig("syslinux"))

  val rEFIndPath: Path = PathsEx.get(config.getString("refind.path"))

  val rEFIndDrivers: List[Regex] =
    option[List[String]]("refind.drivers", config).getOrElse(List(".*")).map(new Regex(_))

}

class EFISettings(config: Config) extends BaseConfig(config) {

  val loader: Option[String] = option[String]("efi.loader", config)

  val grubOverride: Option[String] = option[String]("grub.override", config)

  val grubFonts: Option[String] = option[String]("grub.fonts", config)

}

class SyslinuxSettings(profile: ProfileSettings, config: Config) extends BaseConfig(config) {

  private val menuEntries = option[Config]("menu.entries", config)

  val menuEntriesDefault: Option[String] = menuEntries.flatMap(option[String]("default", _))

  val menuEntriesHeader: Option[String] = menuEntries.flatMap(option[String]("header", _))

  private val extra = option[Config]("extra", config)

  val extraImagesPath: List[Path] = extra.toList.flatMap(_.getStringList("images.path").asScala.toList).map { path =>
    PathsEx.get(path)
  }

  lazy val extraComponents: List[SyslinuxComponent] =
    extra.toList.flatMap(_.getConfigList("components").asScala.toList).map { config =>
      val kind = option[String]("kind", config).getOrElse("image")
      val label = option[String]("label", config).getOrElse(kind)
      val image = option[String]("image", config).flatMap { imgName =>
        val r = extraImagesPath.map { path =>
          path.resolve(imgName)
        }.find(_.toFile.exists)

        if (r.isEmpty) {
          Dialogs.error(
            owner = Some(USBInstall.stage),
            title = Some("Missing component image"),
            headerText = Some(s"Image not found in configured path"),
            contentText = Some(s"Profile: ${profile.profileName}\nImage: $imgName")
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

}


object ErrorAction extends Enumeration {
  val Ask, Stop, Skip = Value
}
