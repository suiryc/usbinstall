package usbinstall.os

import java.nio.file.Paths
import usbinstall.settings.InstallSettings
import suiryc.scala.io.NameFilter._
import suiryc.scala.io.{PathFinder, RegularFileFilter}
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.sys.linux.DevicePartition


class OSInstall(val settings: OSSettings, val efi: Boolean = false) {

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
  def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {}

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

  /* XXX - caller must check enabled && installable ? */
  def prepare(os: OSInstall): Unit =
    if (os.settings.install)
      os.prepare

  private def mountAndDo(os: OSInstall, todo: (Option[PartitionMount], Option[PartitionMount]) => Unit): Unit = {
    val iso = os.settings.iso() map { pathISO =>
      new PartitionMount(pathISO, InstallSettings.pathMountISO)
    }
    val part = os.settings.partition() map { partition =>
      new PartitionMount(partition.dev, InstallSettings.pathMountPartition)
    }

    try {
      iso foreach { _.mount }
      part foreach { _.mount }
      todo(iso, part)
    }
    /* XXX - catch and log */
    finally {
      /* XXX - sync ? */
      /* XXX - try/catch ? */
      part foreach { _.umount }
      iso foreach { _.umount }
    }
  }

  private def preparePartition(os: OSInstall, part: DevicePartition) = {
    import suiryc.scala.misc.RichEither._

    val kind = os.settings.partitionFormat
    val label = os.settings.partitionLabel

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

      val input = s"""t
${part.partNumber}
$id
w
""".getBytes

      Command.execute(Seq("fdisk", part.device.dev.getPath), stdinSource = Command.input(input)).toEither("Failed to set partition type") ||
        Command.execute(Seq("partprobe", "-d", part.device.dev.getPath)).toEither("Failed to set partition type")
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

    val r = (if (os.settings.formatable) format else Right("Partition formatting disabled")) &&
      setType && setLabel

    /* slow devices need some time before being usable */
    Thread.sleep(1000)

    r
  }

  private def findEFI(os: OSInstall, mount: Option[PartitionMount]): Unit = {
    mount foreach { mount =>
      val finder = PathFinder(mount.to) * """(?i:efi)""".r * """(?i:boot)""".r * ("""(?i:bootx64.efi)""".r & RegularFileFilter)
      finder.get.toList.sorted.headOption foreach { path =>
        os.settings.efiBootloader = Some(mount.to.toPath.relativize(path.toPath))
      }
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
            val syslinux = syslinuxRoot.toPath.resolve(Paths.get("extlinux", "extlinux")).toFile
            val target = mount.to.toPath.resolve("syslinux").toFile
            Command.execute(Seq(syslinux.getPath, "--install", target.getPath))

          case _: PartitionFormat.MS =>
            /* Note: it is safer (and mandatory for NTFS) to unmount partition first */
            mount.umount
            val syslinux = syslinuxRoot.toPath.resolve(Paths.get("linux", "syslinux")).toFile
            val target = part.dev
            Command.execute(Seq(syslinux.getPath, "--install", target.getPath))
        }
        /* XXX - what to do with command result ? */
      }
    }
  }

  /* XXX - caller must check enabled && installable ? */
  def install(os: OSInstall): Unit = {
    if (os.settings.install) {
      /* prepare syslinux */
      os.settings.syslinuxVersion foreach { version =>
        SyslinuxInstall.get(version)
        /* XXX - stop right now if problem ? */
        /* XXX - setting to only set syslinux ? */
      }

      /* prepare partition */
      os.settings.partition() foreach { part =>
        preparePartition(os, part)
      }
    }

    /* XXX - only if something to do ? */
    if (os.settings.enabled) {
      mountAndDo(os, (isoMount, partMount) => {
        if (os.settings.install) {
          os.install(isoMount, partMount)
        }

        /* prepare EFI */
        if (os.efi) {
          findEFI(os, partMount)
        }

        if (os.settings.install) {
          installBootloader(os, partMount)
        }
      })
    }
  }

  /* XXX - caller must check enabled && installable ? */
  def postInstall(os: OSInstall): Unit = {
    /* XXX - only if something to do ? */
    if (os.settings.install) {
      mountAndDo(os, (_, _) => os.postInstall)
    }
  }

}
