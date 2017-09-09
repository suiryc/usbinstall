package usbinstall.os

import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI


class SystemRescueCDInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
{

  override def setup(partMount: PartitionMount): Unit = {
    val targetRoot = partMount.to.toAbsolutePath

    renameSyslinux(targetRoot)

    ui.action("Prepare syslinux") {
      val grubBoot = targetRoot / "boot" / "grub"
      val confs = (grubBoot ++ (targetRoot / "syslinux")) * (".*\\.cfg".r | ".*\\.conf".r)
      val regexReplacers = List(
        renameSyslinuxRegexReplacer,
        RegexReplacer("(?i)scandelay=1", "scandelay=2")
      )
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, regexReplacers:_*)
      }
    }
  }

}
