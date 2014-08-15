package usbinstall

import javafx.scene.layout.Pane
import suiryc.scala.concurrent.Cancellable


trait StepPane
  extends Pane
  with HasEventSubscriptions
{

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */

  var subscriptionHolders: List[HasEventSubscriptions] =
    Nil

  override def cancelSubscriptions() {
    super.cancelSubscriptions()
    subscriptionHolders foreach(_.cancelSubscriptions())
    subscriptionHolders = Nil
  }

  val previous: AbstractStepButton

  val next: AbstractStepButton

}

trait UseStepPane {
  def setStepPane(stepPane: StepPane)
}

trait HasEventSubscriptions {

  protected var subscriptions: List[Cancellable] =
    Nil

  def cancelSubscriptions() {
    subscriptions.foreach(_.cancel())
    subscriptions = Nil
  }

}
