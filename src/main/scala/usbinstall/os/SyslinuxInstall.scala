package usbinstall.os

import java.nio.file.{Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import scala.collection.mutable
import suiryc.scala.io.{PathFinder, RegularFileFilter}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.sys.{Command, CommandResult}
import usbinstall.{InstallUI, Stages}
import usbinstall.settings.{InstallSettings, Settings}


class SyslinuxInstall(
  override val settings: OSSettings,
  override val ui: InstallUI
) extends OSInstall(settings, ui)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val syslinuxVersion = settings.syslinuxVersion.get
    val syslinuxRoot = SyslinuxInstall.get(syslinuxVersion).get
    val partition = settings.partition().get
    val devicePath = partition.device.dev
    val targetRoot = partMount.get.to.toAbsolutePath

    Command.execute(Seq("parted", "-s", partition.device.dev.toString, "set", partition.partNumber.toString, "boot", "on"),
      skipResult = false)

    ui.action("Apply syslinux") {
      val pathModules = targetRoot.resolve(Paths.get("syslinux", "modules"))
      val pathImages = targetRoot.resolve(Paths.get("syslinux", "images"))
      val pathBootdisk = targetRoot.resolve("bootdisk")

      List(pathModules, pathImages, pathBootdisk) foreach(_.toFile.mkdirs())

      val finder = PathFinder(syslinuxRoot) / "com32" / (
          ("libutil" / "libutil.c32") ++
          ("lib" / "libcom32.c32") ++
          ("menu" / "vesamenu.c32") ++
          ("chain" / "chain.c32")
        ) ++
        PathFinder(syslinuxRoot) / "memdisk" / "memdisk"

      copy(finder.get().toList.map(_.toPath), syslinuxRoot, pathModules, None)
      copy(syslinuxRoot.resolve(Paths.get("sample", "syslinux_splash.jpg")), syslinuxRoot, pathImages, None)

      for {
        component <- Settings.core.syslinuxExtraComponents if (component.kind == SyslinuxComponentKind.Image)
        image <- component.image
      } {
        copy(image, image.getParent, pathBootdisk, Some(PosixFilePermissions.fromString("rw-rw-rw-")))
      }

      /* Original disabled code wth altmbr */
//    # altmbr does not seem to work as expected ...
//    #[ -e "${syslinuxLocation}"/mbr/altmbr.bin ] \
//    #    && dd bs=439 count=1 if="${syslinuxLocation}"/mbr/altmbr.bin of=${_devpath} \
//    #    && printf $(printf '\\x%02X' ${syslinuxPartnb}) | dd bs=1 count=1 of=${_devpath} seek=439

      val mbrBin = syslinuxRoot.resolve(Paths.get("mbr", "mbr.bin"))
      if (mbrBin.exists) {
        val CommandResult(result, stdout, stderr) =
          Command.execute(Seq("dd", "bs=440", "count=1", s"if=${mbrBin}", s"of=${devicePath}"))
        if (result != 0) {
          Stages.errorStage("MBR failure", Some("Could not install syslinux MBR.\nYou may have to do it manually."), stderr)
        }
      }
    }

    /* XXX - TODO */
  }

}

//post_install_syslinux()
//{
//    local _flavor=$1
//    local toolname=${install_component[${_flavor}]}
//    local partpath=${install_component_partition[${_flavor}]}
//    local partnb=
//    local partnb2=
//    local line=
//    local component=
//    local component_ext=
//    local component_label=
//    local component_iso=
//    local component_partpath=
//    local component_kernel=
//    local syslinuxFile=${install_component_syslinux_file[${_flavor}]}
//    local dialogStatus=
//
//    updateStatus dialogStatus "Post-install ${toolname}"
//
//    updateStatus dialogStatus " * Mount ${partpath} on ${dirPartMount}"
//
//    install_cleanMount "${dirPartMount}"
//    mkdir -p "${dirPartMount}"
//    mount "${partpath}" "${dirPartMount}"
//    checkReturnCode "Failed to mount partition" 2
//
//    updateStatus dialogStatus " * Backup MBR"
//
//    dd if="${_devpath}" of="${dirPartMount}"/mbr.backup bs=512 count=1 conv=notrunc
//    checkReturnCode "Failed to backup MBR" 2
//
//    local component_idx=0
//    for ((component_idx=0; component_idx<${#install_component[@]}; component_idx++))
//    do
//        component_label=${install_component_syslinux_label[${component_idx}]}
//        component_partpath=${install_component_partition[${component_idx}]}
//
//        if [ "${component}" = "syslinux" ] || [ -z "${component_label}" ] \
//        || [ -z "${component_partpath}" ]
//        then
//            continue
//        fi
//
//        partnb=${component_partpath:${#_partpath_prefix}}
//        if [ ${partnb} -eq 1 ] || [ ${partnb} -gt 4 ]
//        then
//            continue
//        fi
//
//        for ((partnb2=1; partnb2<${partnb}; partnb2++))
//        do
//            parted -s "${_devpath}" rm ${partnb2} && sync && sleep 1
//            checkReturnCode "Failed to remove partition ${partnb2}" 2
//        done
//
//        dd if="${_devpath}" of="${dirPartMount}"/mbr.part${partnb} bs=512 count=1 conv=notrunc
//        checkReturnCode "Failed to backup MBR for part ${partnb}" 2
//
//        dd if="${dirPartMount}"/mbr.backup of="${_devpath}" bs=512 count=1 conv=notrunc && sync && sleep 1
//        checkReturnCode "Failed to restore MBR" 2
//    done
//
//    updateStatus dialogStatus " * Apply syslinux"
//
//    local defaultEntry=${install_component_syslinux_label[${SYSTEMRESCUECD_IDX}]}
//    local confFile="${dirPartMount}/syslinux/${syslinuxFile}"
//    echo "PATH modules
//UI vesamenu.c32
//MENU TITLE Boot menu
//MENU BACKGROUND images/syslinux_splash.jpg
//
//PROMPT 0
//TIMEOUT 100
//ONTIMEOUT ${defaultEntry}
//
//MENU DEFAULT ${defaultEntry}
//
//" > "${confFile}"
//
//    for ((component_idx=0; component_idx<${#install_component[@]}; component_idx++))
//    do
//        component=${install_component[${component_idx}]}
//        component_label=${install_component_syslinux_label[${component_idx}]}
//        component_partpath=${install_component_partition[${component_idx}]}
//        component_kernel=${install_component_syslinux_kernel[${component_idx}]}
//        component_append=${install_component_syslinux_append[${component_idx}]}
//
//        if [ "${component}" = "syslinux" ] || [ -z "${component_label}" ] \
//        || [ -z "${component_partpath}" ]
//        then
//            continue
//        fi
//
//        echo "LABEL ${component_label}
//    MENU LABEL ^${component}" >> "${confFile}"
//
//        if [ -n "${component_kernel}" ]
//        then
//            echo "    KERNEL ${component_kernel}" >> "${confFile}"
//            if [ -n "${component_append}" ]
//            then
//                echo "    APPEND${component_append}" >> "${confFile}"
//            fi
//        else
//            partnb=${component_partpath:${#_partpath_prefix}}
//            echo "    KERNEL chain.c32
//    APPEND boot ${partnb}${component_append}" >> "${confFile}"
//        fi
//        echo >> "${confFile}"
//    done
//
//    echo "MENU SEPARATOR
//" >> "${confFile}"
//
//    for ((component_idx=0; component_idx<${#install_component[@]}; component_idx++))
//    do
//        component=${install_component[${component_idx}]}
//        component_label=${install_component_syslinux_label[${component_idx}]}
//        component_iso=${install_component_iso[${component_idx}]}
//        component_partpath=${install_component_partition[${component_idx}]}
//        component_kernel=${install_component_syslinux_kernel[${component_idx}]}
//        component_append=${install_component_syslinux_append[${component_idx}]}
//
//        if [ "${component}" = "syslinux" ] || [ -z "${component_label}" ] \
//        || [ -z "${component_iso}" ] || [ -n "${component_partpath}" ]
//        then
//            continue
//        fi
//
//        component_iso_name=$(basename "${component_iso}")
//        echo "LABEL ${component_label}
//    MENU LABEL ^${component}" >> "${confFile}"
//
//        if [ -n "${component_kernel}" ]
//        then
//            echo "    KERNEL ${component_kernel}" >> "${confFile}"
//            if [ -n "${component_append}" ]
//            then
//                echo "    APPEND${component_append}" >> "${confFile}"
//            fi
//        else
//            echo "    KERNEL modules/memdisk
//    INITRD /bootdisk/${component_iso_name}
//    APPEND iso${component_append}" >> "${confFile}"
//        fi
//        echo >> "${confFile}"
//    done
//
//    echo "MENU SEPARATOR
//
//MENU BEGIN
//MENU TITLE Misc tools
//" >> "${confFile}"
//
//    while read line
//    do
//        component=${line%%=*}
//        line=${line#*=}
//        component_entry=${line%%=*}
//        line=${line#*=}
//        component_label=${line%%=*}
//
//        if [ -z "${component}" ]
//        then
//            continue
//        fi
//
//        echo "LABEL ${component_entry}
//    MENU LABEL ^${component_label}
//    KERNEL modules/memdisk
//    INITRD /bootdisk/${component}" >> "${confFile}"
//
//        component_ext=${component##*.}
//        if [ "${component_ext}" = "iso" ]
//        then
//            echo "    APPEND iso
//" >> "${confFile}"
//        elif [ "${component_ext}" = "img" ]
//        then
//            echo "    APPEND floppy
//" >> "${confFile}"
//        fi
//    done < <(cat "${dirSyslinuxBootdisk}"/menu.txt)
//
//    echo "MENU SEPARATOR
//
//LABEL return
//  MENU LABEL Return to main menu
//  MENU EXIT
//
//MENU END
//" >> "${confFile}"
//
//    sync
//    checkReturnCode "Failed to sync filesystem" 2
//}


object SyslinuxInstall {

  private val versions = mutable.Map[Int, Option[Path]]()

  def get(version: Int) =
    versions.getOrElseUpdate(version, find(version))

  protected def find(version: Int): Option[Path] = {
    findArchive(version).fold[Option[Path]] {
      /* XXX - log error */
      None
    } { path =>
      findBase(uncompress(path)).fold[Option[Path]] {
        /* XXX - log error */
        None
      } { path =>
        build(path)
        Some(path)
      }
    }
  }

  protected def findArchive(version: Int): Option[Path] = {
    val files = Settings.core.toolsPath flatMap { path =>
      val finder = PathFinder(path) ** (s"""syslinux.*-${version}.*""".r & RegularFileFilter)

      finder.get map(_.toPath)
    }
    files.sorted.reverse.headOption
  }

  protected def uncompress(path: Path): Path = {
    val isZip = path.getFileName.toString.endsWith(".zip")

    val uncompressPath = InstallSettings.pathTemp.resolve(path.getFileName)
    uncompressPath.toFile.mkdirs
    val CommandResult(result, stdout, stderr) =
      if (isZip) Command.execute(Seq("unzip", "-qq", path.toString, "-d", uncompressPath.toString))
      else Command.execute(Seq("tar", "xf", path.toString, "-C", uncompressPath.toString))

      if (result != 0) {
        /* XXX - log error; missing file will be apparent later */
      }

    uncompressPath
  }

  protected def findBase(root: Path): Option[Path] = {
    def parentOption(path: Path) = Option(path.getParent)

    val finder = PathFinder(root) ** "extlinux" / "extlinux"
    finder.get.toList.sorted.headOption map(_.toPath) flatMap(parentOption) flatMap(parentOption)
  }

  protected def build(base: Path) {
    /*import scala.collection.JavaConversions._

    def commandEnvf(env: java.util.Map[String, String]) {
      env.put("DEBUG", "")
    }

    val CommandResult(result, arch, stderr) = Command.execute(Seq("uname", "-i"))

    if ((result == 0) && (arch != "i386")) {
      Command.execute(Seq("make"), workingDirectory = Some(base.toFile), envf = Some(commandEnvf _))
    }*/
  }

}
