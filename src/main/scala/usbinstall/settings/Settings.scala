package usbinstall.settings

import com.typesafe.config.{Config, ConfigFactory}
import java.nio.file.{Path, Paths}
import java.util.prefs.Preferences
import scala.collection.JavaConversions._
import scala.language.postfixOps
import scala.reflect.ClassTag
import suiryc.scala.io.{DirectoryFileFilter, PathFinder, RichFile}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.settings.{BaseSettings, PersistentSetting}
import suiryc.scala.misc.{EnumerationEx, Units}
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

  implicit private val settings: BaseSettings = this
  implicit private val errorAction: ErrorAction.type = ErrorAction

  protected def option(config: Config, path: String): Option[String] =
    if (config.hasPath(path)) Some(config.getString(path)) else None

  protected def makePath(path: String): Path =
    if (path.startsWith("~")) {
      val rest = path.substring(2)
      val home = RichFile.userHome.toPath
      if (rest == "") home
      else home.resolve(rest)
    }
    else Paths.get(path)

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
    makePath(path)
  }

  val isos = isoPath flatMap { path =>
    ((path:PathFinder) **(""".*\.iso""".r, DirectoryFileFilter, true, Some(2))).get map(_.toPath)
  } sortBy { _.toString } reverse

  val toolsPath = config.getStringList("tools.path").toList map { path =>
    makePath(path)
  }

  protected val syslinuxExtra = config.getConfig("syslinux.extra")

  val syslinuxExtraImagesPath = syslinuxExtra.getStringList("images.path").toList map { path =>
    makePath(path)
  }

  val syslinuxExtraComponents = syslinuxExtra.getConfigList("components").toList map { config =>
    val kind = option(config, "kind") getOrElse("image")
    val label = option(config, "label") getOrElse(kind)
    val image = option(config, "image") flatMap { name =>
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
