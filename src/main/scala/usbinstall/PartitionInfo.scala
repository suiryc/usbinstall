package usbinstall

import java.io.File
import java.nio.file.Paths
import scala.io.Source


class PartitionInfo(val device: DeviceInfo, val partNumber: Int) {

  val dev = new File(device.dev.getParentFile(), device.dev.getName() + partNumber)

  val size = PartitionInfo.size(dev).fold(_ => 0L, size => size)

  val uuid = PartitionInfo.uuid(dev).fold(_ => "unknown-uuid", uuid => uuid)

  override def toString =
    s"Partition(device=$device, dev=$dev, uuid=$uuid, size=$size)"

}

object PartitionInfo {

  def size(dev: File) =
    try {
      val (result, stdout, stderr) = Utils.doCmd(Seq("blockdev", "--getsz", dev.toString))
      if (result == 0) {
        Right(stdout.trim.toLong * 512)
      }
      else {
        Left(new Exception(s"Cannot get device size: $stderr"))
      }
    }
    catch {
      case e: Throwable =>
        Left(e)
    }

  def uuid(dev: File) =
    try {
      val (result, stdout, stderr) = Utils.doCmd(Seq("blkid", "-o", "value", "-s", "UUID", dev.toString))
      if ((result == 0) && (stdout.trim() != "")) {
        Right(stdout.trim)
      }
      else if (stderr != "") {
        Left(new Exception(s"Cannot get device UUID: $stderr"))
      }
      else {
        Left(new Exception(s"Cannot get device UUID"))
      }
    }
    catch {
      case e: Throwable =>
        Left(e)
    }

  def mounted(partition: PartitionInfo) =
    Source.fromFile(Paths.get("/", "proc", "mounts").toFile()).getLines() map { line =>
      line.trim().split("""\s""").head
    } exists { line =>
      (line == partition.dev.toString()) || (line == s"/dev/disk/by-uuid/${partition.uuid}")
    }

  def umount(partition: PartitionInfo) {
    val (result, stdout, stderr) = Utils.doCmd(Seq("umount", partition.dev.toString()))

    if (result != 0) {
      Stages.errorStage("Cannot unmount partition", Some(partition.dev.toString()), stderr)
    }
  }

}
