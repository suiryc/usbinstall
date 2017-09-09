package usbinstall.os

import scala.util.matching.Regex
import suiryc.scala.io.PathFinder
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI


class ArchLinuxInstall(
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
      val uuid = settings.partition.get.get.uuid.fold(throw _, v => v)

      val confs = PathFinder(targetRoot) / (("arch" / "boot" / "syslinux") ++ ("loader" / "entries")) * (".*\\.cfg".r | ".*\\.conf".r)
      val regex = new Regex("""(?i)([ \t]*(?:options|kernel|append)[ \t]+[^\r\n]*[ \t]+)archisolabel=[^\s]+""", "pre")
      val regexReplacer = RegexReplacer(regex, (m: Regex.Match) =>
        s"${m.group("pre")}archisodevice=/dev/disk/by-uuid/$uuid"
      )
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, regexReplacer)
      }
    }
  }

}
