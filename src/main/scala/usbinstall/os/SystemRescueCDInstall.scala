package usbinstall.os

import scala.util.matching.Regex
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

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val source = isoMount.get.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.get.to.toAbsolutePath
    val finder = source.***

    copy(finder, sourceRoot, targetRoot, settings.partitionFormat, "Copy ISO content")

    renameSyslinux(targetRoot)

    ui.action("Prepare syslinux") {
      val grubBoot = targetRoot / "boot" / "grub"
      val confs = (grubBoot ++ (targetRoot / "syslinux")) * (".*\\.cfg".r | ".*\\.conf".r)
      val regexReplacers = List(
        RegexReplacer("(?i)scandelay=1", "scandelay=2"),
        RegexReplacer(
          new Regex("""(?i)([ \t]+(?:linux|initrd)[ \t]+)/isolinux/""", "pre"),
          (m: Regex.Match) => s"${m.group("pre")}/syslinux/"
        )
      )
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, regexReplacers:_*)
      }
    }
  }

}
