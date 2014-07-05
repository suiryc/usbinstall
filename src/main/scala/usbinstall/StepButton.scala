package usbinstall

import javafx.beans.property.{ReadOnlyBooleanProperty, SimpleBooleanProperty}


abstract class AbstractStepButton(
  val visible: Boolean,
  xdisabled: Boolean,
  val label: String
) {

  def triggered: Unit

  val disable = new SimpleBooleanProperty()
  def disable_=(v: Boolean) = disable.set(v)
  val disabled: ReadOnlyBooleanProperty = disable

  disable.set(xdisabled)

}

object NoButton extends AbstractStepButton(false, false, "") {
  override def triggered = {}
}

class StepButton(
  pane: StepPane,
  f: => Boolean,
  override val label: String
) extends AbstractStepButton(true, false, label)
{
  override def triggered = {
    if (f) pane.cancelSubscriptions
  }
}

class PreviousButton(pane: StepPane, f: => Boolean)
  extends StepButton(pane, f, "Previous")

class NextButton(pane: StepPane, f: => Boolean)
  extends StepButton(pane, f, "Next")

class CancelButton(pane: StepPane, f: => Boolean)
  extends StepButton(pane, f, "Cancel")
