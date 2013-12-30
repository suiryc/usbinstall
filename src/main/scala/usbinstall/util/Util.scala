package usbinstall.util

import dev.scalascript.sys.Command
import java.io.File
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import java.nio.file.attribute.BasicFileAttributes
import scala.reflect.ClassTag


object Util {

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

  /**
   * Wraps null array.
   * Replaces null by empty array if needed.
   *
   * @param a array to wrap
   * @return non-null array
   */
  def wrapNull[T: ClassTag](a: Array[T]) =
    if (a == null) new Array[T](0)
    else a

  /**
   * Gets whether a file is a directory.
   * Also works for symbolic links.
   *
   * @param file path to test
   * @return whether path is a directory (even if symbolic link)
   */
  @annotation.tailrec
  def isDirectory(file: File): Option[File] = if (file.isDirectory) Some(file)
    else if (isSymbolicLink(file)) isDirectory(Files.readSymbolicLink(file.toPath).toFile)
    else None

  /**
   * Gets whether a file is a symbolic link.
   *
   * @param file path to test
   * @return whether path is a symbolic link
   */
  def isSymbolicLink(file: File): Boolean =
    readAttributes(file.toPath, classOf[BasicFileAttributes],
      LinkOption.NOFOLLOW_LINKS) map { _.isSymbolicLink } getOrElse(false)

  /**
   * Reads path attributes.
   *
   * @param path path to get attributes from
   * @return attributes
   */
  def readAttributes[A <: BasicFileAttributes](path: Path, typ: Class[A], options: LinkOption*): Option[A] =
    if (Files.exists(path, options: _*)) Some(Files.readAttributes(path, typ, options: _*))
    else None

  /**
   * Backups file from source to target.
   *
   * @param sourceRoot root for source file
   * @param source     (relative) source to backup
   * @param targetRoot root for target file
   */
  def backupFile(sourceRoot: Path, source: Path, targetRoot: Path) {
    def backupFile(sourceRoot: Path, source: Option[Path], targetRoot: Path) {
      import RichFile._

      source match {
        case Some(source) =>
          val sourcePath = sourceRoot.resolve(source)
          val sourceRealPath = sourcePath.getParent.toRealPath().resolve(sourcePath.toFile.getName)
          /*if (!sourceRealPath.startsWith(sourceRoot))
            log(0, s"WARNING! Real path[$sourceRealPath] is outside root path[$sourceRoot], skipping")
          else {*/
            val sourceReal = sourceRoot.relativize(sourceRealPath)
            val pathTarget = targetRoot.resolve(sourceReal)
            if (!pathTarget.exists) {
              /* first make sure parent exists (both real and possible link) */
              backupFile(sourceRoot, Option(source.getParent), targetRoot)
              backupFile(sourceRoot, Option(sourceReal.getParent), targetRoot)
              /* then copy source to target */
              /*log(2, s"Coying path[$sourceRealPath] to path[$pathTarget]")*/
              Files.copy(sourceRealPath, pathTarget,
                StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
            }
          /*}*/

        case None =>
          if (!targetRoot.exists) {
            /* first make sure parent exists */
            backupFile(sourceRoot, None, targetRoot.getParent)
            /* then create target (directory) with user/group if necessary */
            targetRoot.mkdir
            /*targetRoot.changeOwner(config.user, config.group)*/
          }
      }
    }

    /*log(1, s"Path: $source")*/

    backupFile(sourceRoot, Some(source), targetRoot)
  }

}
