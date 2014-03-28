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
import suiryc.scala.misc.Units
import usbinstall.util.Util
import usbinstall.os.{OSInstallStatus, OSKind, OSSettings, PartitionFormat}


object Settings {

  protected val confPath = "usbinstall"

  /** Core settings. */
  val core = new Settings(ConfigFactory.load(),
    Preferences.userRoot.node("suiryc.usbinstall").node(confPath))

}

class Settings(
  protected[settings] val config: Config,
  protected[settings] val prefs: Preferences
) {

  import Settings.confPath

  protected[settings] def optionPath(name: String) = confPath + '.' + name

  /* XXX - triggers Exception ? */
  //config.checkValid(ConfigFactory.defaultReference(), confPath)

  protected def option(config: Config, path: String): Option[String] =
    if (config.hasPath(path)) Some(config.getString(path)) else None

  protected def file(path: String) =
    if (path.startsWith("~")) {
      val rest = path.substring(2)
      if (rest == "") RichFile.userHome
      else new File(RichFile.userHome, rest)
    }
    else new File(path)

  val oses = config.getConfigList(optionPath("oses")).toList map { config =>
    val kind = config.getString("kind")
    val label = option(config, "label") getOrElse(kind)
    new OSSettings(
      OSKind(kind),
      label,
      Units.storage.fromHumanReadable(config.getString("size")),
      option(config, "iso.pattern") map { _.r },
      config.getString("partition.label"),
      PartitionFormat(config.getString("partition.format")),
      option(config, "syslinux.label"),
      option(config, "syslinux.version") map { _.toInt },
      option(config, "status") map { OSInstallStatus(_) } getOrElse(OSInstallStatus.Install)
    )
  }

  val isoPath = config.getStringList(optionPath("iso.path")).toList map { path =>
    file(path)
  }

  val isos = isoPath flatMap { path =>
    ((path:PathFinder) **(""".*\.iso""".r, DirectoryFileFilter, true, Some(2))).get()
  } sortBy { _.toString() } reverse

  val toolsPath = config.getStringList(optionPath("tools.path")).toList map { path =>
    file(path)
  }

  implicit private val settings: Settings = this

  val componentInstallError =
    PersistentSetting[String]("componentInstallError")

}


abstract class PersistentSetting[T]
{

  protected val path: String

  def apply(): T

  def update(v: T): Unit

}

class PersistentStringSetting(protected val path: String)(implicit settings: Settings) extends PersistentSetting[String]
{

  def apply(): String =
    /* XXX - more efficient way to check whether path exists and only use 'config' if not ? */
    settings.prefs.get(path, settings.config.getString(settings.optionPath(path)))

  def update(v: String) =
    settings.prefs.put(path, v)

}

object PersistentSetting {

  import scala.language.implicitConversions

  implicit def toValue[T](p :PersistentSetting[T]): T = p()

  implicit def forString(path: String)(implicit settings: Settings): PersistentSetting[String] =
    new PersistentStringSetting(path)

  def apply[T](p: PersistentSetting[T])(implicit settings: Settings) = p

}
