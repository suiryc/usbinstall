package usbinstall.util

import javafx.collections.FXCollections
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.{Label, ListCell, ListView}
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.stage.{Stage, WindowEvent}
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.javafx.util.Callback._
import suiryc.scala.misc.{MessageLevel, MessageLineWriter, MessageWriter}
import suiryc.scala.sys.Command
import usbinstall.settings.Settings


object DebugStage {

  /* XXX - scrollbars sometimes appear when resizing LogArea down */

  /* Note: logger sometimes is already running inside JavaFX thread */
  protected def jfxSchedule(action: => Unit) {
    JFXSystem.schedule(action, false)
  }

  private var pos: Option[(Double, Double)] = None
  private var size: Option[(Double, Double)] = None

  private val area = new LogArea

  val areaWriter = area.msgWriter
  areaWriter.setThreshold(Settings.core.logDebugThreshold())
  Settings.core.logDebugThreshold.listen { v =>
    areaWriter.setThreshold(v)
  }

  case class MessageCellData(val level: MessageLevel.Value, val msg: String)

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

    override def write(level: MessageLevel.Value, msg: String, throwable: Option[Throwable]) {
      val item = MessageCellData(level, msg)
      jfxSchedule {
        listViewItems.add(item)
        scrollListViewToEnd()
      }
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
    /* Note: we are in the JavaFX thread */
    pos foreach { t =>
      stage.setX(t._1)
      stage.setY(t._2)
    }
    size foreach { t =>
      stage.setWidth(t._1)
      stage.setHeight(t._2)
    }
    /* Note: populating list view while hidden trigger warnings */
    stage.show
    listView.setItems(listViewItems)
    scrollListViewToEnd()
  }

  def hide() {
    /* Note: we are in the JavaFX thread */
    pos = Some(stage.getX(), stage.getY())
    size = Some(stage.getWidth(), stage.getHeight())
    stage.hide
    listView.setItems(FXCollections.observableArrayList[MessageCellData]())
  }

  def showing = stage.showingProperty

}
