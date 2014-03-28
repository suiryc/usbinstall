package usbinstall

import javafx.scene.Node
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.ToggleButton
import scalafx.stage.{Modality, Stage}
import scalafxml.core.{FXMLView, NoDependencyResolver}
import scalafxml.core.macros.sfxml
import suiryc.scala.javafx.concurrent.JFXSystem
import usbinstall.util.DebugStage


@sfxml
class ToolBarController(
  private val showLogs: ToggleButton,
  private val stepPane: StepPane
)
{

  showLogs.selected = DebugStage.showing()

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */
  stepPane.subscriptions ::= DebugStage.showing.onChange { (_, _, newValue) =>
    showLogs.selected = newValue
  }

  def onOptions(event: ActionEvent) {
    val options = FXMLView(getClass.getResource("options.fxml"),
      NoDependencyResolver)
    val stage = new Stage {
      title = "Options"
      scene = new Scene {
        root = options
      }
    }
    stage.initModality(Modality.WINDOW_MODAL)
    stage.initOwner(event.source.asInstanceOf[Node].scene().window())
    stage.showAndWait()
  }

  def onShowLogs(event: ActionEvent) {
    if (showLogs.selected()) DebugStage.show()
    else DebugStage.hide()
  }

}
