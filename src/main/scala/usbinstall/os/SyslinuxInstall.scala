package usbinstall.os

import java.nio.file.{Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import scala.collection.mutable
import scala.language.postfixOps
import scala.util.matching.Regex
import suiryc.scala.io.{PathFinder, RegularFileFilter}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.misc.RichEither._
import suiryc.scala.sys.{Command, CommandResult}
import usbinstall.{InstallUI, Stages}
import usbinstall.settings.{InstallSettings, Settings}


class SyslinuxInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val syslinuxVersion = settings.syslinuxVersion.get
    val syslinuxRoot = SyslinuxInstall.get(syslinuxVersion).get
    val partition = settings.partition().get
    val device = partition.device
    val devicePath = device.dev
    val targetRoot = partMount.get.to.toAbsolutePath

    val others = Settings.core.oses filter(other => ((other ne settings) && other.enabled && other.syslinuxLabel.isDefined))
    val components = Settings.core.syslinuxExtraComponents

    Command.execute(Seq("parted", "-s", partition.device.dev.toString, "set", partition.partNumber.toString, "boot", "on"),
      skipResult = false)

    ui.action("Copy syslinux files") {
      val pathModules = targetRoot.resolve(Paths.get("syslinux", "modules"))
      val pathImages = targetRoot.resolve(Paths.get("syslinux", "images"))
      val pathBootdisk = targetRoot.resolve("bootdisk")

      List(pathModules, pathImages, pathBootdisk) foreach(_.toFile.mkdirs())

      val syslinuxCom32 = syslinuxRoot.resolve("com32")
      val syslinuxModules = List(
        syslinuxCom32.resolve(Paths.get("libutil", "libutil.c32")),
        syslinuxCom32.resolve(Paths.get("lib", "libcom32.c32")),
        syslinuxCom32.resolve(Paths.get("menu", "vesamenu.c32")),
        syslinuxCom32.resolve(Paths.get("chain", "chain.c32")),
        syslinuxRoot.resolve(Paths.get("memdisk", "memdisk"))
      )

      copy(syslinuxModules, syslinuxRoot, pathModules, None)
      copy(syslinuxRoot.resolve(Paths.get("sample", "syslinux_splash.jpg")), syslinuxRoot, pathImages, None)

      for {
        component <- components if (component.kind == SyslinuxComponentKind.Image)
        image <- component.image
      } {
        copy(image, image.getParent, pathBootdisk, Some(PosixFilePermissions.fromString("rw-rw-rw-")))
      }
    }

    ui.action("Apply MBR") {
      /* Original (disabled) code with altmbr */
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

    ui.action("Backup MBR") {
      Command.execute(Seq("dd", "bs=512", "count=1", "conv=notrunc", s"if=${devicePath}", s"of=${targetRoot.resolve("mbr.backup")}"),
        skipResult = false)
    }

    /* Note: Win7/8 only accesses the first partition (whatever its type) for
     * removable USB disks: so the install files must be on this partition,
     *  or a virtual drive has to be set, or partition table must be altered
     * (e.g. from a LiveCD) to have the wanted partition as first
     */
    for {
      other <- others if (other.kind == OSKind.Win7_8)
      _ <- other.syslinuxLabel
      otherPartition <- other.partition() if ((otherPartition.partNumber > 1) && (otherPartition.partNumber < 5))
    } {
      try {
        ui.action(s"Backup MBR for partition ${otherPartition.partNumber}") {
          for (otherPartNumber <- 1 until otherPartition.partNumber) {
            Command.execute(Seq("parted", "-s", devicePath.toString, "rm", otherPartNumber.toString),
              skipResult = false)
          }
          device.partprobe().toEither("Failed to refresh partition table").orThrow

          Command.execute(Seq("dd", "bs=512", "count=1", "conv=notrunc", s"if=${devicePath}", s"of=${targetRoot.resolve(s"mbr.part${otherPartition.partNumber}")}"),
            skipResult = false)
        }
      }
      finally {
        ui.action("Restore MBR") {
          Command.execute(Seq("dd", "bs=512", "count=1", "conv=notrunc", s"if=${targetRoot.resolve("mbr.backup")}", s"of=${devicePath}"),
            skipResult = false)

          device.partprobe().toEither("Failed to refresh partition table").orThrow
        }
      }
    }

    ui.action("Configure syslinux") {
      val syslinuxFile = getSyslinuxFile(targetRoot)
      val sb = new StringBuilder
      val defaultEntry = others find { other =>
        other.kind == OSKind.SystemRescueCD
      } orElse others.headOption

      sb.append(
"""PATH modules
UI vesamenu.c32
MENU TITLE Boot menu
MENU BACKGROUND images/syslinux_splash.jpg

PROMPT 0
TIMEOUT 100
""")

      defaultEntry flatMap(_.syslinuxLabel) foreach { label =>
        sb.append(
s"""ONTIMEOUT ${label}

MENU DEFAULT ${label}

""")
      }

      for {
        other <- others
        syslinuxLabel <- other.syslinuxLabel
        otherPartition <- other.partition()
      } {
        sb.append(
s"""
LABEL ${syslinuxLabel}
    MENU LABEL ^${other.label}
    KERNEL chain.c32
    APPEND boot ${otherPartition.partNumber}
""")

        /* Original code allowing to specify a 'kernel' and 'append' options */
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
      }

      components find(_.kind == SyslinuxComponentKind.Grub4DOS) foreach { component =>
        sb.append(
s"""

MENU SEPARATOR

LABEL ${component.syslinuxLabel}
    MENU LABEL ^${component.label}
    KERNEL chain.c32
    APPEND boot ${partition.partNumber} ntldr=/grldr
""")
      }

      sb.append(
s"""

MENU SEPARATOR

MENU BEGIN
MENU TITLE Misc tools
""")

      for {
        component <- components if (component.kind == SyslinuxComponentKind.Image)
        image <- component.image
      } {
        val imageName = image.getFileName()

      sb.append(
s"""
LABEL ${component.syslinuxLabel}
    MENU LABEL ^${component.label}
    KERNEL modules/memdisk
    INITRD /bootdisk/${imageName}
""")

        if (imageName.toString.toLowerCase().endsWith(".iso")) {
          sb.append(
"""    APPEND iso
""")
        }
        else if (imageName.toString.toLowerCase().endsWith(".img")) {
          sb.append(
"""    APPEND floppy
""")
        }
      }

      sb.append(
"""

MENU SEPARATOR

LABEL return
  MENU LABEL Return to main menu
  MENU EXIT


MENU END
""")

      syslinuxFile.toFile.write(sb.toString)
    }

    installGrub4DOS(partMount.get)

    installREFInd(partMount.get)
  }

  protected def installGrub4DOS(partMount: PartitionMount): Unit = {
    val grub4dosRoot = ui.action(s"Search Grub4DOS") {
      SyslinuxInstall.getGrub4DOS()
    }
    val targetRoot = partMount.to.toAbsolutePath

    ui.action("Copy Grub4DOS files") {
      copy(grub4dosRoot.resolve("grldr"), grub4dosRoot, targetRoot, None)
    }

    ui.action("Configure Grub4DOS") {
      val grub4dosFile = targetRoot.resolve("menu.lst")
      val sb = new StringBuilder

      sb.append(
"""title Reboot
    reboot
""")

      grub4dosFile.toFile.write(sb.toString)
    }
  }

  protected def installREFInd(partMount: PartitionMount): Unit = {
    val refindPath = Settings.core.rEFIndPath
    val pathISO = ui.action(s"Search rEFInd") {
      SyslinuxInstall.findPath(List(refindPath.resolve("iso")), """(?i)refind.*""".r).fold {
        throw new Exception("Could not find rEFInd ISO")
      } { path => path}
    }
    val iso = new PartitionMount(pathISO, InstallSettings.tempDirectory("rEFInd"))
    val targetRoot = partMount.to.toAbsolutePath

    iso.mount
    try {
      val source = iso.to
      val sourceRoot = source.toAbsolutePath

      copy(source ***, sourceRoot, targetRoot, "Copy rEFInd ISO content")
    }
    finally {
      iso.umount
    }

    copy(PathFinder(refindPath) / "drivers_x64" ***, refindPath, targetRoot.resolve(Paths.get("EFI", "boot")), "Copy rEFInd extra content")

    ui.action("Configure rEFInd") {
      val refindFile = targetRoot.resolve(Paths.get("EFI", "boot", "refind.conf"))
      val sb = new StringBuilder

      sb.append(refindFile.read)

      sb.append(
"""
# USB stick configuration

scanfor manual
""")

      searchEFI(Some(partMount))
      for {
        os <- Settings.core.oses if (os.enabled)
        _ <- os.partition()
        efiBootloader <- os.efiBootloader
      } {
        sb.append(
s"""
menuentry \"${os.label}\" {
    icon /EFI/boot/icons/${OSKind.efiIcon(os.kind)}
    volume \"${os.partitionLabel}\"
    loader /${efiBootloader}
}
""")
      }

      refindFile.toFile.write(sb.toString)
    }
  }

}

/* Original code allowing to use an OS as an image directly from syslinux (kind
 * of like misc images, but allowing to select the ISO at runtime)
 */
//    local component_idx=0
//    for ((component_idx=0; component_idx<${#install_component[@]}; component_idx++))
//    do
//        component=${install_component[${component_idx}]}
//        component_label=${install_component_syslinux_label[${component_idx}]}
//        component_iso=${install_component_iso[${component_idx}]}
//        component_partpath=${install_component_partition[${component_idx}]}
//
//        if [ "${component}" = "syslinux" ] || [ -z "${component_label}" ] \
//        || [ -z "${component_iso}" ] || [ -n "${component_partpath}" ]
//        then
//            continue
//        fi
//
//        component_iso_name=$(basename "${component_iso}")
//        cp "${component_iso}" "${dirPartMount}"/bootdisk/ \
//            && sync \
//            && chmod 444 "${dirPartMount}"/bootdisk/"${component_iso_name}"
//        checkReturnCode "Failed to copy ${component}" 2
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

object SyslinuxInstall {

  private val versions = mutable.Map[Int, Option[Path]]()

  def get(version: Int) =
    versions.getOrElseUpdate(version, find(version))

  protected def find(version: Int): Option[Path] = {
    findSyslinuxArchive(version).fold[Option[Path]] {
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

  protected def findPath(roots: List[Path], regex: Regex): Option[Path] = {
    val files = roots flatMap { path =>
      val finder = PathFinder(path) ** (regex & RegularFileFilter)

      finder.get map(_.toPath)
    }
    files.sorted.reverse.headOption
  }

  protected def findSyslinuxArchive(version: Int): Option[Path] =
    findPath(Settings.core.toolsPath, s"""(?i)syslinux.*-${version}.*""".r)

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

  protected def findGrub4DOSArchive(): Option[Path] =
    findPath(Settings.core.toolsPath, """(?i)grub4dos.*""".r)

  def getGrub4DOS(): Path = {
    findGrub4DOSArchive.fold[Path] {
      throw new Exception("Could not find Grub4DOS archive")
    } { path =>
      val root = uncompress(path)
      val finder = PathFinder(root) ** "grldr"
      finder.get.toList.sorted.headOption.fold {
        throw new Exception(s"Could not find Grub4DOS files in its archive[${path.getFileName()}]")
      } { path =>
        path.toPath.getParent
      }
    }
  }

}
