package usbinstall.util

import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.layout.{Priority, VBox}
import scalafx.stage.Stage
import suiryc.scala.io.LineSplitterOutputStream
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.misc.MessageLineWriter
import suiryc.scala.sys.Command


object DebugStage {

  /* XXX - debugArea has scrollbars if resizing down, and scrollbars disappear when resizing up */

  val area = new LogArea {
    vgrow = Priority.ALWAYS
  }

  val areaWriter = new MessageLineWriter {
    override def write(line: String) =
      JFXSystem.schedule {
        area.write(line)
      }
  }

  private val areaOutputStream = new LineSplitterOutputStream(areaWriter)
  Command.addExtraOutputSink(areaOutputStream)

  private val dpane = new VBox {
    padding = Insets(5)
    spacing = 5
    alignment = Pos.TOP_CENTER
    content = List(area)
  }
  private val dscene = new Scene {
    root = dpane
  }
  private val dstage = new Stage {
    scene = dscene
  }

  def show =
    dstage.show

  def hide =
    dstage.hide

}
