package usbinstall

import ch.qos.logback.classic.{Logger, LoggerContext}
import java.io.PrintStream
import javafx.application.Application
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import suiryc.scala.io.{
  LineSplitterOutputStream,
  ProxyLineWriter,
  SystemStreams
}
import suiryc.scala.io.RichFile._
import suiryc.scala.log.ProxyAppender
import suiryc.scala.misc.{MessageLineWriter, MessageWriter}
import usbinstall.settings.{InstallSettings, Settings}


object USBInstall extends App {

  protected val loggerNames = List("usbinstall", "suiryc")

  val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]

  var stage: Stage = _

  var firstScene = true

  protected var appender: ProxyAppender = _
  protected var lineWriter: ProxyLineWriter = _
  protected var systemStreams: SystemStreams = _

  /* XXX - check we are root */
  /* XXX - check external tools are present */

  (new USBInstall).launch()

  private def newAppender(writers: Seq[MessageWriter]) = {
    val appender = new ProxyAppender(writers)
    appender.setContext(lc)
    appender.start()
    appender
  }

  private def addAppender(appender: ProxyAppender) {
    loggerNames foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.addAppender(appender)
    }
  }

  private def detachAppender(appender: ProxyAppender) {
    loggerNames foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.detachAppender(appender)
    }
  }

  def addLogWriter(writer: MessageLineWriter) {
    appender.addWriter(writer)
    lineWriter.addWriter(writer)
  }

  def removeLogWriter(writer: MessageLineWriter) {
    appender.removeWriter(writer)
    lineWriter.removeWriter(writer)
  }

}

class USBInstall extends Application {

  import USBInstall._

  def launch() {
    Application.launch()
  }

  override def start(primaryStage: Stage) {
    stage = primaryStage

    appender = newAppender(List(LogsStage.areaWriter))
    addAppender(appender)

    lineWriter = new ProxyLineWriter(List(LogsStage.areaWriter))
    systemStreams = SystemStreams.replace(new PrintStream(new LineSplitterOutputStream(lineWriter)))

    Stages.chooseDevice()
    /* Explicitely load the settings */
    Settings.load()
  }

  override def stop() {
    InstallSettings.pathTemp.delete(true)
    detachAppender(appender)
    SystemStreams.restore(systemStreams)
  }

}
