package usbinstall.os

import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import usbinstall.InstallUI


class GPartedLiveInstall(
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

    /* XXX - check whether errors trigger exceptions or not */
    copy(finder, sourceRoot, targetRoot, "Copy ISO content")

    renameSyslinux(targetRoot)
  }

}
