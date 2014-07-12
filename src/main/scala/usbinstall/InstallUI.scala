package usbinstall

import javafx.scene.control.Label
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.LogArea


class InstallUI(
  step: Label,
  action: Label,
  globalActivity: LogArea,
  var osActivity: Option[LogArea]
) {

  def setStep(value: Option[String]) {
    /* Note: we need to determine the value now (deferred call may see an
     * updated value for the given 'var'.
     */
    val osActivity = this.osActivity
    jfxSchedule {
      step.setText(value.getOrElse(""))
      value foreach { value =>
        osActivity foreach(_.write(s"**** Step: $value"))
        globalActivity.write(s"**** Step: $value")
      }
    }
  }

  def setStep(value: String) {
    setStep(Option(value))
  }

  def setAction(value: Option[String]) {
    val osActivity = this.osActivity
    jfxSchedule {
      action.setText(value.getOrElse(""))
      value foreach { value =>
        osActivity foreach(_.write(s"** Action: $value"))
        globalActivity.write(s"** Action: $value")
      }
    }
  }

  def setAction(value: String) {
    setAction(Option(value))
  }

  def action[T](value: String)(todo: => T): T = {
    setAction(value)
    try {
      val r = todo
      setAction(None)
      r
    }
    catch {
      case e: Throwable =>
        setAction(s"'$value' failed")
        throw e
    }
  }

  def none() {
    setStep(None)
    setAction(None)
  }

  def activity(value: String) {
    val osActivity = this.osActivity
    jfxSchedule {
      osActivity foreach(_.write(value))
    }
  }

  protected def jfxSchedule(action: => Unit) {
    JFXSystem.schedule(action, false)
  }

}
