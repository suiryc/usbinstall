package usbinstall.util

import javafx.scene.control.ListCell
import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.{Label, ListView}
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafx.stage.Stage
import suiryc.scala.io.LineSplitterOutputStream
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.misc.{MessageLevel, MessageLineWriter, MessageWriter}
import suiryc.scala.sys.Command


object DebugStage {

  /* XXX - debugArea has scrollbars if resizing down, and scrollbars disappear when resizing up */

  private val area = new LogArea {
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

  case class MessageCellData(val level: MessageLevel.LevelValue, val msg: String)

  class MessageCell
    extends ListCell[MessageCellData]
  {
    val hbox = new HBox
    val labelType = new Label
    val labelMsg = new Label

    hbox.getChildren.addAll(labelType, labelMsg)

    override protected def updateItem(item: MessageCellData, empty: Boolean) {
      super.updateItem(item, empty)
      setText(null)
      if (empty) {
        setGraphic(null)
      }
      else {
        val (txtType, txtMsg) = Option(item) map { msg =>
          (msg.level.shortName, msg.msg)
        } getOrElse(("UNK", "No Message"))
        labelType.text = txtType
        labelMsg.text = txtMsg
        setGraphic(hbox)
      }
    }
  }

  private val listView = new ListView[MessageCellData] {
    items = ObservableBuffer()
    cellFactory = { lv =>
      new MessageCell
    }
  }

  val listViewWriter = new MessageWriter {

    override def write(level: MessageLevel.LevelValue, msg: String) {
      listView.items().add(MessageCellData(level, msg))
    }

  }

  private val dpane = new VBox {
    padding = Insets(5)
    spacing = 5
    alignment = Pos.TOP_CENTER
    content = List(area, listView)
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
