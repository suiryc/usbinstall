package usbinstall.settings

import com.typesafe.config.{Config, ConfigFactory}
import java.io.File
import scala.collection.JavaConversions._
import scala.language.postfixOps
import suiryc.scala.io.{DirectoryFileFilter, PathFinder, RichFile}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.misc.Units
import usbinstall.util.Util
import usbinstall.os.{OSInstallStatus, OSKind, OSSettings, PartitionFormat}


object Settings {

  /** Core settings. */
  val core = new Settings(ConfigFactory.load())

}


abstract class BaseSettings(config: Config) {

  protected val confPath: String

  protected def optionPath(name: String) = confPath + '.' + name

  /* XXX - triggers Exception ? */
  //config.checkValid(ConfigFactory.defaultReference(), confPath)

}


class Settings(config: Config) extends BaseSettings(config) {

  protected val confPath = "usbinstall"

  def option(config: Config, path: String): Option[String] =
    if (config.hasPath(path)) Some(config.getString(path)) else None

  def file(path: String) =
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

  def isos = isoPath flatMap { path =>
    ((path:PathFinder) **(""".*\.iso""".r, DirectoryFileFilter, true, Some(2))).get()
  } sortBy { _.toString() } reverse

  val toolsPath = config.getStringList(optionPath("tools.path")).toList map { path =>
    file(path)
  }

}
