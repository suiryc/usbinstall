package usbinstall.os

import java.nio.file.Paths
import scala.util.matching.Regex
import suiryc.scala.io.PathFinder
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI


class RedHatInstall(
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

    copy(finder, sourceRoot, targetRoot, "Copy ISO content")

    val syslinuxFile = getSyslinuxFile(targetRoot)
    renameSyslinux(targetRoot)

    ui.action("Prepare syslinux") {
      val partition = settings.partition.get.get
      val uuid = partition.uuid.fold(throw _, v => v)
      var fsType = partition.fsType.fold(throw _, v => v)
      val optuuid = s"UUID=${uuid}"

      /* Original code for persistence */
//    local parttype=$(blkid -s TYPE -o value "${partpath}")
//    local partuuid=$(blkid -o value -s UUID "${partpath}")
//    local optuuid="UUID=${partuuid}"
//
//    local dirEFIBoot=$(find "${dirPartMount}" -maxdepth 1 -type d -iname 'EFI')
//    if [ -n "${dirEFIBoot}" ]
//    then
//        dirEFIBoot=$(find "${dirEFIBoot}" -maxdepth 1 -type d -iname 'BOOT')
//    fi
//    if [ ${_persistence} -ne 0 ]
//    then
//        find "${dirEFIBoot}" "${dirPartMount}"/syslinux -maxdepth 1 \( -name "*.cfg" -or -name "*.conf" \) \
//            | xargs grep -lEi "[[:blank:]]+(kernel|append|linuxefi)[[:blank:]]+.*initrd" \
//            | xargs perl -pi -e "s/([ \t]+(?:kernel|append|linuxefi)[ \t]+.*)[ \t]+ro[ \t]+(|.*[ \t]+)(liveimg[ \t]+)/\1 rw \2\3overlay=${optuuid}:\/LiveOS\/overlay-${partuuid} /i"
//        checkReturnCode "Failed to prepare syslinux" 2
//    fi

      val efiBoot = targetRoot / "(?i)EFI".r / "(?i)BOOT".r
      val confs = (efiBoot ++ (targetRoot / "syslinux")) * (".*\\.cfg".r | ".*\\.conf".r)
      val regexReplacers = List(RegexReplacer(
        new Regex("(?i)([ \t]+(?:kernel|append|linuxefi|initrdefi)[ \t]+)/isolinux/", "pre"),
        (m: Regex.Match) => s"${m.group("pre")}/syslinux/"
      ), RegexReplacer(
        new Regex("(?i)([ \t]+(?:kernel|append|linuxefi|initrdefi)[ \t]+.*[ \t]+root=live:)[^ \t]+", "pre"),
        (m: Regex.Match) => s"${m.group("pre")}${optuuid}"
      ), RegexReplacer(
        new Regex("(?i)([ \t]+(?:kernel|append|linuxefi|initrdefi)[ \t]+.*[ \t]+rootfstype=)[^ \t\r\n]+", "pre"),
        (m: Regex.Match) => s"${m.group("pre")}${fsType}"
      ))
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, regexReplacers:_*)
      }
    }

    /* Original code for persistence */
//    persistenceFile="${dirPartMount}"/LiveOS/overlay-"${partuuid}"
//    if [ ${_persistence} -ne 0 ]
//    then
//        # Generate file for persistency
//        touch "${persistenceFile}"
//        persistencySize=$(($(df -B 1 "${dirPartMount}" | tail -n 1 | awk '{print $4}')/(1024*1024) - 1))
//        if [ ${persistencySize} -lt 1024 ]
//        then
//            dialog --yesno "There is ${persistencySize}MiB available for persistency, which is less than 1GiB.\nContinue anyway ?" 20 70
//            if [ $? -ne 0 ]
//            then
//                _persistence=0
//                rm "${persistenceFile}"
//            fi
//        fi
//    fi
//    if [ ${_persistence} -ne 0 ]
//    then
//        updateStatus dialogStatus " * Generate ${persistencySize}MiB file for persistency"
//        dd if=/dev/zero of="${persistenceFile}" bs=1M count=${persistencySize} \
//            && mkfs.ext4 -F "${persistenceFile}"
//        checkReturnCode "Failed to generate persistency file" 2
//    fi
  }

}
