package usbinstall.os

import scala.language.postfixOps
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import usbinstall.InstallUI


class GPartedLiveInstall(
  override val settings: OSSettings,
  override val ui: InstallUI
) extends OSInstall(settings, ui)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val source = isoMount.get.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.get.to.toAbsolutePath
    val finder = source ***

    /* XXX - handle errors */
    copy(finder, sourceRoot, targetRoot, "Copy ISO content")

    renameSyslinux(targetRoot)
  }

}
