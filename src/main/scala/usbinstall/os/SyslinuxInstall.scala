package usbinstall.os

import java.io.File
import scala.collection.mutable
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.{PathFinder, RegularFileFilter}
import usbinstall.settings.{InstallSettings, Settings}
import suiryc.scala.sys.{Command, CommandResult}


object SyslinuxInstall {

  private val versions = mutable.Map[Int, Option[File]]()

  def get(version: Int) =
    versions.getOrElseUpdate(version, find(version))

  protected def find(version: Int): Option[File] = {
    findArchive(version).fold[Option[File]] {
      /* XXX - log error */
      None
    } { file =>
      findBase(uncompress(file)).fold[Option[File]] {
        /* XXX - log error */
        None
      } { file =>
        build(file)
        Some(file)
      }
    }
  }

  protected def findArchive(version: Int): Option[File] = {
    val files = Settings.core.toolsPath flatMap { file =>
      val finder = PathFinder(file) ** (s"""syslinux.*-${version}.*""".r & RegularFileFilter)

      finder.get
    }
    files.sorted.reverse.headOption
  }

  protected def uncompress(file: File) = {
    val isZip = file.getName.endsWith(".zip")

    val uncompressPath = new File(InstallSettings.pathTemp, file.getName)
    uncompressPath.mkdirs
    val CommandResult(result, stdout, stderr) =
      if (isZip) Command.execute(Seq("unzip", "-qq", file.getPath, "-d", uncompressPath.getPath))
      else Command.execute(Seq("tar", "xf", file.getPath, "-C", uncompressPath.getPath))

      if (result != 0) {
        /* XXX - log error; missing file will be apparent later */
      }

    uncompressPath
  }

  protected def findBase(root: File) = {
    def parentOption(file: File) = Option(file.getParentFile)

    val finder = PathFinder(root) ** "extlinux" / "extlinux"
    finder.get.toList.sorted.headOption flatMap(parentOption) flatMap(parentOption)
  }

  protected def build(base: File) {
    /*import scala.collection.JavaConversions._

    def commandEnvf(env: java.util.Map[String, String]) {
      env.put("DEBUG", "")
    }

    val CommandResult(result, arch, stderr) = Command.execute(Seq("uname", "-i"))

    if ((result == 0) && (arch != "i386")) {
      Command.execute(Seq("make"), workingDirectory = Some(base), envf = Some(commandEnvf _))
    }*/
  }

}
