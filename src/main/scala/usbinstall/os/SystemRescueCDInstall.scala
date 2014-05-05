package usbinstall.os

import java.nio.file.{Files, Paths}
import scala.language.postfixOps
import suiryc.scala.io.FilesEx
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI


class SystemRescueCDInstall(
  override val settings: OSSettings,
  override val ui: InstallUI
) extends OSInstall(settings, ui, efi = false)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val source = isoMount.get.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.get.to.toAbsolutePath
    val finder = source ***

    ui.action("Copy ISO content") {
      finder.get.toList.sortBy(_.getPath) foreach { file =>
        val pathFile = file.toAbsolutePath
        val pathRelative = sourceRoot.relativize(pathFile)
        val pathTarget = targetRoot.resolve(pathRelative)
        if (pathTarget.exists)
          logger.warn(s"Source[$sourceRoot] path[$pathRelative] already processed, skipping")
        else {
          ui.activity(s"Copying file[$pathRelative] from[$sourceRoot] to[$targetRoot]")
          FilesEx.copy(
            sourceRoot,
            pathRelative,
            targetRoot,
            followLinks = false
          )
        }
      }
    }

    val syslinuxFile = Paths.get(targetRoot.toString(), "syslinux", settings.syslinuxFile)
    if (!syslinuxFile.exists) {
      val isolinuxCfg = Paths.get(targetRoot.toString(), "isolinux", "isolinux.cfg")
      if (isolinuxCfg.exists) ui.action("Rename isolinux folder to syslinux") {
        syslinuxFile.getParent().delete(true)
        ui.activity(s"Rename source[${isolinuxCfg.getParent()}] target[${syslinuxFile.getParent()}]")
        Files.move(isolinuxCfg, isolinuxCfg.getParent().resolve(syslinuxFile.getFileName()))
        Files.move(isolinuxCfg.getParent(), syslinuxFile.getParent())
      }
    }

    RegexReplacer.inplace(syslinuxFile, RegexReplacer("scandelay=1", "scandelay=5"))
  }

}
