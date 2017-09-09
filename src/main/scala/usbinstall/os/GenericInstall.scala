package usbinstall.os

import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import usbinstall.InstallUI


class GenericInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val source = isoMount.get.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.get.to.toAbsolutePath
    val finder = source.***

    copy(finder, sourceRoot, targetRoot, settings.partitionFilesystem, "Copy ISO content")

    renameSyslinux(targetRoot)

    ui.action("Prepare syslinux") {
      val confs = targetRoot / "syslinux" * (".*\\.cfg".r | ".*\\.conf".r)
      val regexReplacers = List(renameSyslinuxRegexReplacer)
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, regexReplacers:_*)
      }
    }

    ui.action("Prepare grub") {
      fixGrubSearch(targetRoot)
    }
  }

}
