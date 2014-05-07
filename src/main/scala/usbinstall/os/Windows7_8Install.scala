package usbinstall.os

import java.nio.file.{Files, Paths}
import scala.language.postfixOps
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.sys.Command
import usbinstall.InstallUI


class Windows7_8Install(
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

    val bootx64 = targetRoot.resolve(Paths.get("efi", "boot", "bootx64.efi"))
    if (!bootx64.exists) ui.action("Copy EFI boot file") {
      val wim = sourceRoot.resolve(Paths.get("sources", "install.wim"))
      val wimExtract = "1/Windows/Boot/EFI/bootmgfw.efi"
      val bootmgfw = targetRoot.resolve(Paths.get("efi", "boot", "bootmgfw.efi"))

      ui.activity(s"Extract source[${sourceRoot.relativize(wim)}] file[$wimExtract] to[${bootmgfw.getParent}]")
      Command.execute(Seq(
        "7z", "e",
        wim.toString,
        wimExtract,
        s"-o${bootmgfw.getParent.toString}"
      ))

      ui.activity(s"Rename source[${targetRoot.relativize(bootmgfw)}] target[${targetRoot.relativize(bootx64)}]")
      Files.move(bootmgfw, bootx64)
    }
    /* Original code to handle booting on FAT partition: */
//    # Note: not necessary with NTFS
//    local syslinuxVersion=${install_component_syslinux_version[${_flavor}]}
//    if [ -n "${syslinuxVersion}" ]
//    then
//        local syslinuxLocation=\${syslinux${syslinuxVersion}Location}
//
//        updateStatus dialogStatus " * Apply syslinux"
//
//        mkdir -p "${dirPartMount}"/syslinux/modules \
//            && cp "${syslinuxLocation}"/com32/libutil/libutil.c32 "${syslinuxLocation}"/com32/lib/libcom32.c32 \
//                "${syslinuxLocation}"/com32/chain/chain.c32 "${dirPartMount}"/syslinux/modules \
//            && sync
//        checkReturnCode "Failed to copy syslinux" 2
//
//        local confFile="${dirPartMount}/syslinux/${syslinuxFile}"
//        # syslinux want some default
//        echo "PATH modules
//DEFAULT bootmgr
//TIMEOUT 1
//
//KERNEL chain.c32
//APPEND boot fs ntldr=/bootmgr
//
//LABEL bootmgr
//    KERNEL chain.c32
//    APPEND boot fs ntldr=/bootmgr
//" > "${confFile}"
//
//        sync
//        checkReturnCode "Failed to sync filesystem" 2
//    fi
  }

}
