package usbinstall.os

import java.nio.file.Path
import scala.collection.mutable
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.{PathFinder, RegularFileFilter}
import suiryc.scala.sys.{Command, CommandResult}
import usbinstall.InstallUI
import usbinstall.settings.{InstallSettings, Settings}


class SyslinuxInstall(
  override val settings: OSSettings,
  override val ui: InstallUI
) extends OSInstall(settings, ui)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    /* XXX - TODO */
  }

}

object SyslinuxInstall {

  private val versions = mutable.Map[Int, Option[Path]]()

  def get(version: Int) =
    versions.getOrElseUpdate(version, find(version))

  protected def find(version: Int): Option[Path] = {
    findArchive(version).fold[Option[Path]] {
      /* XXX - log error */
      None
    } { path =>
      findBase(uncompress(path)).fold[Option[Path]] {
        /* XXX - log error */
        None
      } { path =>
        build(path)
        Some(path)
      }
    }
  }

  protected def findArchive(version: Int): Option[Path] = {
    val files = Settings.core.toolsPath flatMap { path =>
      val finder = PathFinder(path) ** (s"""syslinux.*-${version}.*""".r & RegularFileFilter)

      finder.get map(_.toPath)
    }
    files.sorted.reverse.headOption
  }

  protected def uncompress(path: Path): Path = {
    val isZip = path.getFileName.toString.endsWith(".zip")

    val uncompressPath = InstallSettings.pathTemp.resolve(path.getFileName)
    uncompressPath.toFile.mkdirs
    val CommandResult(result, stdout, stderr) =
      if (isZip) Command.execute(Seq("unzip", "-qq", path.toString, "-d", uncompressPath.toString))
      else Command.execute(Seq("tar", "xf", path.toString, "-C", uncompressPath.toString))

      if (result != 0) {
        /* XXX - log error; missing file will be apparent later */
      }

    uncompressPath
  }

  protected def findBase(root: Path): Option[Path] = {
    def parentOption(path: Path) = Option(path.getParent)

    val finder = PathFinder(root) ** "extlinux" / "extlinux"
    finder.get.toList.sorted.headOption map(_.toPath) flatMap(parentOption) flatMap(parentOption)
  }

  protected def build(base: Path) {
    /*import scala.collection.JavaConversions._

    def commandEnvf(env: java.util.Map[String, String]) {
      env.put("DEBUG", "")
    }

    val CommandResult(result, arch, stderr) = Command.execute(Seq("uname", "-i"))

    if ((result == 0) && (arch != "i386")) {
      Command.execute(Seq("make"), workingDirectory = Some(base.toFile), envf = Some(commandEnvf _))
    }*/
  }

}
