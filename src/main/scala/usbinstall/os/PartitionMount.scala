package usbinstall.os

import java.io.File
import suiryc.scala.sys.{Command, CommandResult}
import usbinstall.Stages


class PartitionMount(
  val from: File,
  val to: File,
  val mountOptions: Seq[String] = Nil
)
{

  private var _mounted = false

  def mounted = _mounted

  def mount = if (!mounted) {
    val CommandResult(result, stdout, stderr) = Command.execute(Seq("mount") ++ mountOptions ++ Seq(from.toString(), to.toString()))

    if (result != 0) {
      Stages.errorStage("Cannot mount partition", Some(s"From $from to $to"), stderr)
      throw new Exception(s"Cannot mount partition[$from]: $stderr")
    }

    _mounted = true
  }

  def umount = if (mounted) {
    val CommandResult(result, stdout, stderr) = Command.execute(Seq("umount", to.toString()))

    if (result != 0) {
      val CommandResult(result, stdout, stderr) = Command.execute(Seq("umount", "-lf", to.toString()))

      if (result != 0) {
        Stages.errorStage("Cannot unmount partition", Some(to.toString()), stderr)
      }
    }

    _mounted = false
  }

}
