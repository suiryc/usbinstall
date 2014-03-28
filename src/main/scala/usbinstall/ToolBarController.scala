package usbinstall

import scalafx.event.ActionEvent
import scalafx.scene.control.ToggleButton
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
  }

  def onShowLogs(event: ActionEvent) {
    if (showLogs.selected()) DebugStage.show()
    else DebugStage.hide()
  }

}
