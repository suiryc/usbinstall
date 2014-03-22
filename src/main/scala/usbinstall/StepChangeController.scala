package usbinstall

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.control.Button
import scalafxml.core.macros.sfxml


@sfxml
class StepChangeController(
  private val previous: Button,
  private val next: Button,
  private val stepPane: Panes.StepPane
) {

  /* Note: subscriptions on tied objects do not need to be cancelled
   * for parent stage to be GCed. */

  val stepPrevious = stepPane.previous
  if (stepPrevious.visible) {
    previous.text = stepPrevious.label
    previous.disable = stepPrevious.disabled.value
    previous.onAction = { e: ActionEvent =>
      stepPrevious.triggered
    }
    stepPrevious.disabled.onChange { (_, _, disabled) =>
      previous.disable = disabled
    }
  }
  else previous.visible = false

  val stepNext = stepPane.next
  if (stepNext.visible) {
    next.text = stepNext.label
    next.disable = stepNext.disabled.value
    next.onAction = { e: ActionEvent =>
      stepNext.triggered
    }
    stepNext.disabled.onChange { (_, _, disabled) =>
      next.disable = disabled
    }
  }
  else next.visible = false

}
