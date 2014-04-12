package usbinstall.settings

import com.typesafe.config.{Config, ConfigFactory}
import java.io.File
import java.util.prefs.Preferences
import scala.collection.JavaConversions._
import scala.language.postfixOps
import scala.reflect.ClassTag
import suiryc.scala.io.{DirectoryFileFilter, PathFinder, RichFile}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.settings.{BaseSettings, PersistentSetting}
import suiryc.scala.misc.{EnumerationEx, Units}
import usbinstall.util.Util
import usbinstall.os.{OSKind, OSSettings, PartitionFormat}


object Settings {

  protected val confPath = "usbinstall"

  /** Core settings. */
  val core = new Settings(ConfigFactory.load().getConfig(confPath),
    Preferences.userRoot.node("suiryc.usbinstall").node(confPath))

  object default {

    val componentInstallError = ErrorAction.Ask

  }

}

class Settings(
  config: Config,
  prefs: Preferences
) extends BaseSettings(config, prefs)
{

  implicit private val settings: BaseSettings = this
  implicit private val errorAction: ErrorAction.type = ErrorAction

  protected def option(config: Config, path: String): Option[String] =
    if (config.hasPath(path)) Some(config.getString(path)) else None

  protected def file(path: String) =
    if (path.startsWith("~")) {
      val rest = path.substring(2)
      if (rest == "") RichFile.userHome
      else new File(RichFile.userHome, rest)
    }
    else new File(path)

  val oses = config.getConfigList("oses").toList map { config =>
    val kind = config.getString("kind")
    val label = option(config, "label") getOrElse(kind)

    implicit val settings = new BaseSettings(config, prefs.node("oses").node(label.replace('/', '_')))

    new OSSettings(
      OSKind(kind),
      label,
      Units.storage.fromHumanReadable(config.getString("size")),
      option(config, "iso.pattern") map { _.r },
      config.getString("partition.label"),
      PartitionFormat(config.getString("partition.format")),
      option(config, "syslinux.label"),
      option(config, "syslinux.version") map { _.toInt }
    )
  }

  val isoPath = config.getStringList("iso.path").toList map { path =>
    file(path)
  }

  val isos = isoPath flatMap { path =>
    ((path:PathFinder) **(""".*\.iso""".r, DirectoryFileFilter, true, Some(2))).get()
  } sortBy { _.toString() } reverse

  val toolsPath = config.getStringList("tools.path").toList map { path =>
    file(path)
  }

  val componentInstallError =
    PersistentSetting.forEnumerationEx("componentInstallError", Settings.default.componentInstallError)

}


object ErrorAction extends EnumerationEx {
  val Ask = Value
  val Stop = Value
  val Skip = Value
}
