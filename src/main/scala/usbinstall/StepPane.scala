package usbinstall

import javafx.scene.layout.Pane
import suiryc.scala.javafx.event.Subscription


trait StepPane extends Pane {

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */
  var subscriptions: List[Subscription] = Nil

  def cancelSubscriptions {
    subscriptions foreach { _.unsubscribe }
    subscriptions = Nil
  }

  val previous: AbstractStepButton

  val next: AbstractStepButton

}

trait UseStepPane {
  def setStepPane(stepPane: StepPane)
}

trait HasEventSubscriptions {
  def getSubscriptions(): List[Subscription]
}
