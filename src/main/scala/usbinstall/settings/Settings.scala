package usbinstall.settings

import com.typesafe.config.{Config, ConfigFactory}
import java.util.prefs.Preferences
import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import suiryc.scala.io.{DirectoryFileFilter, PathFinder, PathsEx}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.javafx.beans.property.PersistentProperty
import suiryc.scala.settings.{BaseConfig, BaseSettings, PersistentSetting}
import suiryc.scala.misc.{EnumerationEx, MessageLevel, Units}
import usbinstall.Stages
import usbinstall.os.{
  OSKind,
  OSSettings,
  PartitionFormat,
  SyslinuxComponent,
  SyslinuxComponentKind
}
import usbinstall.util.Util


object Settings {

  protected val confPath = "usbinstall"

  /** Core settings. */
  val core = new Settings(ConfigFactory.load().getConfig(confPath),
    Preferences.userRoot.node("suiryc.usbinstall").node(confPath))

  object default {

    val logDebugThreshold = MessageLevel.DEBUG

    val logInstallThreshold = MessageLevel.INFO

    val componentInstallError = ErrorAction.Ask

  }

  def load() {
    /* Settings are automatically loaded by accessing this object for the
     * first time.
     */
  }

}

class Settings(
  config: Config,
  prefs: Preferences
) extends BaseSettings(config, prefs)
{

  import BaseConfig._

  implicit private val settings: BaseSettings = this
  implicit private val errorAction: ErrorAction.type = ErrorAction
  implicit private val logThreshold: MessageLevel.type = MessageLevel

  val oses = config.getConfigList("oses").toList map { config =>
    val kind = config.getString("kind")
    val label = option[String]("label", config) getOrElse(kind)

    implicit val settings = new BaseSettings(config, prefs.node("oses").node(label.replace('/', '_')))

    new OSSettings(
      OSKind(kind),
      label,
      Units.storage.fromHumanReadable(config.getString("size")),
      option[String]("iso.pattern", config) map { _.r },
      config.getString("partition.label"),
      PartitionFormat(config.getString("partition.format")),
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

  val syslinuxExtraComponents = syslinuxExtra.getConfigList("components").toList map { config =>
    val kind = option[String]("kind", config) getOrElse("image")
    val label = option[String]("label", config) getOrElse(kind)
    val image = option[String]("image", config) flatMap { name =>
      val r = syslinuxExtraImagesPath map { path =>
        path.resolve(name)
      } find(_.toFile.exists)

      if (!r.isDefined) {
        Stages.errorStage("Missing component image", Some(label), s"Image[$name] not found in configured path")
      }

      r
    }

    new SyslinuxComponent(
      SyslinuxComponentKind(kind),
      label,
      image
    )
  }

  val rEFIndPath = PathsEx.get(config.getString("refind.path"))

  val logDebugThreshold =
    PersistentProperty(PersistentSetting.forSEnumeration("logDebugThreshold", Settings.default.logDebugThreshold))

  val logInstallThreshold =
    PersistentProperty(PersistentSetting.forSEnumeration("logInstallThreshold", Settings.default.logInstallThreshold))

  val componentInstallError =
    PersistentSetting.forEnumerationEx("componentInstallError", Settings.default.componentInstallError)

  def reset() =
    for (os <- oses) {
      os.reset()
    }

}


object ErrorAction extends EnumerationEx {
  val Ask = Value
  val Stop = Value
  val Skip = Value
}
