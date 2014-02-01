package usbinstall.os

import usbinstall.settings.InstallSettings
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.sys.linux.DevicePartition


class OSInstall(val settings: OSSettings) {

  /**
   * Prepares OS installation.
   *
   * XXX - reword ?
   * Does anything necessary before partition is formatted (if requested) and
   * actual OS installation.
   * E.g.: find/compile tools files necessary for OS installation.
   */
  def prepare: Unit = {}

  /**
   * Installs OS.
   *
   * Partition is already formatted.
   * EFI setup and syslinux bootloader install are performed right after.
   */
  def install: Unit = {}

  /**
   * Performs any post-install steps.
   */
  def postInstall: Unit = {}

}

object OSInstall {

  def apply(settings: OSSettings): OSInstall = settings.kind match {
    case OSKind.GPartedLive =>
      new GPartedLiveInstall(settings)

      /* XXX */
  }

  def prepare(os: OSInstall): Unit =
    if (os.settings.installStatus() == OSInstallStatus.Install)
      os.prepare

  private def mountAndDo(os: OSInstall, todo: => Unit): Unit = {
    val iso = os.settings.iso() map { pathISO =>
      new PartitionMount(pathISO, InstallSettings.pathMountISO)
    }
    val part = os.settings.partition() map { partition =>
      new PartitionMount(partition.dev, InstallSettings.pathMountPartition)
    }

    try {
      iso foreach { _.mount }
      part foreach { _.mount }
      todo
    }
    /* XXX - catch and log */
    finally {
      /* XXX - sync ? */
      /* XXX - try/catch ? */
      part foreach { _.umount }
      iso foreach { _.umount }
    }
  }

  private def preparePartition(part: DevicePartition, kind: PartitionFormat.Value, label: String) = {
    import suiryc.scala.misc.RichEither._

    def format = {
      val command = kind match {
        case PartitionFormat.ext2 =>
          Seq(s"mkfs.${kind}", part.dev.getPath)

        case PartitionFormat.fat32 =>
          Seq(s"mkfs.vfat", "-F", "32", part.dev.getPath)

        case PartitionFormat.ntfs =>
          Seq(s"mkfs.${kind}", "--fast", part.dev.getPath)
      }

      Command.execute(command).toEither("Failed to format partition")
    }

    def setType = {
      val id = kind match {
        case PartitionFormat.ext2 => "83"
        case PartitionFormat.fat32 => "b"
        case PartitionFormat.ntfs => "7"
      }

      /* XXX */
//# Set partition type
//part_set_type()
//{
//    local diskpath=${1%%[0-9]*}
//    local diskpart=${1##*[^0-9]}
//
//    # slow devices need some time before being usable
//    sleep 1
//
//    fdisk ${diskpath} <<EOF && partprobe -d ${diskpath}
//t
//${diskpart}
//$2
//w
//EOF
//}

      /* && */Command.execute(Seq("partprobe", "-d", part.device.dev.getPath)).toEither("Failed to set partition type")
    }

    def setLabel = {
      val (command, envf) = kind match {
        case PartitionFormat.ext2 =>
          /* Max ext2 label length: 16 */
          (Seq("e2label", part.dev.getPath, label), None)

        case PartitionFormat.fat32 =>
          def commandEnvf(env: java.util.Map[String, String]) {
            env.put("MTOOLS_SKIP_CHECK", "1")
          }

          val actualLabel = label.take(11).padTo(11, ' ')
          /* Max FAT32 label length: 11
           * To work correctly, it is better to truncate/pad it.
           */
          (Seq(s"mlabel", "-i", part.dev.getPath, s"::$actualLabel"), Some(commandEnvf _))

        case PartitionFormat.ntfs =>
          (Seq("ntfslabel", "--force", part.dev.getPath, label), None)
      }

      Command.execute(command, envf = envf).toEither("Failed to label partition")
    }

    val r = format && setType && setLabel

    /* slow devices need some time before being usable */
    Thread.sleep(1000)

    r
  }

  def install(os: OSInstall): Unit = {
    /* XXX - prepare syslinux (find files) - if required (install, or else ?) ? */
    if (os.settings.installStatus() == OSInstallStatus.Install) {
      os.settings.syslinuxVersion foreach { version =>
        SyslinuxInstall.get(version)
      }
    }

    /* XXX - prepare partition (format, set type, set label) if required */
    os.settings.partitionLabel

    /* XXX - only if 'install' required (and something to do ?) */
    mountAndDo(os, os.install)

    /* XXX - prepare EFI */
//efi_setup()
//{
//    local _idx=$1
//    local bootloader=
//
//    if [ -n "${install_component_efi_bootloader[${_idx}]}" ]
//    then
//        efi_find_bootloader "${dirPartMount}" bootloader
//    fi
//    install_component_efi_bootloader[${_idx}]="${bootloader}"
//}
//
//efi_find_bootloader() {
//    local _dirSearch=$1
//    local _varName=$2
//
//    eval ${_varName}=
//
//    local _bootloader=${_dirSearch}
//    _bootloader=$(find "${_bootloader}"/ -maxdepth 1 -type d -iname "efi" 2> /dev/null | head -n 1)
//    if [ -z "${_bootloader}" ]
//    then
//        return
//    fi
//    _bootloader=$(find "${_bootloader}"/ -maxdepth 1 -type d -iname "boot" 2> /dev/null | head -n 1)
//    if [ -z "${_bootloader}" ]
//    then
//        return
//    fi
//    _bootloader=$(find "${_bootloader}"/ -maxdepth 1 -type f -iname "bootx64.efi" 2> /dev/null | head -n 1)
//    if [ -z "${_bootloader}" ]
//    then
//        return
//    fi
//
//    eval ${_varName}=\${_bootloader:\${#_dirSearch}+1}
//}

    /* XXX - install syslinux if avaibale - and required ? */
//bootloader_install()
//{
//    local _idx=$1
//    local partpath=${install_component_partition[${_idx}]}
//    local parttype=${install_component_partition_type[${_idx}]}
//    local syslinuxVersion=${install_component_syslinux_version[${_idx}]}
//    eval local syslinuxLocation=\${syslinux${syslinuxVersion}Location}
//
//    case "${parttype}" in
//    ext*)
//        "${syslinuxLocation}"/extlinux/extlinux --install "${dirPartMount}/syslinux"
//        ;;
//
//    ntfs|fat*)
//        # Note: it is safer (and mandatory for NTFS) to unmount partition first
//        install_cleanMount "${dirPartMount}"
//        "${syslinuxLocation}"/linux/syslinux --install "${partpath}"
//        ;;
//
//    *)
//        echo "Warning: Could not setup bootloader[$1]; unhandled partition type[${parttype}]"
//        return 1
//        ;;
//    esac
//}
  }

  def postInstall(os: OSInstall): Unit = {
    /* XXX - only if 'install' required (and something to do ?) */
    mountAndDo(os, os.postInstall)
  }

}
