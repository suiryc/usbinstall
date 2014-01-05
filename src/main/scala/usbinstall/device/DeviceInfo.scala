package usbinstall.device

import java.io.File
import java.nio.file.Paths
import scala.io.Source
import suiryc.scala.io.{NameFilter, PathFinder}


/* XXX - move to scala-misc, suiryc.scala.sys.linux ? */
class DeviceInfo(val block: File) {
  import DeviceInfo._
  import PathFinder._
  import NameFilter._

  val vendor = deviceVendor(block)
  val model = deviceModel(block)
  val props = ueventProps(block)
  val dev = new File("/dev", block.getName())

  def size: Either[Throwable, Long] = PartitionInfo.size(dev)

  def partitions =
    (dev.getParentFile() * s"""${dev.getName()}[0-9]+""".r).get map { path =>
      new PartitionInfo(this, path.getName().substring(dev.getName().length()).toInt)
    }

  override def toString =
    s"Device(block=$block, dev=$dev, vendor=$vendor, model=$model, props=$props)"

}

object UnknownDevice extends DeviceInfo(new File(""))

object DeviceInfo {

  private val KeyValueRegexp = """^([^=]*)=(.*)$""".r

  def devicePropertyContent(block: File, path: String*) = {
    val file = Paths.get(block.toString(), path: _*).toFile()

    Option(
      if (file.exists()) 
        Source.fromFile(file).getLines() map { line =>
          line.trim()
        } filterNot { line =>
          line == ""
        } mkString(" / ")
      else null
    )
  }

  def deviceVendor(block: File) =
    devicePropertyContent(block, "device", "vendor") getOrElse "<unknown>"

  def deviceModel(block: File) =
    devicePropertyContent(block, "device", "model") getOrElse "<unknown>"

  private def ueventProps(block: File) = {
    val uevent = Paths.get(block.toString(), "device", "uevent").toFile()
    val props = Map.empty[String, String]

    if (uevent.exists()) {
      Source.fromFile(uevent).getLines().toList.foldLeft(props) { (props, line) =>
        line match {
          case KeyValueRegexp(key, value) =>
            props + (key.trim() -> value.trim())

          case _ =>
            props
        }
      }
    }
    else props
  }

  def apply(block: File): DeviceInfo = new DeviceInfo(block)

}
