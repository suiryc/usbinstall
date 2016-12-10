package usbinstall

import javafx.beans.property.{SimpleBooleanProperty, SimpleStringProperty}


abstract class AbstractStepButton(
  val visible: Boolean,
  xdisabled: Boolean,
  xlabel: String,
  var onTrigger: () => Boolean
) {

  def triggered(): Unit

  val disableProperty = new SimpleBooleanProperty(xdisabled)
  def disable: Boolean = disableProperty.get
  def disable_=(v: Boolean): Unit = disableProperty.set(v)

  val labelProperty = new SimpleStringProperty(xlabel)
  def label: String = labelProperty.get
  def label_=(v: String): Unit = labelProperty.set(v)

}

object NoButton extends AbstractStepButton(false, false, "", () => false) {
  override def triggered(): Unit = {}
}

class StepButton(
  pane: StepPane,
  f: => Boolean,
  xlabel: String
) extends AbstractStepButton(true, false, xlabel, () => f)
{

  override def triggered(): Unit = {
    if (onTrigger()) pane.cancelSubscriptions()
  }

}

class PreviousButton(pane: StepPane, f: => Boolean)
  extends StepButton(pane, f, "Previous")

class NextButton(pane: StepPane, f: => Boolean)
  extends StepButton(pane, f, "Next")

class CancelButton(pane: StepPane, f: => Boolean)
  extends StepButton(pane, f, "Cancel")
