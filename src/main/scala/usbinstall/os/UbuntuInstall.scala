package usbinstall.os

import java.nio.file.Paths
import scala.util.matching.Regex
import suiryc.scala.io.PathFinder
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI


class UbuntuInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
{

  //override def installRequirements() =
  //  Set("cpio", "lzma")

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val source = isoMount.get.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.get.to.toAbsolutePath
    val finder = source.***

    copy(finder, sourceRoot, targetRoot, "Copy ISO content")

    /* Without 'casper', we need to patch 'initrd'. See comments below. */
    if (!targetRoot.resolve("casper").isDirectory)
      throw new Exception("Ubuntu LiveCD without 'casper' directory are not handled")

    /* Not always necessary, but without 'fallback.efi' OS may not boot */
    val grubx64EFI = PathFinder(targetRoot) / "(?i)EFI".r / "(?i)BOOT".r / "(?i)grubx64.efi".r
    grubx64EFI.get().headOption map(_.toPath) foreach { grubx64EFI =>
      val fallbackEFI = grubx64EFI.getParent.resolve("fallback.efi")
      if (!fallbackEFI.exists) ui.action("Prepare EFI") {
        duplicate(grubx64EFI, targetRoot, fallbackEFI, None)
      }
    }

    val syslinuxFile = getSyslinuxFile(targetRoot)
    renameSyslinux(targetRoot)

    ui.action("Prepare syslinux") {
      val uuid = settings.partition.get.get.uuid.fold(throw _, v => v)

      /* Original code for persistence */
//    if [ ${_persistence} -ne 0 ]
//    then
//        find "${dirPartMount}"/boot/grub "${dirPartMount}"/syslinux -maxdepth 1 \( -name "*.cfg" -or -name "*.conf" \) \
//            | xargs grep -lEi "[[:blank:]]+(linux|append)[[:blank:]]+.*(initrd|boot=casper)" \
//            | xargs perl -pi -e 's/([ \t]+(?:linux[ \t]+.*vmlinuz(?:\.efi)?|append)[ \t]+)(.*initrd|.*boot=casper)/\1persistent \2/i'
//        checkReturnCode "Failed to prepare syslinux" 2
//    fi

      /* Update 'casper' */
      targetRoot.resolve(Paths.get(".disk", "casper-uuid-override")).toFile.write(s"$uuid\n")
      val confs = PathFinder(targetRoot) / (("boot" / "grub") ++ "syslinux") * (".*\\.cfg".r | ".*\\.conf".r)
      val regex = new Regex("(?i)([ \t]+(?:linux|append)[ \t]+.*boot=casper)", "pre")
      val regexReplacer = RegexReplacer(regex, (m: Regex.Match) =>
        s"${m.group("pre")} uuid=$uuid"
      )
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, regexReplacer)
      }
    }

    /* Original code for persistence */
//    persistenceFile="${dirPartMount}"/casper-rw
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

/*
 * Without 'casper' - which indicates in a file the partition it belongs to by
 * its UUID - the LiveCD needs its 'initrd' to be patched to be able to boot on
 * a given partition. In this case, adding a file with our own UUID and patching
 * syslinux config becomes unnecessary.
 * Original (bash) code is kept here for reference:
 */
//    local parttype=$(blkid -s TYPE -o value "${partpath}")
//    local partuuid=$(blkid -o value -s UUID "${partpath}")
//
//    local casper=0
//    if [ -d "${dirPartMount}"/casper ]
//    then
//        casper=1
//    fi
//
//    if [ ${casper} -eq 0 ]
//    then
//        updateStatus dialogStatus " * Update initrd"
//
//        local initrdPath=
//        for initrdPath in "${dirPartMount}"/casper/initrd.lz "${dirPartMount}"/casper/initrd.gz "${dirPartMount}"/install/initrd.gz
//        do
//            if [ -e "${initrdPath}" ]
//            then
//                break
//            fi
//            initrdPath=
//        done
//
//        if [ -z "${initrdPath}" ]
//        then
//            exitProgram "Could not find initrd" 2
//        fi
//
//        local initrdCompress=( 'gzip' '-9' '--stdout' )
//        local initrdDecompress=( 'gzip' '--decompress' '--stdout' )
//        if [ "${initrdPath##*.}" == "lz" ]
//        then
//            initrdCompress=( 'lzma' '-7' '--stdout' )
//            initrdDecompress=( 'lzma' '--decompress' '--stdout' '--suffix=.lz' )
//        fi
//
//        (
//            if [ -e "${dirTmp}"/initrd ]
//            then
//                rm -rf "${dirTmp}"/initrd
//            fi
//
//            mkdir "${dirTmp}"/initrd \
//                && cd "${dirTmp}"/initrd \
//                && "${initrdDecompress[@]}" "${initrdPath}" | cpio --extract --make-directories --no-absolute-filenames
//        )
//        checkReturnCode "Failed to extract initrd content" 2
//
//        # Note: BusyBox's mount does not handle the '-U UUID' option, but blkid or
//        # findfs shall be present
//        echo "#"'!'"/bin/sh
//
//partpath=\$(blkid -U \"${partuuid}\" 2> /dev/null)
//if [ -z \"\${partpath}\" ]
//then
//    partpath=\$(findfs \"UUID=${partuuid}\" 2> /dev/null)
//fi
//if [ -n \"\${partpath}\" ]
//then
//    if [ ! -e /cdrom ]
//    then
//        mkdir /cdrom
//    fi
//    mount -t ${parttype} -o ro \"\${partpath}\" /cdrom
//fi
//
//exit 0
//" > "${dirTmp}"/initrd/init.extra
//        chmod +x "${dirTmp}"/initrd/init.extra
//
//        perl -pi -e 's/^(exec .*init.*)$/\/init.extra\n\1/' "${dirTmp}"/initrd/init
//        grep -cEi "^/init\.extra$" "${dirTmp}"/initrd/init > /dev/null
//        checkReturnCode "Failed to update initrd" 2
//
//        (
//            cd "${dirTmp}"/initrd \
//                && find ./ | cpio --create --format=newc | "${initrdCompress[@]}" > "${initrdPath}"
//        )
//        checkReturnCode "Failed to update initrd" 2
//
//        rm -rf "${dirTmp}"/initrd
//    fi
