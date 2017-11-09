package usbinstall.os

import java.nio.file.{Files, Paths}
import suiryc.scala.io.RichFile._
import suiryc.scala.sys.Command
import usbinstall.InstallUI


class WindowsInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
{

  override def installRequirements(): Set[String] = {
    super.installRequirements() ++ Set("wimextract")
  }

  override def install(isoMount: PartitionMount, partMount: PartitionMount): Unit = {
    val source = isoMount.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.to.toAbsolutePath

    super.install(isoMount, partMount)

    // We need to extract the EFI boot file from the WIM/ESD image.
    // We could do it from the file copied in the partition, but it should
    // be faster to access the one from the original ISO.
    val bootx64 = targetRoot.resolve(Paths.get("efi", "boot", "bootx64.efi"))
    if (!bootx64.exists) ui.action("Copy EFI boot file") {
      val srcOpt = List("esd", "wim").map { ext =>
        sourceRoot.resolve(Paths.get("sources", s"install.$ext"))
      }.find { path =>
        path.exists
      }

      srcOpt match {
        case Some(src) =>
          val srcImage = "1"
          val srcExtract = "/Windows/Boot/EFI/bootmgfw.efi"
          val bootmgfw = targetRoot.resolve(Paths.get("efi", "boot", "bootmgfw.efi"))

          ui.activity(s"Extract source[${sourceRoot.relativize(src)}] image[$srcImage] file[$srcExtract] to[${targetRoot.relativize(bootmgfw).getParent}]")
          bootmgfw.getParent.mkdirs
          Command.execute(Seq(
            "wimextract",
            src.toString,
            srcImage,
            srcExtract,
            s"--dest-dir=${bootmgfw.getParent.toString}"
          ))

          ui.activity(s"Rename source[${targetRoot.relativize(bootmgfw)}] target[${targetRoot.relativize(bootx64)}]")
          Files.move(bootmgfw, bootx64)

        case None =>
          ui.activity("Could not find WIM/ESD install file")
      }
    }
    ()
    // Original code to handle booting on FAT partition:
    //
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
