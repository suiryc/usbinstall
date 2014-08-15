package usbinstall.os

import grizzled.slf4j.Logging
import java.nio.file.Path
import suiryc.scala.sys.{Command, CommandResult}


class PartitionMount(
  val from: Path,
  val to: Path,
  val mountOptions: Seq[String] = Nil
) extends Logging
{

  private var _mounted = false

  def mounted = _mounted

  def mount() = if (!mounted) {
    val CommandResult(result, stdout, stderr) = Command.execute(Seq("mount") ++ mountOptions ++ Seq(from.toString, to.toString))

    if (result != 0) {
      error(s"Cannot mount partition from $from to $to: $stderr")
      throw new Exception(s"Cannot mount partition[$from]: $stderr")
    }

    _mounted = true
  }

  def umount() = if (mounted) {
    val CommandResult(result, stdout, stderr) = Command.execute(Seq("umount", to.toString))

    if (result != 0) {
      val CommandResult(result, stdout, stderr) = Command.execute(Seq("umount", "-lf", to.toString))

      if (result != 0) {
        error(s"Cannot unmount partition[$to]: $stderr")
      }
    }

    _mounted = false
  }

}
