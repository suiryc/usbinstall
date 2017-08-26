package usbinstall.os

import com.typesafe.scalalogging.StrictLogging
import java.io.File
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.PosixFilePermission
import scala.io.Codec
import scala.util.matching.Regex
import suiryc.scala.io.{FilesEx, PathFinder, RegularFileFilter}
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.RichFile._
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.sys.linux.DevicePartition
import suiryc.scala.util.RichEither._
import suiryc.scala.util.matching.RegexReplacer
import usbinstall.InstallUI
import usbinstall.settings.{InstallSettings, ProfileSettings}


class OSInstall(
  val settings: OSSettings,
  val ui: InstallUI,
  val checkCancelled: () => Unit
) extends StrictLogging
{

  /**
   * Lists the requirements for installing this OS.
   */
  def installRequirements(): Set[String] = Set.empty

  /**
   * Prepares OS installation.
   *
   * Does anything necessary before partition is formatted (if requested) and
   * actual OS installation.
   * E.g.: find/compile tools files necessary for OS installation.
   * Syslinux prepared separately.
   */
  def prepare() { }

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
          Set(s"mkfs.$kind", "e2label")

        case PartitionFormat.fat32 =>
          Set("mkfs.vfat", "mlabel")

        case PartitionFormat.ntfs =>
          Set(s"mkfs.$kind", "ntfslabel")
      }) ++ Set("fdisk", "partprobe")
    } else Set.empty

    installRequirements() ++ formatRequirements
  }

  protected def getSyslinuxFile(targetRoot: Path): Path =
    Paths.get(targetRoot.toString, "syslinux", settings.syslinuxFile)

  /**
   * Copies source hierarchy to target.
   *
   * @param finder source hierarchy finder
   * @param sourceRoot source root, to determine relative paths to copy
   * @param targetRoot target root
   * @param targetType target filesystem kind
   * @param label label to log as UI action
   */
  protected def copy(finder: PathFinder, sourceRoot: Path, targetRoot: Path, targetType: PartitionFormat.Value, label: String) {
    ui.action(label) {
      finder.get().toList.sortBy(_.getPath).foreach { file =>
        val pathFile = file.toAbsolutePath
        val pathRelative = sourceRoot.relativize(pathFile)
        val pathTarget = targetRoot.resolve(pathRelative)
        checkCancelled()
        if (pathTarget.exists)
          logger.warn(s"Path[$pathRelative] already processed, skipping")
        else {
          val copyOptions =
           if (targetType == PartitionFormat.fat32)
             List(StandardCopyOption.COPY_ATTRIBUTES)
           else
             List(StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
          ui.activity(s"Copying file[$pathRelative]")
          FilesEx.copy(
            sourceRoot,
            pathRelative,
            targetRoot,
            followLinks = false,
            options = copyOptions
          )
        }
      }
    }
  }

  /**
   * Copies files to target folder.
   *
   * Source folder hierarchy is not preserved. Files are copied directly
   * inside the target folder.
   *
   * @param sources files to copy
   * @param sourceRoot source root (informational)
   * @param targetRoot target root
   * @param mode copied files mode
   */
  protected def copy(sources: List[Path], sourceRoot: Path, targetRoot: Path, mode: Option[java.util.Set[PosixFilePermission]]) {
    for (source <- sources) {
      copy(source, sourceRoot, targetRoot, mode)
    }
  }

  /**
   * Copies file.
   *
   * @param source file to copy
   * @param sourceRoot source root (informational)
   * @param target target file
   * @param mode copied file mode
   */
  protected def duplicate(source: Path, sourceRoot: Path, target: Path, mode: Option[java.util.Set[PosixFilePermission]]) {
    checkCancelled()
    if (target.exists) {
      logger.warn(s"Path[${sourceRoot.relativize(source)}] already processed, skipping")
    } else {
      ui.activity(s"Copying file[${sourceRoot.relativize(source)}]")
      // First create target hierarchy if needed
      target.getParent.mkdirs
      Files.copy(source, target,
        StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
    }
    mode.foreach(target.toFile.changeMode)
  }

  /**
   * Copies file to target folder.
   *
   * Source folder hierarchy is not preserved. File is copied directly
   * inside the target folder.
   *
   * @param source file to copy
   * @param sourceRoot source root (informational)
   * @param targetRoot target root
   * @param mode copied file mode
   */
  protected def copy(source: Path, sourceRoot: Path, targetRoot: Path, mode: Option[java.util.Set[PosixFilePermission]]) {
    val target = targetRoot.resolve(source.getFileName)
    duplicate(source, sourceRoot, target, mode)
  }

  protected def renameSyslinux(targetRoot: Path) {
    checkCancelled()
    val syslinuxFile = getSyslinuxFile(targetRoot)
    if (!syslinuxFile.exists) {
      val syslinuxCfg = Paths.get(targetRoot.toString, "syslinux", "syslinux.cfg")
      val isolinuxCfg = Paths.get(targetRoot.toString, "isolinux", "isolinux.cfg")
      if (syslinuxCfg.exists) ui.action("Rename syslinux configuration file") {
        ui.activity(s"Rename source[${targetRoot.relativize(syslinuxCfg)}] target[${targetRoot.relativize(syslinuxFile)}]")
        Files.move(syslinuxCfg, syslinuxFile)
      }
      else if (isolinuxCfg.exists) ui.action("Rename isolinux folder to syslinux") {
        syslinuxFile.getParent.delete(recursive = true)
        ui.activity(s"Rename source[${targetRoot.relativize(isolinuxCfg).getParent}] target[${targetRoot.relativize(syslinuxFile).getParent}]")
        Files.move(isolinuxCfg, isolinuxCfg.getParent.resolve(syslinuxFile.getFileName))
        Files.move(isolinuxCfg.getParent, syslinuxFile.getParent)
      }
    }
    ()
  }

  protected def fixGrubSearch(targetRoot: Path): Unit = {
    val uuid = settings.partition.get.get.uuid.fold(throw _, v => v)

    val pfroot = (PathFinder(targetRoot) * "(?i)boot".r) ++ (PathFinder(targetRoot) * "(?i)efi".r)
    val confs = pfroot ** ("(?i).*\\.cfg".r | "(?i).*\\.conf".r)
    val regexReplacers = List(
      RegexReplacer(
        new Regex("""(?i)([ \t]+(?:search)[ \t]+[^\r\n]*)[ \t]+-f[ \t]+[^\s]+""", "pre"),
        (m: Regex.Match) => s"${m.group("pre")} --fs-uuid $uuid"
      )
    )
    for (conf <- confs.get()) {
      regexReplace(targetRoot, conf, regexReplacers:_*)
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
    def find(loader0: String): Option[Path] = {
      val loader = loader0.toLowerCase
      if (loader.endsWith(".efi")) {
        val finder0 = loader.split('/').toList.filterNot(_.isEmpty).map { p =>
          new Regex(s"(?i)$p")
        }.foldLeft(PathFinder(mount.to)) { (acc, r) =>
          acc * r
        }
        val finder = finder0 ? RegularFileFilter
        finder.get().toList.sorted.headOption.map(f => mount.to.relativize(f.toPath))
      } else {
        find(s"efi/boot/${loader}x64.efi").orElse {
          find(s"efi/boot/$loader.efi")
        }
      }
    }

    val search = settings.efiLoader.toList ::: List("grub", "boot")
    search.foreach { s =>
      if (settings.efiBootloader.isEmpty) {
        settings.efiBootloader = find(s)
      }
    }
    settings.efiBootloader
  }

  def searchEFI(partMount: Option[PartitionMount]) {
    if (settings.efiBootloader.isEmpty) partMount.foreach { partMount =>
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
  extends StrictLogging
{

  def apply(settings: OSSettings, ui: InstallUI, checkCancelled: () => Unit): OSInstall =
    settings.kind match {
      case OSKind.Syslinux =>
        new SyslinuxInstall(settings, ui, checkCancelled)

      case OSKind.Windows =>
        new WindowsInstall(settings, ui, checkCancelled)

      case OSKind.Generic =>
        new GenericInstall(settings, ui, checkCancelled)

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

      case kind =>
        val msg = s"Unhandled OS type: $kind"
        throw new Exception(msg)
    }

  private def mountAndDo(os: OSInstall, todo: (Option[PartitionMount], Option[PartitionMount]) => Unit): Unit = {
    val iso = os.settings.iso.get.map { pathISO =>
      new PartitionMount(pathISO, InstallSettings.pathMountISO)
    }
    val part = os.settings.partition.get.map { partition =>
      new PartitionMount(partition.dev, InstallSettings.pathMountPartition)
    }

    try {
      iso.foreach { iso =>
        os.ui.activity(s"Mounting ISO[${iso.from.getFileName}]")
        iso.mount()
      }
      part.foreach { part =>
        os.ui.activity(s"Mounting partition[${part.from}]")
        part.mount()
      }
      todo(iso, part)
    }
    finally {
      part.foreach { part =>
        os.ui.activity("Unmounting partition")
        part.umount()
      }
      iso.foreach { iso =>
        os.ui.activity("Unmounting ISO")
        iso.umount()
      }
    }
  }

  private def preparePartition(os: OSInstall, part: DevicePartition) = {
    val kind = os.settings.partitionFormat
    val label = os.settings.partitionLabel

    def format = {
      if (os.settings.formatable) {
        val command = kind match {
          case PartitionFormat.ext2 =>
            Seq(s"mkfs.$kind", part.dev.toString)

          case PartitionFormat.fat32 =>
            Seq("mkfs.vfat", "-F", "32", part.dev.toString)

          case PartitionFormat.ntfs =>
            Seq(s"mkfs.$kind", "--fast", part.dev.toString)
        }

        os.ui.action(s"Format partition ${part.dev.toString} ($kind)") {
          Command.execute(command).toEither("Failed to format partition")
        }
      }
      else Right("Not formatable")
    }

    def setType() = {
      val (partType, id) = kind match {
        case PartitionFormat.ext2 => ("ext2", "83")
        case PartitionFormat.fat32 => ("vfat", "b")
        case PartitionFormat.ntfs => ("ntfs", "7")
      }

      if (!part.fsType.contains(partType)) {
        val input =
          s"""t
${part.partNumber}
$id
w
""".getBytes

        os.ui.action(s"Set partition ${part.dev.toString} type ($kind)") {
          Command.execute(Seq("fdisk", part.device.dev.toString), stdinSource = Command.input(input)).toEither("Failed to set partition type") ||
            Command.execute(Seq("partprobe", "-d", part.device.dev.toString)).toEither("Failed to set partition type")
        }
      }
      else Right("Partition type already set")
    }

    def setLabel() = {
      if (!part.label.contains(label)) {
        val (command, envf) = kind match {
          case PartitionFormat.ext2 =>
            // Max ext2 label length: 16
            (Seq("e2label", part.dev.toString, label), None)

          case PartitionFormat.fat32 =>
            def commandEnvf(env: java.util.Map[String, String]) {
              env.put("MTOOLS_SKIP_CHECK", "1")
              ()
            }

            val actualLabel = label.take(11).padTo(11, ' ')
            // Max FAT32 label length: 11
            // To work correctly, it is better to truncate/pad it.
            (Seq("mlabel", "-i", part.dev.toString, s"::$actualLabel"), Some(commandEnvf _))

          case PartitionFormat.ntfs =>
            (Seq("ntfslabel", "--force", part.dev.toString, label), None)
        }

        os.ui.action(s"Set partition ${part.dev.toString} label ($label)") {
          Command.execute(command, envf = envf).toEither("Failed to label partition")
        }
      }
      else Right("Partition label already set")
    }

    format && setType && setLabel &&
      part.device.partprobe().toEither("Failed to refresh partition table")
  }

  private def installBootloader(profile: ProfileSettings, os: OSInstall, mount: Option[PartitionMount]): Unit = {
    for {
      syslinuxVersion <- os.settings.syslinuxVersion
      part <- os.settings.partition.get
      mount <- mount
    } {
      // Note: we already ensured this syslinux version was found
      val syslinux = SyslinuxInstall.get(profile, syslinuxVersion).get
      val CommandResult(result, _, stderr) =
        os.settings.partitionFormat match {
          case _: PartitionFormat.extX =>
            val syslinuxBin = syslinux.modules.resolve(Paths.get("extlinux", "extlinux"))
            val target = mount.to.resolve("syslinux")
            Command.execute(Seq(syslinuxBin.toString, "--install", target.toString))

          case _: PartitionFormat.MS =>
            // Note: it is safer (and mandatory for NTFS) to unmount partition first
            mount.umount()
            val syslinuxBin = syslinux.modules.resolve(Paths.get("linux", "syslinux"))
            val target = part.dev
            Command.execute(Seq(syslinuxBin.toString, "--install", target.toString))
        }
      if (result != 0) {
        logger.error(s"Failed to install syslinux bootloader: $stderr")
        throw new Exception("Failed to install syslinux bootloader")
      }
    }
  }

  private def deleteContent(root: Path) {
    if (!root.toFile.delete(true, true)) {
      // Some files may have the 'immutable' attribute
      val finder = PathFinder(root).***

      finder.get().map(_.toPath).foreach { path =>
        Command.execute(Seq("chattr", "-i", path.toString))
      }

      if (!root.toFile.delete(true, true)) {
        val msg = "Some content could not be deleted"
        logger.error(s"$msg: ${finder.get().mkString(", ")}")
        throw new Exception(msg)
      }
    }
  }

  def install(profile: ProfileSettings, os: OSInstall): Unit = {
    os.ui.none()

    // Prepare
    if (os.settings.install) {
      os.ui.setStep(s"Prepare ${os.settings.label} installation")
      os.checkCancelled()
      os.prepare()
    }

    // Actual install
    if (os.settings.enabled) {
      os.ui.none()
      os.ui.setStep(s"Install ${os.settings.label}")
      os.checkCancelled()
    }

    if (os.settings.enabled) {
      // prepare syslinux
      os.settings.syslinuxVersion.foreach { version =>
        os.ui.action(s"Search syslinux $version") {
          if (SyslinuxInstall.get(profile, version).isEmpty) {
            throw new Exception(s"Could not find syslinux $version")
          }
          val name = SyslinuxInstall.getSource(profile, version).map(_.getFileName.toString).getOrElse("n/a")
          os.ui.activity(s"Syslinux found: $name")
        }
      }

      // prepare (format, set type and label) partition
      os.settings.partition.get.foreach { part =>
        os.checkCancelled()
        preparePartition(os, part).orThrow
      }
    }

    if (os.settings.enabled) {
      os.checkCancelled()
      mountAndDo(os, (isoMount, partMount) => {
        // erase content
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

        // prepare EFI
        os.searchEFI(partMount)

        if (os.settings.enabled) {
          os.ui.action(s"Install bootloader") {
            os.checkCancelled()
            installBootloader(profile, os, partMount)
          }
        }
      })
    }

    os.ui.none()
  }

}
