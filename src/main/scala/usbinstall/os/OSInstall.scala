package usbinstall.os

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.io.Codec
import suiryc.scala.io.{FilesEx, PathFinder, RegularFileFilter}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.RichFile._
import suiryc.scala.sys.Command
import suiryc.scala.sys.linux.DevicePartition
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI
import usbinstall.settings.InstallSettings


class OSInstall(val settings: OSSettings, val ui: InstallUI)
  extends Logging
{

  /**
   * Prepares OS installation.
   *
   * XXX - reword ?
   * Does anything necessary before partition is formatted (if requested) and
   * actual OS installation.
   * E.g.: find/compile tools files necessary for OS installation.
   * Syslinux prepared separately.
   */
  def prepare(): Unit = { }

  /**
   * Installs OS.
   *
   * Partition is already formatted.
   * EFI setup and syslinux bootloader install are performed right after.
   */
  def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = { }

  protected def getSyslinuxFile(targetRoot: Path) =
    Paths.get(targetRoot.toString(), "syslinux", settings.syslinuxFile)

  protected def copy(finder: PathFinder, sourceRoot: Path, targetRoot: Path, label: String) {
    ui.action(label) {
      finder.get.toList.sortBy(_.getPath) foreach { file =>
        val pathFile = file.toAbsolutePath
        val pathRelative = sourceRoot.relativize(pathFile)
        val pathTarget = targetRoot.resolve(pathRelative)
        if (pathTarget.exists)
          logger.warn(s"Path[$pathRelative] already processed, skipping")
        else {
          ui.activity(s"Copying file[$pathRelative]")
          /* XXX - can a 'copy' fail ? */
          FilesEx.copy(
            sourceRoot,
            pathRelative,
            targetRoot,
            followLinks = false
          )
        }
      }
    }
  }

  protected def renameSyslinux(targetRoot: Path) {
    val syslinuxFile = getSyslinuxFile(targetRoot)
    if (!syslinuxFile.exists) {
      val syslinuxCfg = Paths.get(targetRoot.toString(), "syslinux", "syslinux.cfg")
      val isolinuxCfg = Paths.get(targetRoot.toString(), "isolinux", "isolinux.cfg")
      if (syslinuxCfg.exists) ui.action("Rename syslinux configuration file") {
        ui.activity(s"Rename source[${targetRoot.relativize(syslinuxCfg)}] target[${targetRoot.relativize(syslinuxFile)}]")
        Files.move(syslinuxCfg, syslinuxFile)
      }
      else if (isolinuxCfg.exists) ui.action("Rename isolinux folder to syslinux") {
        syslinuxFile.getParent().delete(true)
        ui.activity(s"Rename source[${targetRoot.relativize(isolinuxCfg).getParent()}] target[${targetRoot.relativize(syslinuxFile).getParent()}]")
        Files.move(isolinuxCfg, isolinuxCfg.getParent().resolve(syslinuxFile.getFileName()))
        Files.move(isolinuxCfg.getParent(), syslinuxFile.getParent())
      }
    }
  }

  def regexReplace(root: Path, path: Path, rrs: RegexReplacer*)(implicit codec: Codec): Boolean = {
    val replaced = RegexReplacer.inplace(path, rrs:_*)

    if (replaced)
      ui.activity(s"Modified file[${root.relativize(path)}]")

    replaced
  }

  def regexReplace(root: Path, file: File, rrs: RegexReplacer*)(implicit codec: Codec): Boolean =
    regexReplace(root, file.toPath, rrs:_*)

}

object OSInstall {

  def apply(settings: OSSettings, ui: InstallUI): OSInstall = settings.kind match {
    case OSKind.Win7_8 =>
      new Windows7_8Install(settings, ui)

    case OSKind.GPartedLive =>
      new GPartedLiveInstall(settings, ui)

    case OSKind.SystemRescueCD =>
      new SystemRescueCDInstall(settings, ui)

    case OSKind.Ubuntu =>
      new UbuntuInstall(settings, ui)

    case OSKind.RedHat =>
      new RedHatInstall(settings, ui)

    /* XXX */
    case kind =>
      val msg = s"Unhandled OS type: $kind"
      throw new Exception(msg)
  }

  private def mountAndDo(os: OSInstall, todo: (Option[PartitionMount], Option[PartitionMount]) => Unit): Unit = {
    val iso = os.settings.iso() map { pathISO =>
      new PartitionMount(pathISO, InstallSettings.pathMountISO)
    }
    val part = os.settings.partition() map { partition =>
      new PartitionMount(partition.dev, InstallSettings.pathMountPartition)
    }

    try {
      iso foreach { iso =>
        os.ui.activity(s"Mounting ISO from[${iso.from}] to[${iso.to}]")
        iso.mount
      }
      part foreach { part =>
        os.ui.activity(s"Mounting partition from[${part.from}] to[${part.to}]")
        part.mount
      }
      todo(iso, part)
    }
    /* XXX - catch and log */
    finally {
      /* XXX - sync ? */
      /* XXX - try/catch ? */
      part foreach { part =>
        os.ui.activity(s"Unmounting partition[${part.to}]")
        part.umount
      }
      iso foreach { iso =>
        os.ui.activity(s"Unmounting ISO[${iso.to}]")
        iso.umount
      }
    }
  }

  private def preparePartition(os: OSInstall, part: DevicePartition) = {
    import suiryc.scala.misc.RichEither._

    val kind = os.settings.partitionFormat
    val label = os.settings.partitionLabel

    def format = {
      val command = kind match {
        case PartitionFormat.ext2 =>
          Seq(s"mkfs.${kind}", part.dev.toString)

        case PartitionFormat.fat32 =>
          Seq(s"mkfs.vfat", "-F", "32", part.dev.toString)

        case PartitionFormat.ntfs =>
          Seq(s"mkfs.${kind}", "--fast", part.dev.toString)
      }

      os.ui.action(s"Format partition ${part.dev.toString} ($kind)") {
        Command.execute(command).toEither("Failed to format partition")
      }
    }

    def setType = {
      val id = kind match {
        case PartitionFormat.ext2 => "83"
        case PartitionFormat.fat32 => "b"
        case PartitionFormat.ntfs => "7"
      }

      val input = s"""t
${part.partNumber}
$id
w
""".getBytes

      os.ui.action(s"Set partition ${part.dev.toString} type ($kind)") {
        Command.execute(Seq("fdisk", part.device.dev.toString), stdinSource = Command.input(input)).toEither("Failed to set partition type") ||
          Command.execute(Seq("partprobe", "-d", part.device.dev.toString)).toEither("Failed to set partition type")
      }
    }

    def setLabel = {
      val (command, envf) = kind match {
        case PartitionFormat.ext2 =>
          /* Max ext2 label length: 16 */
          (Seq("e2label", part.dev.toString, label), None)

        case PartitionFormat.fat32 =>
          def commandEnvf(env: java.util.Map[String, String]) {
            env.put("MTOOLS_SKIP_CHECK", "1")
          }

          val actualLabel = label.take(11).padTo(11, ' ')
          /* Max FAT32 label length: 11
           * To work correctly, it is better to truncate/pad it.
           */
          (Seq(s"mlabel", "-i", part.dev.toString, s"::$actualLabel"), Some(commandEnvf _))

        case PartitionFormat.ntfs =>
          (Seq("ntfslabel", "--force", part.dev.toString, label), None)
      }

      os.ui.action(s"Set partition ${part.dev.toString} label ($label)") {
        Command.execute(command, envf = envf).toEither("Failed to label partition")
      }
    }

    /* XXX - if !format but install, clean partition */
    val r = (if (os.settings.formatable) format else Right("Partition formatting disabled")) &&
      setType && setLabel

    /* slow devices need some time before being usable */
    Thread.sleep(1000)

    r
  }

  private def findEFI(os: OSInstall, mount: Option[PartitionMount]): Option[Path] = {
    mount flatMap { mount =>
      val finder = PathFinder(mount.to) * """(?i:efi)""".r * """(?i:boot)""".r * ("""(?i:bootx64.efi)""".r & RegularFileFilter)
      finder.get.toList.sorted.headOption foreach { path =>
        os.settings.efiBootloader = Some(mount.to.relativize(path.toPath))
      }
      os.settings.efiBootloader
    }
  }

  private def installBootloader(os: OSInstall, mount: Option[PartitionMount]): Unit = {
    for {
      syslinuxVersion <- os.settings.syslinuxVersion
      part <- os.settings.partition()
      mount <- mount
    } {
      SyslinuxInstall.get(syslinuxVersion).fold {
        /* XXX - log error */
      } { syslinuxRoot =>
        os.settings.partitionFormat match {
          case _: PartitionFormat.extX =>
            val syslinux = syslinuxRoot.toPath.resolve(Paths.get("extlinux", "extlinux"))
            val target = mount.to.resolve("syslinux")
            Command.execute(Seq(syslinux.toString, "--install", target.toString))

          case _: PartitionFormat.MS =>
            /* Note: it is safer (and mandatory for NTFS) to unmount partition first */
            mount.umount
            val syslinux = syslinuxRoot.toPath.resolve(Paths.get("linux", "syslinux"))
            val target = part.dev
            Command.execute(Seq(syslinux.toString, "--install", target.toString))
        }
        /* XXX - what to do with command result ? */
      }
    }
  }

  /* XXX - caller must check enabled && installable ? */
  def install(os: OSInstall, checkCancelled: () => Unit): Unit = {
    os.ui.none()

    /* Prepare */
    if (os.settings.install) {
      os.ui.setStep(s"Prepare ${os.settings.label} installation")
      checkCancelled()
      os.prepare()
    }

    /* Actual install */
    os.ui.none()
    os.ui.setStep(s"Install ${os.settings.label}")
    checkCancelled()

    if (os.settings.install) {
      /* prepare syslinux */
      os.settings.syslinuxVersion foreach { version =>
        os.ui.action(s"Search syslinux $version") {
          SyslinuxInstall.get(version)
          /* XXX - stop right now if problem ? */
          /* XXX - setting to only set syslinux ? */
        }
      }

      /* prepare partition */
      os.settings.partition() foreach { part =>
        checkCancelled()
        preparePartition(os, part)
      }
    }

    /* XXX - only if something to do ? */
    if (os.settings.enabled) {
      checkCancelled()
      mountAndDo(os, (isoMount, partMount) => {
        if (os.settings.install) {
          checkCancelled()
          os.install(isoMount, partMount)
        }

        /* prepare EFI */
        os.ui.action(s"Search EFI path") {
          checkCancelled()
          findEFI(os, partMount) match {
            case Some(path) =>
              os.ui.activity(s"Found EFI[$path]")

            case None =>
              os.ui.activity("No EFI found")
          }
        }

        if (os.settings.install) {
          os.ui.action(s"Install bootloader") {
            checkCancelled()
            installBootloader(os, partMount)
          }
        }
      })
    }

    os.ui.none()
  }

}
