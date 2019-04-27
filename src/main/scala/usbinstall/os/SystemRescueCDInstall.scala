package usbinstall.os

import scala.util.matching.Regex
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder
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
      val uuid = settings.partition.get.get.uuid.fold(throw _, identity)
      val confs = PathFinder(targetRoot) / (("sysresccd" / "boot" / "syslinux") ++ ("boot" / "grub")) * (".*\\.cfg".r | ".*\\.conf".r)
      val regex = new Regex("""(?i)([ \t]*(?:linux|options|kernel|append)[ \t]+[^\r\n]*[ \t]+)archisolabel=[^\s]+""", "pre")
      val regexReplacer = RegexReplacer(regex, (m: Regex.Match) =>
        s"${m.group("pre")}archisodevice=/dev/disk/by-uuid/$uuid"
      )
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, regexReplacer)
      }
    }
  }

}
