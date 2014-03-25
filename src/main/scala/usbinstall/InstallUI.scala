package usbinstall

import scalafx.scene.control.Label
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.LogArea


class InstallUI(
  step: Label,
  action: Label,
  activity: LogArea
) {

  def setStep(value: Option[String]) {
    JFXSystem.schedule {
      step.text = value.getOrElse("")
      value foreach { value =>
        activity.write(s"**** Step: $value")
      }
    }
  }

  def setStep(value: String) {
    setStep(Option(value))
  }

  def setAction(value: Option[String]) {
    JFXSystem.schedule {
      action.text = value.getOrElse("")
      value foreach { value =>
        activity.write(s"** Action: $value")
      }
    }
  }

  def setAction(value: String) {
    setAction(Option(value))
  }

  def action[T](value: String)(todo: => T): T = {
    setAction(value)
    try {
      todo
    }
    finally {
      setAction(None)
    }
  }

  def none() {
    setStep(None)
    setAction(None)
  }

  def activity(value: String) {
    JFXSystem.schedule {
      activity.write(value)
    }
  }

}
