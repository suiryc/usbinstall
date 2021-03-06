package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.scene.{Node, Parent, Scene}
import javafx.scene.control.ToggleButton
import javafx.stage.{Modality, Stage}
import scala.annotation.unused
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.stage.{Stages => sfxStages}
import usbinstall.{HasEventSubscriptions, LogsStage}


class ToolBarController
  extends Initializable
  with HasEventSubscriptions
{

  @FXML
  protected var showLogs: ToggleButton = _

  protected var paneController: Option[Any] = None

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {
    showLogs.setSelected(LogsStage.showing.get)
    // Note: subscriptions on external object need to be cancelled for
    // pane/scene to be GCed.
    subscriptions ::= LogsStage.showing.listen { newValue =>
      showLogs.setSelected(newValue)
    }
  }

  def setPaneController(controller: Option[Any]): Unit = {
    paneController = controller
  }

  def onOptions(event: ActionEvent): Unit = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/options.fxml"))
    val options = loader.load[Parent]()

    val stage = new Stage
    stage.setTitle("Options")
    stage.setScene(new Scene(options))
    stage.initModality(Modality.WINDOW_MODAL)
    sfxStages.initOwner(stage, event.getSource.asInstanceOf[Node].getScene.getWindow)

    sfxStages.onStageReady(stage, first = false) {
      sfxStages.setMinimumDimensions(stage)
    }
    stage.showAndWait()
  }

  def onShowLogs(@unused event: ActionEvent): Unit = {
    if (showLogs.isSelected) LogsStage.show()
    else LogsStage.hide()
  }

}
