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

  def setStep(value: Option[String]): Unit = {
    jfxSchedule {
      step.setText(value.getOrElse(""))
    }
    value.foreach { value =>
      osActivity.foreach(_.write(s"**** Step: $value"))
      globalActivity.write(s"**** Step: $value")
    }
  }

  def setStep(value: String): Unit = {
    setStep(Option(value))
  }

  def setAction(value: Option[String]): Unit = {
    jfxSchedule {
      action.setText(value.getOrElse(""))
    }
    value.foreach { value =>
      osActivity.foreach(_.write(s"** Action: $value"))
      globalActivity.write(s"** Action: $value")
    }
  }

  def setAction(value: String): Unit = {
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
      case ex: Exception =>
        setAction(s"'$value' failed")
        throw ex
    }
  }

  def none(): Unit = {
    setStep(None)
    setAction(None)
  }

  def activity(value: String): Unit = {
    osActivity.foreach(_.write(value))
  }

  protected def jfxSchedule(action: => Unit): Unit = {
    JFXSystem.schedule(action, logReentrant = false)
  }

}
