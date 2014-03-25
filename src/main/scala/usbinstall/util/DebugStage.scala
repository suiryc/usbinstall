package usbinstall.util

import javafx.scene.control.ListCell
import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.{Label, ListView}
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafx.stage.{Stage, WindowEvent}
import suiryc.scala.io.LineSplitterOutputStream
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.misc.{MessageLevel, MessageLineWriter, MessageWriter}
import suiryc.scala.sys.Command


object DebugStage {

  /* XXX - scrollbars sometimes appear when resizing LogArea down */

  private val area = new LogArea {
    vgrow = Priority.ALWAYS
  }

  def logAreaWriter(area: LogArea) =
    new MessageLineWriter {
      override def write(line: String) =
        JFXSystem.schedule {
          area.write(line)
        }
    }

  val areaWriter = logAreaWriter(area)

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

  private val listViewItems = ObservableBuffer[MessageCellData]()
  private val listView = new ListView[MessageCellData] {
    items = listViewItems
    cellFactory = { lv =>
      new MessageCell
    }
  }

  protected def scrollListViewToEnd() {
    listView.scrollTo(listView.items().length)
  }

  val listViewWriter = new MessageWriter {

    override def write(level: MessageLevel.LevelValue, msg: String, throwable: Option[Throwable]) {
      val item = MessageCellData(level, msg)
      listViewItems.add(item)
      scrollListViewToEnd()
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
    onCloseRequest = { (event: WindowEvent) =>
      event.consume()
      DebugStage.hide()
    }
  }

  def show() {
    dstage.show
    listView.items = listViewItems
    scrollListViewToEnd()
  }

  def hide() {
    /* Note: updating the list view when not visible generates warnings */
    listView.items = ObservableBuffer[MessageCellData]()
    dstage.hide
  }

}
