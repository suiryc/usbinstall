package usbinstall.util

import javafx.collections.FXCollections
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.{Label, ListCell, ListView}
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.stage.{Stage, WindowEvent}
import suiryc.scala.io.LineSplitterOutputStream
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.javafx.util.Callback._
import suiryc.scala.misc.{MessageLevel, MessageLineWriter, MessageWriter}
import suiryc.scala.sys.Command


object DebugStage {

  /* XXX - scrollbars sometimes appear when resizing LogArea down */

  private var pos: Option[(Double, Double)] = None
  private var size: Option[(Double, Double)] = None

  private val area = new LogArea

  def logAreaWriter(area: LogArea) =
    new MessageLineWriter {
      override def write(line: String) =
        JFXSystem.schedule({
          area.write(line)
        }, false)
    }

  val areaWriter = logAreaWriter(area)

  private val areaOutputStream = new LineSplitterOutputStream(areaWriter)

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
        labelType.setText(txtType)
        labelMsg.setText(txtMsg)
        setGraphic(hbox)
      }
    }
  }

  private val listViewItems = FXCollections.observableArrayList[MessageCellData]()
  private val listView = new ListView[MessageCellData]
  listView.setItems(listViewItems)
  listView.setCellFactory { (lv: ListView[MessageCellData]) =>
    new MessageCell
  }

  protected def scrollListViewToEnd() {
    if (showing.get)
      listView.scrollTo(listView.getItems().size)
  }

  val listViewWriter = new MessageWriter {

    override def write(level: MessageLevel.LevelValue, msg: String, throwable: Option[Throwable]) {
      val item = MessageCellData(level, msg)
      listViewItems.add(item)
      scrollListViewToEnd()
    }

  }

  private val dpane = new VBox
  dpane.setPadding(new Insets(5))
  dpane.setSpacing(5)
  dpane.setAlignment(Pos.TOP_CENTER)
  VBox.setVgrow(area, Priority.ALWAYS)
  dpane.getChildren().setAll(area, listView)
  private val dscene = new Scene(dpane)
  private val stage = new Stage
  stage.setScene(dscene)
  stage.setOnCloseRequest { (event: WindowEvent) =>
    event.consume()
    DebugStage.hide()
  }

  def show() {
    pos foreach { t =>
      stage.setX(t._1)
      stage.setY(t._2)
    }
    size foreach { t =>
      stage.setWidth(t._1)
      stage.setHeight(t._2)
    }
    stage.show
    listView.setItems(listViewItems)
    scrollListViewToEnd()
  }

  def hide() {
    pos = Some(stage.getX(), stage.getY())
    size = Some(stage.getWidth(), stage.getHeight())
    /* Note: updating the list view when not visible generates warnings */
    listView.setItems(FXCollections.observableArrayList[MessageCellData]())
    stage.hide
  }

  def showing = stage.showingProperty

}
