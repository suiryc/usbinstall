package usbinstall.os

import java.nio.file.Paths
import scala.util.matching.Regex
import suiryc.scala.io.PathFinder
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.sys.Command
import suiryc.scala.util.matching.RegexReplacer
import suiryc.scala.util.RichEither._
import usbinstall.InstallUI


class UbuntuInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
{

  override def requirements(): Set[String] = {
    val extra = if (settings.persistent.get) {
      Set("dd")
      //Set("cpio", "lzma")
    } else {
      Set.empty
    }
    super.requirements() ++ extra
  }

  override def setup(partMount: PartitionMount): Unit = {
    val targetRoot = partMount.to.toAbsolutePath
    val persistent = settings.persistent.get

    // Without 'casper', we need to patch 'initrd'. See comments below.
    if (!targetRoot.resolve("casper").isDirectory) {
      throw new Exception("Ubuntu LiveCD without 'casper' directory are not handled")
    }

    // Not always necessary, but without 'fallback.efi' OS may not boot
    val grubx64EFI = PathFinder(targetRoot) / "(?i)EFI".r / "(?i)BOOT".r / "(?i)grubx64.efi".r
    grubx64EFI.get().headOption.map(_.toPath).foreach { grubx64EFI =>
      val fallbackEFI = grubx64EFI.getParent.resolve("fallback.efi")
      if (!fallbackEFI.exists) ui.action("Prepare EFI") {
        duplicate(grubx64EFI, targetRoot, fallbackEFI, None)
      }
    }

    renameSyslinux(targetRoot)

    ui.action("Prepare syslinux") {
      val uuid = settings.partition.optPart.get.uuid.fold(throw _, v => v)

      // Update 'casper'
      targetRoot.resolve(Paths.get(".disk", "casper-uuid-override")).toFile.write(s"$uuid\n")
      val confs = PathFinder(targetRoot) / (("boot" / "grub") ++ "syslinux") * (".*\\.cfg".r | ".*\\.conf".r)
      val regexUUID = new Regex("""(?i)([ \t]+(?:linux|append)[ \t]+[^\r\n]*boot=casper)""", "pre")
      val regexUUIDReplacer = RegexReplacer(regexUUID, (m: Regex.Match) =>
        s"${m.group("pre")} uuid=$uuid"
      )

      val rrs =
        if (persistent) {
          val regexPers = new Regex("""(?i)([ \t]+(?:linux|append)[ \t]+[^\r\n]*(?:boot=casper|initrd=[^\s]*))""", "pre")
          val regexPersReplacer = RegexReplacer(regexPers, (m: Regex.Match) =>
            s"${m.group("pre")} persistent"
          )
          regexUUIDReplacer :: regexPersReplacer :: Nil
        } else {
          regexUUIDReplacer :: Nil
        }
      for (conf <- confs.get()) {
        regexReplace(targetRoot, conf, rrs:_*)
      }
    }

    if (persistent) {
      // Generate the persistence file
      ui.action("Generate persistency file") {
        val persistenceFile = targetRoot.resolve("casper-rw")
        // Note: leave 1 MiB for bootloader etc
        val sizeMB = targetRoot.toFile.getUsableSpace / (1024L * 1024L) - 1
        ui.activity(s"There is ${ if (sizeMB < 1024) "only " else "" }${sizeMB}MiB available for persistency")

        Command.execute(Seq("dd", "bs=1M", s"count=$sizeMB", "if=/dev/zero", s"of=$persistenceFile")).toEither("Failed to create persistency file") &&
          Command.execute(Seq("mkfs.ext4", "-F", persistenceFile.toString)).toEither("Failed to format persistency file")
      }
    }
    ()
  }

}

// Without 'casper' - which indicates in a file the partition it belongs to by
// its UUID - the LiveCD needs its 'initrd' to be patched to be able to boot on
// a given partition. In this case, adding a file with our own UUID and patching
// syslinux config becomes unnecessary.
// Original (bash) code is kept here for reference:
//
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
