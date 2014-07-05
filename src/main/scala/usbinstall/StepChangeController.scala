package usbinstall

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.Button
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._


class StepChangeController
  extends Initializable
  with UseStepPane
{

  @FXML
  protected var previous: Button = _

  @FXML
  protected var next: Button = _

  /* Note: subscriptions on tied objects do not need to be cancelled
   * for pane/scene to be GCed. */

  protected var stepPrevious: AbstractStepButton = _

  protected var stepNext: AbstractStepButton = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
  }

  override def setStepPane(stepPane: StepPane) {
    stepPrevious = stepPane.previous
    if (stepPrevious.visible) {
      previous.setText(stepPrevious.label)
      previous.setDisable(stepPrevious.disabled.get)
      stepPrevious.disabled.listen { disabled =>
        previous.setDisable(disabled)
      }
    }
    else previous.setVisible(false)

    stepNext = stepPane.next
    if (stepNext.visible) {
      next.setText(stepNext.label)
      next.setDisable(stepNext.disabled.get)
      stepNext.disabled.listen { disabled =>
        next.setDisable(disabled)
      }
    }
    else next.setVisible(false)
  }

  def onPrevious(event: ActionEvent) {
    stepPrevious.triggered
  }

  def onNext(event: ActionEvent) {
    stepNext.triggered
  }

}
