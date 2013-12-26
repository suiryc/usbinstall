package usbinstall

import com.typesafe.config.{Config, ConfigFactory}
import dev.scalascript.io.{DirectoryFileFilter, PathFinder, RichFile}
import dev.scalascript.io.NameFilter._
import dev.scalascript.io.PathFinder._
import java.io.File
import scala.collection.JavaConversions._
import scala.language.postfixOps


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

  val oses = config.getConfigList(optionPath("oses")).toList map { config =>
    val kind = config.getString("kind")
    val label = option(config, "label") getOrElse(kind)
    new OSSettings(
      kind,
      label,
      Utils.fromHumanReadableSize(config.getString("size")),
      option(config, "iso.pattern") map { _.r },
      config.getString("partition.label"),
      PartitionFormat.withName(config.getString("partition.format").toLowerCase()),
      option(config, "syslinux.label"),
      option(config, "syslinux.version") map { _.toInt },
      option(config, "status") map { OSInstallStatus.withName(_) } getOrElse(OSInstallStatus.Install)
    )
  }

  val isoPath = config.getStringList(optionPath("iso.path")).toList map { path =>
    if (path.startsWith("~")) {
      val rest = path.substring(2)
      if (rest == "") RichFile.userHome
      else new File(RichFile.userHome, rest)
    }
    else new File(path)
  }

  def isos = isoPath flatMap { path =>
    ((path:PathFinder) **(""".*\.iso""".r, DirectoryFileFilter, true, Some(2))).get()
  } sortBy { _.toString() } reverse

}
