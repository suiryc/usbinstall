package usbinstall

import javafx.scene.layout.Pane
import suiryc.scala.concurrent.Cancellable


trait StepPane
  extends Pane
  with HasEventSubscriptions
{

  // Note: subscriptions on external object need to be cancelled for
  // pane/scene to be GCed.

  var subscriptionHolders: List[HasEventSubscriptions] =
    Nil

  override def cancelSubscriptions(): Unit = {
    super.cancelSubscriptions()
    subscriptionHolders.foreach(_.cancelSubscriptions())
    subscriptionHolders = Nil
  }

  val previous: AbstractStepButton

  val next: AbstractStepButton

}

trait UseStepPane {
  def setStepPane(stepPane: StepPane): Unit
}

trait HasEventSubscriptions {

  // Note: this is for subscriptions on objects not owned by the controller
  // pane (e.g. Settings accessible from all classes). We need to track those
  // and cancel them when leaving the pane, otherwise they remain forever
  // and can be problematic when navigating forth and back in panes.
  protected var subscriptions: List[Cancellable] =
    Nil

  def cancelSubscriptions(): Unit = {
    subscriptions.foreach(_.cancel())
    subscriptions = Nil
  }

}
