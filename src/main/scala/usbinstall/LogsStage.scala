package usbinstall

import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.fxml.FXMLLoader
import javafx.scene.{Parent, Scene}
import javafx.stage.{Stage, WindowEvent}
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.stage.{Stages ⇒ sfxStages}
import suiryc.scala.log.ThresholdLogLinePatternWriter
import usbinstall.controllers.LogsController
import usbinstall.settings.Settings


object LogsStage {

  // Note: when resizing down the TextArea, vertical scrollbar may appear even
  // if there is actually nothing to scroll (empty area).
  // There does not seem to be a correct way to get rid of the scrollbar when
  // not needed (e.g. listening to visible size/region size changes etc).

  private val loader = new FXMLLoader(getClass.getResource("/fxml/logs.fxml"))
  private val root = loader.load[Parent]()
  private val controller = loader.getController[LogsController]()

  val areaWriter: ThresholdLogLinePatternWriter = controller.logArea.msgWriter
  areaWriter.setPattern(Settings.core.logDebugPattern)
  areaWriter.setThreshold(Settings.core.logDebugThreshold.get.level)
  Settings.core.logDebugThreshold.listen { v =>
    areaWriter.setThreshold(v.level)
    controller.logThreshold.getSelectionModel.select(Settings.core.logDebugThreshold.get)
  }

  //
  // Example code with basic list view
  // Note: populating list view while hidden trigger warnings
  //
  // Note: logger sometimes is already running inside JavaFX thread
  //  protected def jfxSchedule(action: => Unit) {
  //    JFXSystem.schedule(action, false)
  //  }
  //
  //  case class MessageCellData(val level: MessageLevel.Value, val msg: String)
  //
  //  class MessageCell
  //    extends ListCell[MessageCellData]
  //  {
  //    val hbox = new HBox
  //    val labelType = new Label
  //    val labelMsg = new Label
  //
  //    hbox.getChildren.addAll(labelType, labelMsg)
  //
  //    override protected def updateItem(item: MessageCellData, empty: Boolean) {
  //      super.updateItem(item, empty)
  //      setText(null)
  //      if (empty) {
  //        setGraphic(null)
  //      }
  //      else {
  //        val (txtType, txtMsg) = Option(item).map { msg =>
  //          (msg.level.shortName, msg.msg)
  //        } getOrElse(("UNK", "No Message"))
  //        labelType.setText(txtType)
  //        labelMsg.setText(txtMsg)
  //        setGraphic(hbox)
  //      }
  //    }
  //}
  //
  //  private val listViewItems = FXCollections.observableArrayList[MessageCellData]()
  //  private val listView = new ListView[MessageCellData]
  //  listView.setItems(listViewItems)
  //  listView.setCellFactory { (lv: ListView[MessageCellData]) =>
  //    new MessageCell
  //  }
  //
  //  protected def scrollListViewToEnd() {
  //    if (showing.get)
  //      listView.scrollTo(listView.getItems().size)
  //  }
  //
  //  val listViewWriter = new MessageWriter {
  //
  //    override def write(level: MessageLevel.Value, msg: String, throwable: Option[Throwable]) {
  //      val item = MessageCellData(level, msg)
  //      jfxSchedule {
  //        listViewItems.add(item)
  //        scrollListViewToEnd()
  //      }
  //    }
  //
  //  }

  protected val stage = new Stage
  stage.setTitle("Logs")
  stage.setScene(new Scene(root))
  // Note: stage will disappear if declared owner is hidden.
  // So don't use initOwner.

  stage.setOnCloseRequest { event: WindowEvent =>
    event.consume()
    LogsStage.hide()
  }

  // Only track minimum dimensions upon first display
  sfxStages.onStageReady(stage, first = false) {
    sfxStages.setMinimumDimensions(stage)
  }
  // Keep position/size upon hiding/showing
  sfxStages.keepBounds(stage)

  def show() {
    stage.show()
  }

  def hide() {
    stage.hide()
  }

  def showing: ReadOnlyBooleanProperty = stage.showingProperty

}
