package usbinstall

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.scene.{Node, Parent, Scene}
import javafx.scene.control.ToggleButton
import javafx.stage.{Modality, Stage}
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.Subscription
import suiryc.scala.javafx.stage.{Stages => sfxStages}


class ToolBarController
  extends Initializable
  with HasEventSubscriptions
{

  @FXML
  protected var showLogs: ToggleButton = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    showLogs.setSelected(LogsStage.showing.get)
    /* Note: subscriptions on external object need to be cancelled for
     * pane/scene to be GCed. */
    subscriptions ::= LogsStage.showing.listen { newValue =>
      showLogs.setSelected(newValue)
    }
  }

  def onOptions(event: ActionEvent) {
    /* XXX - track minimum width to apply */
    val options = FXMLLoader.load[Parent](getClass.getResource("options.fxml"))
    val stage = new Stage
    stage.setTitle("Options")
    stage.setScene(new Scene(options))
    stage.initModality(Modality.WINDOW_MODAL)
    stage.initOwner(event.getSource.asInstanceOf[Node].getScene().getWindow())
    /* Track dimension as soon as shown, and unlisten once done */
    val subscription = stage.showingProperty().listen { showing =>
      if (showing) sfxStages.trackMinimumDimensions(stage)
    }
    stage.showAndWait()
    subscription.unsubscribe()
  }

  def onShowLogs(event: ActionEvent) {
    if (showLogs.isSelected) LogsStage.show()
    else LogsStage.hide()
  }

}
