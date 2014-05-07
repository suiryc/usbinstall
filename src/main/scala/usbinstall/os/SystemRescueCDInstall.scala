package usbinstall.os

import scala.language.postfixOps
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI


class SystemRescueCDInstall(
  override val settings: OSSettings,
  override val ui: InstallUI
) extends OSInstall(settings, ui)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val source = isoMount.get.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.get.to.toAbsolutePath
    val finder = source ***

    copy(finder, sourceRoot, targetRoot, "Copy ISO content")

    val syslinuxFile = getSyslinuxFile(targetRoot)
    renameSyslinux(targetRoot)

    ui.action("Prepare syslinux") {
      RegexReplacer.inplace(syslinuxFile, RegexReplacer("(?i)scandelay=1", "scandelay=5"))
    }
  }

}
