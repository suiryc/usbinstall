package usbinstall.os

import suiryc.scala.io.PathFinder._
import usbinstall.InstallUI


class KaliInstall(
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

    copy(finder, sourceRoot, targetRoot, settings.partitionFormat, "Copy ISO content")

    renameSyslinux(targetRoot)
  }

}
