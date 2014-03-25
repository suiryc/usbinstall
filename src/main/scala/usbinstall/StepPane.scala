package usbinstall

import scalafx.event.subscriptions.Subscription
import scalafx.scene.layout.Pane


trait StepPane extends Pane {

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */
  var subscriptions: List[Subscription] = Nil

  def cancelSubscriptions {
    subscriptions foreach { _.cancel }
    subscriptions = Nil
  }

  val previous: AbstractStepButton

  val next: AbstractStepButton

}

trait HasEventSubscriptions {
  def getSubscriptions(): List[Subscription]
}

trait HasCancel {
  def onCancel(): Unit
}
