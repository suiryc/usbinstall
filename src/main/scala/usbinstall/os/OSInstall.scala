package usbinstall.os

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.PosixFilePermission
import scala.io.Codec
import suiryc.scala.io.{FilesEx, PathFinder, RegularFileFilter}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.RichFile._
import suiryc.scala.misc.RichEither._
import suiryc.scala.sys.Command
import suiryc.scala.sys.linux.DevicePartition
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI
import usbinstall.settings.InstallSettings


class OSInstall(
  val settings: OSSettings,
  val ui: InstallUI,
  val checkCancelled: () => Unit
) extends Logging
{

  /**
   * Lists the requirements for installing this OS.
   */
  def installRequirements(): Set[String] = Set.empty

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

  def requirements(): Set[String] = {
    val formatRequirements = if (settings.install && settings.formatable) {
      val kind = settings.partitionFormat
      (kind match {
        case PartitionFormat.ext2 =>
          Set(s"mkfs.${kind}", "e2label")

        case PartitionFormat.fat32 =>
          Set("mkfs.vfat", "mlabel")

        case PartitionFormat.ntfs =>
          Set(s"mkfs.${kind}", "ntfslabel")
      }) ++ Set("fdisk", "partprobe")
    } else Set.empty

    installRequirements() ++ formatRequirements
  }

  protected def getSyslinuxFile(targetRoot: Path) =
    Paths.get(targetRoot.toString(), "syslinux", settings.syslinuxFile)

  protected def copy(finder: PathFinder, sourceRoot: Path, targetRoot: Path, label: String) {
    ui.action(label) {
      finder.get.toList.sortBy(_.getPath) foreach { file =>
        val pathFile = file.toAbsolutePath
        val pathRelative = sourceRoot.relativize(pathFile)
        val pathTarget = targetRoot.resolve(pathRelative)
        checkCancelled()
        if (pathTarget.exists)
          logger.warn(s"Path[$pathRelative] already processed, skipping")
        else {
          ui.activity(s"Copying file[$pathRelative]")
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

  protected def copy(sources: List[Path], sourceRoot: Path, targetRoot: Path, mode: Option[java.util.Set[PosixFilePermission]]) {
    for (source <- sources) {
      copy(source, sourceRoot, targetRoot, mode)
    }
  }

  protected def duplicate(source: Path, sourceRoot: Path, target: Path, mode: Option[java.util.Set[PosixFilePermission]]) {
    checkCancelled()
    if (target.exists)
      logger.warn(s"Path[${sourceRoot.relativize(source)}] already processed, skipping")
    else {
      ui.activity(s"Copying file[${sourceRoot.relativize(source)}]")
      Files.copy(source, target,
        StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
    }
    mode foreach(target.toFile.changeMode(_))
  }

  protected def copy(source: Path, sourceRoot: Path, targetRoot: Path, mode: Option[java.util.Set[PosixFilePermission]]) {
    val target = targetRoot.resolve(source.getFileName())
    duplicate(source, sourceRoot, target, mode)
  }

  protected def renameSyslinux(targetRoot: Path) {
    checkCancelled()
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
    checkCancelled()
    val replaced = RegexReplacer.inplace(path, rrs:_*)

    if (replaced)
      ui.activity(s"Modified file[${root.relativize(path)}]")

    replaced
  }

  def regexReplace(root: Path, file: File, rrs: RegexReplacer*)(implicit codec: Codec): Boolean =
    regexReplace(root, file.toPath, rrs:_*)

  private def findEFI(mount: PartitionMount): Option[Path] = {
    val finder = PathFinder(mount.to) * """(?i:efi)""".r * """(?i:boot)""".r * ("""(?i:bootx64.efi)""".r & RegularFileFilter)
    finder.get.toList.sorted.headOption foreach { path =>
      settings.efiBootloader = Some(mount.to.relativize(path.toPath))
    }
    settings.efiBootloader
  }

  def searchEFI(partMount: Option[PartitionMount]) {
    if (!settings.efiBootloader.isDefined) partMount.foreach { partMount =>
      ui.action(s"Search EFI path") {
        checkCancelled()
        findEFI(partMount) match {
          case Some(path) =>
            ui.activity(s"Found EFI[$path]")

          case None =>
            ui.activity("No EFI found")
        }
      }
    }
  }

}

object OSInstall
  extends Logging
{

  def apply(settings: OSSettings, ui: InstallUI, checkCancelled: () => Unit): OSInstall =
    settings.kind match {
      case OSKind.Syslinux =>
        new SyslinuxInstall(settings, ui, checkCancelled)

      case OSKind.Win7_8 =>
        new Windows7_8Install(settings, ui, checkCancelled)

      case OSKind.GPartedLive =>
        new GPartedLiveInstall(settings, ui, checkCancelled)

      case OSKind.SystemRescueCD =>
        new SystemRescueCDInstall(settings, ui, checkCancelled)

      case OSKind.Ubuntu =>
        new UbuntuInstall(settings, ui, checkCancelled)

      case OSKind.Fedora =>
        new RedHatInstall(settings, ui, checkCancelled)

      case OSKind.CentOS =>
        new RedHatInstall(settings, ui, checkCancelled)

      case OSKind.ArchLinux =>
        new ArchLinuxInstall(settings, ui, checkCancelled)

      case OSKind.Kali =>
        new KaliInstall(settings, ui, checkCancelled)

      case kind =>
        val msg = s"Unhandled OS type: $kind"
        throw new Exception(msg)
    }

  private def mountAndDo(os: OSInstall, todo: (Option[PartitionMount], Option[PartitionMount]) => Unit): Unit = {
    val iso = os.settings.iso.get map { pathISO =>
      new PartitionMount(pathISO, InstallSettings.pathMountISO)
    }
    val part = os.settings.partition.get map { partition =>
      new PartitionMount(partition.dev, InstallSettings.pathMountPartition)
    }

    try {
      iso foreach { iso =>
        os.ui.activity(s"Mounting ISO[${iso.from.getFileName()}]")
        iso.mount
      }
      part foreach { part =>
        os.ui.activity(s"Mounting partition[${part.from}]")
        part.mount
      }
      todo(iso, part)
    }
    /* XXX - catch and log */
    finally {
      /* XXX - try/catch ? */
      part foreach { part =>
        os.ui.activity("Unmounting partition")
        part.umount
      }
      iso foreach { iso =>
        os.ui.activity("Unmounting ISO")
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
          Seq("mkfs.vfat", "-F", "32", part.dev.toString)

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
          (Seq("mlabel", "-i", part.dev.toString, s"::$actualLabel"), Some(commandEnvf _))

        case PartitionFormat.ntfs =>
          (Seq("ntfslabel", "--force", part.dev.toString, label), None)
      }

      os.ui.action(s"Set partition ${part.dev.toString} label ($label)") {
        Command.execute(command, envf = envf).toEither("Failed to label partition")
      }
    }

    format && setType && setLabel &&
      part.device.partprobe().toEither("Failed to refresh partition table")
  }

  private def installBootloader(os: OSInstall, mount: Option[PartitionMount]): Unit = {
    for {
      syslinuxVersion <- os.settings.syslinuxVersion
      part <- os.settings.partition.get
      mount <- mount
    } {
      /* Note: we already ensured this syslinux version was found */
      val syslinuxRoot = SyslinuxInstall.get(syslinuxVersion).get
      val CommandResult(result, stdout, stderr) =
        os.settings.partitionFormat match {
          case _: PartitionFormat.extX =>
            val syslinux = syslinuxRoot.resolve(Paths.get("extlinux", "extlinux"))
            val target = mount.to.resolve("syslinux")
            Command.execute(Seq(syslinux.toString, "--install", target.toString))

          case _: PartitionFormat.MS =>
            /* Note: it is safer (and mandatory for NTFS) to unmount partition first */
            mount.umount
            val syslinux = syslinuxRoot.resolve(Paths.get("linux", "syslinux"))
            val target = part.dev
            Command.execute(Seq(syslinux.toString, "--install", target.toString))
        }
        /* XXX - what to do with command result ? */
      }
    }
  }

  private def deleteContent(root: Path) {
    if (!root.toFile.delete(true, true)) {
      /* Some files may have the 'immutable' attribute */
      val finder = PathFinder(root).***

      finder.get.map(_.toPath) foreach { path =>
        Command.execute(Seq("chattr", "-i", path.toString))
      }

      if (!root.toFile.delete(true, true)) {
        val msg = "Some content could not be deleted"
        error(s"$msg: ${finder.get.mkString(", ")}")
        throw new Exception(msg)
      }
    }
  }

  def install(os: OSInstall): Unit = {
    os.ui.none()

    /* Prepare */
    if (os.settings.install) {
      os.ui.setStep(s"Prepare ${os.settings.label} installation")
      os.checkCancelled()
      os.prepare()
    }

    /* Actual install */
    if (os.settings.enabled) {
      os.ui.none()
      os.ui.setStep(s"Install ${os.settings.label}")
      os.checkCancelled()
    }

    if (os.settings.install) {
      /* prepare syslinux */
      os.settings.syslinuxVersion foreach { version =>
        os.ui.action(s"Search syslinux $version") {
          if (!SyslinuxInstall.get(version).isDefined)
            throw new Exception(s"Could not find syslinux ${version}")
        }
      }

      /* format partition */
      if (os.settings.formatable) {
        os.settings.partition.get foreach { part =>
          os.checkCancelled()
          preparePartition(os, part).orThrow
        }
      }
    }

    if (os.settings.enabled) {
      os.checkCancelled()
      mountAndDo(os, (isoMount, partMount) => {
        /* erase content */
        if (os.settings.erasable) {
          os.checkCancelled()
          os.ui.action("Erasing partition content") {
            deleteContent(partMount.get.to)
          }
        }

        if (os.settings.install) {
          os.checkCancelled()
          os.install(isoMount, partMount)
        }

        /* prepare EFI */
        os.searchEFI(partMount)

        if (os.settings.install) {
          os.ui.action(s"Install bootloader") {
            os.checkCancelled()
            installBootloader(os, partMount)
          }
        }
      })
    }

    os.ui.none()
  }

}
