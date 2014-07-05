package usbinstall

import ch.qos.logback.classic.{Logger, LoggerContext}
import javafx.application.Application
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import suiryc.scala.io.RichFile._
import suiryc.scala.misc.MessageWriter
import usbinstall.settings.{InstallSettings, Settings}
import usbinstall.util.ProxyAppender
import usbinstall.util.DebugStage


object USBInstall extends App {

  protected val loggerNames = List("usbinstall", "suiryc")

  val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]

  var stage: Stage = _

  var firstScene = true

  /* XXX - check we are root */
  /* XXX - check external tools are present */

  (new USBInstall).launch()

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

class USBInstall extends Application {

  import USBInstall._

  protected var appender: ProxyAppender = _

  def launch() {
    Application.launch()
  }

  override def start(primaryStage: Stage) {
    stage = primaryStage

    appender = newAppender(List(DebugStage.areaWriter, DebugStage.listViewWriter))
    addAppender(appender)

    Stages.chooseDevice()
    /* Explicitely load the settings */
    Settings.load()
  }

  override def stop() {
    InstallSettings.pathTemp.delete(true)
    detachAppender(appender)
  }

}
