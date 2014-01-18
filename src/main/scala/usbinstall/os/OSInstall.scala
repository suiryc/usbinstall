package usbinstall.os

import usbinstall.settings.InstallSettings


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

  def install(os: OSInstall): Unit = {
    /* XXX - prepare syslinux (find files) - if required (install, or else ?) ? */
    if (os.settings.installStatus() == OSInstallStatus.Install) {
    }
//syslinux_setup() {
//    local _idx=$1
//    local _syslinuxVersion=${install_component_syslinux_version[${_idx}]}
//
//    if [ -z "${_syslinuxVersion}" ]
//    then
//        return 0
//    else
//        eval find_syslinux "${_syslinuxVersion}" "\${dirTools}"/ "syslinux${_syslinuxVersion}Location"
//        eval local _syslinuxLocation=\${syslinux${_syslinuxVersion}Location}
//        if [ -z "${_syslinuxLocation}" ]
//        then
//            install_component_enabled[${_idx}]=0
//            return 0
//        fi
//    fi
//}
//# Find syslinux
//find_syslinux()
//{
//    local _version=$1
//    local _dirSearch=$2
//    local _varName=$3
//    local _toolName="syslinux"
//    local _toolArchive=
//    local _toolUnarchived=
//    local _toolLocation=
//    local _toolPresent=0
//    local _dialogStatus=
//
//    eval _toolLocation=\${${_varName}}
//    if [ -n "${_toolLocation}" ] && [ -e "${_toolLocation}" ]
//    then
//        return 0
//    fi
//
//    updateStatus _dialogStatus "Find ${_toolName} ${_version}"
//
//    updateStatus _dialogStatus " * Search ${_toolName}"
//
//    _toolArchive=$(find "${_dirSearch}"/ -type f -name "syslinux*-${_version}*" 2> /dev/null | sort | tail -n 1)
//    if [ -n "${_toolArchive}" ]
//    then
//        # Strip extension, to only check canonical name
//        local _archiveName=$(basename "${_toolArchive}")
//        _archiveName=${_archiveName%.tar*}
//        _archiveName=${_archiveName%.zip}
//        _toolUnarchived=${dirTmp}/${_archiveName}
//    fi
//
//    if [ -n "${_toolUnarchived}" ] && [ -e "${_toolUnarchived}" ]
//    then
//        _toolPresent=1
//    else
//        mkdir -p "${_toolUnarchived}"
//        tar xf "${_toolArchive}" -C "${_toolUnarchived}" 2> /dev/null \
//            || unzip -qq "${_toolArchive}" -d "${_toolUnarchived}" 2> /dev/null
//        # Actually consider tool present (not necessary to rebuild for x86_64 ?)
//        _toolPresent=1
//    fi
//
//    if [ -n "${_toolUnarchived}" ] && [ -e "${_toolUnarchived}" ]
//    then
//        _toolLocation=$(find "${_toolUnarchived}"/ -type f -path "*/extlinux/extlinux" 2> /dev/null | head -n 1)
//        if [ -n "${_toolLocation}" ]
//        then
//            _toolLocation=$(dirname "${_toolLocation}")
//            _toolLocation=$(dirname "${_toolLocation}")
//            _toolLocation=$(cd "${_toolLocation}" && pwd)
//        fi
//        if [ -n "${_toolLocation}" ] && [ ${_toolPresent} -eq 0 ]
//        then
//            if [ $(uname -i) != "i386" ]
//            then
//                updateStatus _dialogStatus " * Compile ${_toolName} for architecture["$(uname -i)"]"
//                (
//                    cd "${_toolLocation}"
//                    # Notes: 
//                    #  - unset DEBUG while calling make (usually fails otherwise
//                    #    unlike manual compilation ...)
//                    #  - recent gcc versions (4.8) or distribs (Ubuntu 13.10)
//                    #    fail to compile if gcc-multilib is not installed
//                    DEBUG= make
//                )
//            fi
//        fi
//    fi
//
//    if [ -z "${_toolLocation}" ]
//    then
//        dialog --msgbox "Could not get ${_toolName} from folder[${_dirSearch}]" 10 70
//        return 0
//    fi
//
//    updateStatus _dialogStatus " *  -> ${_toolLocation}"
//
//    eval ${_varName}=\${_toolLocation}
//}

    /* XXX - prepare partition (format, set type, set label) if required */
//# Setup partition
//part_setup()
//{
//    local _idx=$1
//    local partpath=${install_component_partition[${_idx}]}
//    local partlabel=${install_component_partition_label[${_idx}]}
//    local parttype=${install_component_partition_type[${_idx}]}
//
//    case "${parttype}" in
//    ext*)
//        mkfs.${parttype} "${partpath}" \
//            && part_setup_extX "${partpath}" "${partlabel}"
//        ;;
//
//    ntfs)
//        mkfs.${parttype} --fast "${partpath}" \
//            && part_setup_ntfs "${partpath}" "${partlabel}"
//        ;;
//
//    fat*)
//        mkfs.vfat -F ${parttype:3} "${partpath}" \
//            && part_setup_vfat "${partpath}" "${partlabel}"
//        ;;
//
//    *)
//        echo "Warning: Could not setup partition[$1]; unhandled partition type[${parttype}]"
//        return 1
//        ;;
//    esac
//
//    local _ret=$?
//    # slow devices need some time before being usable
//    sleep 1
//
//    return ${_ret}
//}
//
//# Setup ext2/3/4 partition
//part_setup_extX()
//{
//    e2label "$1" "$2" \
//        && part_set_type "$1" 83
//}
//
//# Setup vfat partition
//part_setup_vfat()
//{
//    local _label=$2
//
//    if [ ${#_label} -gt 11 ]
//    then
//        _label=${_label:0:11}
//    fi
//
//    while [ ${#_label} -lt 11 ]
//    do
//        _label="${_label} "
//    done
//
//    MTOOLS_SKIP_CHECK=1 mlabel -i "$1" "::${_label}" \
//        && part_set_type "$1" b
//}
//
//# Setup ntfs partition
//part_setup_ntfs()
//{
//    ntfslabel --force "$1" "$2" \
//        && part_set_type "$1" 7
//}
//
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
