package usbinstall

import dev.scalascript.sys.Command
import java.io.File


object Utils {

  private val SizeRegexp = """^([0-9]*)\s*([a-zA-Z]*)$""".r

  case class Unit(label: String, factor: Long)

  val sizeSIUnits = List(
    Unit("B", 1024L),
    Unit("KiB", 1024L),
    Unit("MiB", 1024L),
    Unit("GiB", 1024L),
    Unit("TiB", 1024L)
  )

  val sizeUnits = List(
    Unit("B", 1000L),
    Unit("K", 1000L),
    Unit("M", 1000L),
    Unit("G", 1000L),
    Unit("T", 1000L)
  )

  def toHumanReadableSize(size: Long) = {
    @scala.annotation.tailrec
    def loop(units: List[Unit], size: Long): (Long, Unit) = units match {
      case unit :: Nil =>
        (size, unit)

      case head :: tail =>
        if (size > 2 * head.factor) loop(tail, size / head.factor)
        else (size, head)
    }

    val (hrSize, unit) = loop(sizeSIUnits, size)
    hrSize.toString() + s" ${unit.label}"
  }

  def fromHumanReadableSize(size: String): Long = size match {
    case SizeRegexp(value, unit) =>
      val lcunit = unit.toLowerCase()
      def get(units: List[Unit]): Option[Long] = {
        if (units.exists(_.label.toLowerCase() == lcunit))
          Some(value.toLong * units.takeWhile(_.label.toLowerCase() != lcunit).foldLeft(1L)(_ * _.factor))
        else None
      }
      if (unit == "") value.toLong
      else get(sizeSIUnits).orElse(get(sizeUnits)).getOrElse(
        throw new IllegalArgumentException(s"Invalid size[$size]")
      )

    case _ =>
      throw new IllegalArgumentException(s"Invalid size[$size]")
  }

  /**
   * Executes system command.
   *
   * @param cmd           command to perform
   * @param workingDirectory working directory
   * @param captureStdout whether to capture stdout
   * @param printStdout   whether to print stdout
   * @param captureStderr whether to capture stderr
   * @param printStderr   whether to print stderr
   * @param skipResult    whether to not check return code
   * @returns a tuple with the return code, stdout and stderr contents (empty
   *   unless captured)
   * @throws RuntimeException if checked return code is not 0
   */
  def doCmd(
    cmd: Seq[String],
    workingDirectory: Option[File] = None,
    captureStdout: Boolean = true,
    printStdout: Boolean = false,
    captureStderr: Boolean = true,
    printStderr: Boolean = false,
    skipResult: Boolean = true
  ): (Int, String, String) =
  {
    val (result, stdoutBuffer, stderrBuffer) =
      Command.execute(
        cmd = cmd,
        workingDirectory = workingDirectory,
        captureStdout = captureStdout,
        printStdout = printStdout,
        captureStderr = captureStderr,
        printStderr = printStderr
      )

    if (!skipResult && (result != 0)) {
      /*log(2, s"Command[$cmd] failed: code[$result]"
        + (if (!printStdout && captureStdout) s" stdout[$stdoutBuffer]" else "")
        + (if (!printStderr && captureStderr) s" stderr[$stderrBuffer]" else "")
      )*/
      throw new RuntimeException("Nonzero exit value: " + result)
    }
    /*else {
      log(2, s"Command[$cmd] result: code[$result]"
        + (if (!printStdout && captureStdout) s" stdout[$stdoutBuffer]" else "")
        + (if (!printStderr && captureStderr) s" stderr[$stderrBuffer]" else "")
      )
    }*/

    (result, stdoutBuffer.toString, stderrBuffer.toString)
  }

}
