package usbinstall

import ch.qos.logback.classic.{Logger, LoggerContext}
import org.slf4j.LoggerFactory
import scalafx.application.JFXApp
import suiryc.scala.io.RichFile._
import suiryc.scala.misc.MessageWriter
import usbinstall.settings.InstallSettings
import usbinstall.util.ProxyAppender
import usbinstall.util.DebugStage


object USBInstall extends JFXApp {

  protected val loggerNames = List("usbinstall", "suiryc")

  val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
  val appender = newAppender(List(DebugStage.areaWriter, DebugStage.listViewWriter))
  addAppender(appender)

  DebugStage.show()

  Stages.chooseDevice()

  override def stopApp() {
    InstallSettings.pathTemp.delete(true)
    detachAppender(appender)
  }

  def newAppender(writers: Seq[MessageWriter]) = {
    val appender = new ProxyAppender(writers)
    appender.setContext(lc)
    appender.start()
    appender
  }

  def addAppender(appender: ProxyAppender) {
    loggerNames foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.addAppender(appender)
    }
  }

  def detachAppender(appender: ProxyAppender) {
    loggerNames foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.detachAppender(appender)
    }
  }

}
