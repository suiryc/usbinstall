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

  def mounted: Boolean = _mounted

  def mount(): Unit = if (!mounted) {
    // Sometimes trying to mount right after initializing the partition fails.
    // In this case wait a bit and retry.

    @scala.annotation.tailrec
    def loop(left: Int): Unit = {
      val CommandResult(result, _, stderr) = Command.execute(Seq("mount") ++ mountOptions ++ Seq(from.toString, to.toString))

      if (result != 0) {
        if (left > 0) {
          warn(s"Cannot mount partition from $from to $to: $stderr")
          Thread.sleep(500)
          loop(left - 1)
        }
        else {
          error(s"Cannot mount partition from $from to $to: $stderr")
          throw new Exception(s"Cannot mount partition[$from]: $stderr")
        }
      }
    }

    loop(2)

    _mounted = true
  }

  def umount(): Unit = if (mounted) {
    val CommandResult(result, _, _) = Command.execute(Seq("umount", to.toString))

    if (result != 0) {
      val CommandResult(result, _, stderr) = Command.execute(Seq("umount", "-lf", to.toString))

      if (result != 0) {
        error(s"Cannot unmount partition[$to]: $stderr")
      }
    }

    _mounted = false
  }

}
