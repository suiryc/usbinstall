package usbinstall.os

import com.typesafe.scalalogging.StrictLogging
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.util.regex.Pattern
import scala.collection.mutable
import scala.util.matching.Regex
import suiryc.scala.io.{PathFinder, RegularFileFilter}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.util.RichEither._
import usbinstall.{InstallUI, USBInstall}
import usbinstall.settings.{InstallSettings, ProfileSettings}


class SyslinuxInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
{

  override def requirements(): Set[String] = {
    super.requirements() ++ Set("parted", "dd")
  }

  override def install(isoMount: PartitionMount, partMount: PartitionMount): Unit = {
    val profile = InstallSettings.profile.get.get
    val syslinuxVersion = settings.syslinuxVersion.get
    val syslinux = SyslinuxInstall.get(profile, syslinuxVersion).get
    val targetRoot = partMount.to.toAbsolutePath
    val pathBootdisk = targetRoot.resolve("bootdisk")

    val syslinuxSettings = profile.syslinuxSettings
    val components = syslinuxSettings.extraComponents

    ui.action("Copy syslinux files") {
      val pathModules = targetRoot.resolve(Paths.get("syslinux", "modules"))
      val pathImages = targetRoot.resolve(Paths.get("syslinux", "images"))

      List(pathModules, pathImages, pathBootdisk).foreach(_.toFile.mkdirs())

      val syslinuxCom32 = syslinux.modules.resolve("com32")
      val syslinuxModules = List(
        syslinuxCom32.resolve(Paths.get("libutil", "libutil.c32")),
        syslinuxCom32.resolve(Paths.get("lib", "libcom32.c32")),
        syslinuxCom32.resolve(Paths.get("menu", "vesamenu.c32")),
        syslinuxCom32.resolve(Paths.get("chain", "chain.c32")),
        syslinux.modules.resolve(Paths.get("memdisk", "memdisk"))
      )

      copy(syslinuxModules, syslinux.modules, pathModules, None)
      copy(syslinux.root.resolve(Paths.get("sample", "syslinux_splash.jpg")), syslinux.root, pathImages, None)

      for {
        component <- components if component.kind == SyslinuxComponentKind.Image
        image <- component.image
      } {
        copy(image, image.getParent, pathBootdisk, Some(PosixFilePermissions.fromString("rw-rw-rw-")))
      }
    }
  }

  override def setup(partMount: PartitionMount): Unit = {
    val profile = InstallSettings.profile.get.get
    val syslinuxVersion = settings.syslinuxVersion.get
    val syslinux = SyslinuxInstall.get(profile, syslinuxVersion).get
    val partition = settings.partition.get.get
    val device = partition.device
    val devicePath = device.dev
    val targetRoot = partMount.to.toAbsolutePath
    val pathBootdisk = targetRoot.resolve("bootdisk")

    val others = profile.oses.filter(other => (other ne settings) && other.isSelected && other.syslinuxLabel.isDefined)
    val syslinuxSettings = profile.syslinuxSettings
    val components = syslinuxSettings.extraComponents

    Command.execute(Seq("parted", "-s", partition.device.dev.toString, "set", partition.partNumber.toString, "boot", "on"),
      skipResult = false)

    ui.action("Apply MBR") {
      // Original (disabled) code with altmbr
      // # altmbr does not seem to work as expected ...
      // #[ -e "${syslinuxLocation}"/mbr/altmbr.bin ] \
      // #    && dd bs=439 count=1 if="${syslinuxLocation}"/mbr/altmbr.bin of=${_devpath} \
      // #    && printf $(printf '\\x%02X' ${syslinuxPartnb}) | dd bs=1 count=1 of=${_devpath} seek=439

      val mbrBin = syslinux.modules.resolve(Paths.get("mbr", "mbr.bin"))
      if (mbrBin.exists) {
        val cmd = Seq("dd", "bs=440", "count=1", s"if=$mbrBin", s"of=$devicePath")
        val CommandResult(result, _, stderr) =
          Command.execute(cmd)
        if (result != 0) {
          Dialogs.error(
            owner = Some(USBInstall.stage),
            title = Some("MBR failure"),
            headerText = Some(s"Could not install syslinux MBR.\nYou may have to do it manually.\nEx: ${cmd.mkString(" ")}"),
            contentText = Some(stderr)
          )
        }
      }
    }

    ui.action("Backup MBR") {
      Command.execute(Seq("dd", "bs=512", "count=1", "conv=notrunc", s"if=$devicePath", s"of=${targetRoot.resolve("mbr.backup")}"),
        skipResult = false)
    }

    // Note: Win7/8 only accesses the first partition (whatever its type) for
    // removable USB disks: so the install files must be on this partition,
    //  or a virtual drive has to be set, or partition table must be altered
    // (e.g. from a LiveCD) to have the wanted partition as first
    for {
      other <- others if other.kind == OSKind.Windows
      _ <- other.syslinuxLabel
      otherPartition <- other.partition.get if (otherPartition.partNumber > 1) && (otherPartition.partNumber < 5)
    } {
      try {
        ui.action(s"Backup MBR for partition ${otherPartition.partNumber}") {
          for (otherPartNumber <- 1 until otherPartition.partNumber) {
            Command.execute(Seq("parted", "-s", devicePath.toString, "rm", otherPartNumber.toString),
              skipResult = false)
          }
          device.partprobe().toEither("Failed to refresh partition table").orThrow

          Command.execute(Seq("dd", "bs=512", "count=1", "conv=notrunc", s"if=$devicePath", s"of=${targetRoot.resolve(s"mbr.part${otherPartition.partNumber}")}"),
            skipResult = false)
        }
      }
      finally {
        ui.action("Restore MBR") {
          Command.execute(Seq("dd", "bs=512", "count=1", "conv=notrunc", s"if=${targetRoot.resolve("mbr.backup")}", s"of=$devicePath"),
            skipResult = false)

          device.partprobe().toEither("Failed to refresh partition table").orThrow
        }
        ()
      }
    }

    ui.action("Configure syslinux") {
      val syslinuxFile = getSyslinuxFile(targetRoot)
      val sb = new StringBuilder
      val defaultEntry = syslinuxSettings.menuEntriesDefault.orElse {
        others.find { other =>
          other.kind == OSKind.SystemRescueCD
        }.orElse(others.headOption).flatMap(_.syslinuxLabel)
      }

      sb.append(
"""PATH modules
UI vesamenu.c32
MENU TITLE Boot menu
MENU BACKGROUND images/syslinux_splash.jpg

PROMPT 0
TIMEOUT 100
""")

      defaultEntry.foreach { label =>
        sb.append(
s"""ONTIMEOUT $label

MENU DEFAULT $label

""")
      }

      syslinuxSettings.menuEntriesHeader.foreach(sb.append)

      for {
        other <- others
        syslinuxLabel <- other.syslinuxLabel
        otherPartition <- other.partition.get
      } {
        sb.append(
s"""
LABEL $syslinuxLabel
    MENU LABEL ^${other.label}
    KERNEL chain.c32
    APPEND boot ${otherPartition.partNumber}
""")

        // Original code allowing to specify a 'kernel' and 'append' options
        //
        //     if [ -n "${component_kernel}" ]
        //     then
        //         echo "    KERNEL ${component_kernel}" >> "${confFile}"
        //         if [ -n "${component_append}" ]
        //         then
        //             echo "    APPEND${component_append}" >> "${confFile}"
        //         fi
        //     else
        //         partnb=${component_partpath:${#_partpath_prefix}}
        //         echo "    KERNEL chain.c32
        // APPEND boot ${partnb}${component_append}" >> "${confFile}"
        //     fi
      }

      components.find(_.kind == SyslinuxComponentKind.Grub4DOS).foreach { component =>
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
        component <- components if component.kind == SyslinuxComponentKind.Image
        image <- component.image
      } {
        val imageName = image.getFileName

        sb.append(
          s"""
             |LABEL ${component.syslinuxLabel}
             |    MENU LABEL ^${component.label}
             |""".stripMargin)

        val extension = imageName.toString.split('.').last.toLowerCase
        val memdiskKind = extension match {
          case "iso" => Some("iso")
          case "img" => Some("floppy")
          case _     => None
        }
        memdiskKind match {
          case Some(kind) =>
            sb.append(
              s"""    KERNEL modules/memdisk
                 |    INITRD /bootdisk/$imageName
                 |    APPEND $kind
                 |""".stripMargin)

          case None =>
            val actualName = if (extension == "bin") {
              // Files with "bin" extension are considered as boot sectors and
              // not executable code, so rename them.
              val withoutExtension = imageName.toString.split('.').reverse.tail.reverse.mkString(".")
              val pathSrc = pathBootdisk.resolve(imageName)
              val pathDst = pathBootdisk.resolve(withoutExtension)
              ui.activity(s"Rename source[${targetRoot.relativize(pathSrc)}] target[${targetRoot.relativize(pathDst)}]")
              Files.move(pathSrc, pathDst)
              withoutExtension
            } else {
              imageName
            }
            sb.append(
              s"""    KERNEL /bootdisk/$actualName
                 |""".stripMargin)
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

      syslinuxFile.toFile.write(sb.toString())
    }

    installGrub4DOS(profile, partMount)

    installREFInd(profile, partMount)
  }

  protected def installGrub4DOS(profile: ProfileSettings, partMount: PartitionMount): Unit = {
    val grub4dosRoot = ui.action(s"Search Grub4DOS") {
      SyslinuxInstall.getGrub4DOS(profile)
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

      grub4dosFile.toFile.write(sb.toString())
    }
  }

  protected def installREFInd(profile: ProfileSettings, partMount: PartitionMount): Unit = {
    val refindPath = profile.rEFIndPath
    val refindDrivers = profile.rEFIndDrivers
    val pathISO = ui.action(s"Search rEFInd") {
      SyslinuxInstall.findPath(List(refindPath.resolve("iso")), """(?i)refind.*""".r).fold {
        throw new Exception("Could not find rEFInd ISO")
      } { path => path}
    }
    val iso = new PartitionMount(pathISO, InstallSettings.tempDirectory("rEFInd"))
    val targetRoot = partMount.to.toAbsolutePath

    iso.mount()
    try {
      val source = iso.to
      val refind = source.resolve(Paths.get("refind"))
      val sourceRoot = source.toAbsolutePath
      val refindRoot = refind.toAbsolutePath

      // refind/refind_x64.efi -> EFI/boot/bootx64.efi
      duplicate(
        refind.resolve(Paths.get("refind_x64.efi")),
        sourceRoot,
        targetRoot.resolve(Paths.get("EFI", "boot", "bootx64.efi")),
        None
      )
      // refind/drivers_x64/** -> EFI/boot/drivers_x64/**
      refind.resolve(Paths.get("drivers_x64")).asFile.listFiles.toList.filter { f =>
        refindDrivers.exists(_.findFirstMatchIn(f.getName).isDefined)
      }.foreach { f =>
        duplicate(
          f,
          sourceRoot,
          targetRoot.resolve(Paths.get("EFI", "boot", "drivers_x64", f.getName)),
          None
        )
      }
      // refind/icons -> EFI/boot/icons
      copy(
        refind.resolve(Paths.get("icons")).***,
        refindRoot,
        targetRoot.resolve(Paths.get("EFI", "boot")),
        settings.partitionFilesystem,
        "Copy rEFInd icons"
      )
      // shellx64.efi -> EFI/tools/shell.efi
      duplicate(
        source.resolve(Paths.get("shellx64.efi")),
        sourceRoot,
        targetRoot.resolve(Paths.get("EFI", "tools", "shell.efi")),
        None
      )
      // refind/refind.conf-sample -> EFI/boot/
      copy(
        refind.resolve(Paths.get("refind.conf-sample")),
        refindRoot,
        targetRoot.resolve(Paths.get("EFI", "boot")),
        None
      )
    }
    finally {
      iso.umount()
    }

    copy(
      PathFinder(refindPath) / "drivers_x64".***,
      refindPath,
      targetRoot.resolve(Paths.get("EFI", "boot")),
      settings.partitionFilesystem,
      "Copy rEFInd extra content"
    )

    ui.action("Configure rEFInd") {
      val refindFile = targetRoot.resolve(Paths.get("EFI", "boot", "refind.conf"))
      val sb = new StringBuilder

      // Note: rEFInd conf starting with empty line does not work.
      sb.append(
"""timeout 20
scanfor manual
""")

      searchEFI(partMount)
      for {
        os <- profile.oses if os.isSelected
        _ <- os.partition.get
        efiBootloader <- os.efiBootloader
      } {
        sb.append(
s"""
menuentry \"${os.label}\" {
    icon /EFI/boot/icons/${OSKind.efiIcon(os.kind)}
    volume \"${os.partitionLabel}\"
    loader /$efiBootloader
}
""")
      }

      refindFile.toFile.write(sb.toString())
    }
  }

}

// Original code allowing to use an OS as an image directly from syslinux (kind
// of like misc images, but allowing to select the ISO at runtime)
//
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

object SyslinuxInstall
  extends StrictLogging
{

  case class Syslinux(root: Path, modules: Path)
  protected case class SyslinuxArchive(archive: Path, uncompressed: Syslinux)

  private val versions = mutable.Map[String, Option[SyslinuxArchive]]()

  def get(profile: ProfileSettings, version: String): Option[Syslinux] =
    versions.getOrElseUpdate(version, find(profile, version/*, doBuild = true*/)).map(_.uncompressed)

  def getSource(profile: ProfileSettings, version: String): Option[Path] =
    versions.getOrElse(version, find(profile, version/*, doBuild = false*/)).map(_.archive)

  protected def find(profile: ProfileSettings, version: String/*, doBuild: Boolean*/): Option[SyslinuxArchive] = {
    findSyslinuxArchive(profile, version).fold[Option[SyslinuxArchive]] {
      logger.error(s"No archive found for syslinux version[$version]")
      None
    } { archive =>
      // First check whether the matching archive is already associated to
      // another (more precise/generic) version number.
      versions.collectFirst {
        case (_, Some(SyslinuxArchive(a, u))) if archive.compareTo(a) == 0 =>
          SyslinuxArchive(a, u)
      }.orElse(findBase(uncompress(archive)).fold[Option[SyslinuxArchive]] {
        logger.error(s"Could not find syslinux version[$version] files in archive[$archive]")
        None
      } { syslinux =>
        //if (doBuild) build(syslinux)
        Some(SyslinuxArchive(archive, syslinux))
      })
    }
  }

  protected def findPath(roots: List[Path], regex: Regex): Option[Path] = {
    val files = roots.flatMap { path =>
      val finder = PathFinder(path) ** (regex & RegularFileFilter)

      finder.get.map(_.toPath)
    }
    files.sorted.reverse.headOption
  }

  protected def findSyslinuxArchive(profile: ProfileSettings, version: String): Option[Path] =
    findPath(profile.toolsPath, s"""(?i)syslinux.*-${Pattern.quote(version)}.*""".r)

  protected def uncompress(path: Path): Path = {
    val isZip = path.getFileName.toString.endsWith(".zip")

    val uncompressPath = InstallSettings.pathTemp.resolve(path.getFileName)
    uncompressPath.toFile.mkdirs
    val CommandResult(result, _, stderr) =
      if (isZip) Command.execute(Seq("unzip", "-qq", path.toString, "-d", uncompressPath.toString))
      else Command.execute(Seq("tar", "xf", path.toString, "-C", uncompressPath.toString))

      if (result != 0) {
        logger.error(s"Failed to uncompress archive[$path] to[$uncompressPath]: $stderr")
      }

    uncompressPath
  }

  protected def findBase(root: Path): Option[Syslinux] = {
    def parentOption(path: Path) = Option(path.getParent)

    // In syslinux 4 & 5, we have the following hierarchy:
    //   extlinux / extlinux
    //   com32 / * / *.c32
    //   sample / syslinux_splash.jpg
    // Starting with syslinux 6, extlinux & com32 files are now located inside
    // "bios" folder (EFI files being under dedicated folders).
    val finderRoot = (PathFinder(root) ** "sample" / "syslinux_splash.jpg").?
    val finderModules = (PathFinder(root) ** "extlinux" / "extlinux").?
    for {
      root <- finderRoot.get().toList.sorted.headOption.map(_.toPath).flatMap(parentOption).flatMap(parentOption)
      modules <- finderModules.get().toList.sorted.headOption.map(_.toPath).flatMap(parentOption).flatMap(parentOption)
    } yield {
      Syslinux(root, modules)
    }
  }

//  protected def build(syslinux: Syslinux) {
//    import scala.collection.JavaConversions._
//
//    def commandEnvf(env: java.util.Map[String, String]) {
//      env.put("DEBUG", "")
//    }
//
//    val CommandResult(result, arch, stderr) = Command.execute(Seq("uname", "-i"))
//
//    if ((result == 0) && (arch != "i386")) {
//      Command.execute(Seq("make"), workingDirectory = Some(syslinux.root.toFile), envf = Some(commandEnvf _))
//    }
//  }

  protected def findGrub4DOSArchive(profile: ProfileSettings): Option[Path] =
    findPath(profile.toolsPath, """(?i)grub4dos.*""".r)

  def getGrub4DOS(profile: ProfileSettings): Path = {
    findGrub4DOSArchive(profile).fold[Path] {
      throw new Exception("Could not find Grub4DOS archive")
    } { path =>
      val root = uncompress(path)
      val finder = PathFinder(root) ** "grldr"
      finder.get().toList.sorted.headOption.fold {
        throw new Exception(s"Could not find Grub4DOS files in its archive[${path.getFileName}]")
      } { path =>
        path.toPath.getParent
      }
    }
  }

}
