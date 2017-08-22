package usbinstall

import ch.qos.logback.classic.Logger
import java.io.PrintStream
import java.util.Locale
import javafx.application.Application
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import suiryc.scala.io.{
  LineSplitterOutputStream,
  ProxyLineWriter,
  SystemStreams
}
import suiryc.scala.io.RichFile._
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.log.{
  Loggers,
  LogLinePatternWriter,
  LogWriter,
  ProxyAppender
}
import suiryc.scala.sys.{Command, CommandResult}
import usbinstall.settings.{InstallSettings, Settings}


object USBInstall {

  // Set locale to english as application is not i18n
  Locale.setDefault(Locale.ENGLISH)

  protected val loggerNames = List("usbinstall", "suiryc")

  var stage: Stage = _

  var firstScene = true

  protected var appender: ProxyAppender = _
  protected var lineWriter: ProxyLineWriter = _
  protected var systemStreams: SystemStreams = _


  def main(args: Array[String]) {
    (new USBInstall).launch()
  }

  private def newAppender(writers: Seq[LogWriter]) = {
    val appender = new ProxyAppender(writers)
    appender.setContext(Loggers.loggerContext)
    appender.start()
    appender
  }

  private def addAppender(appender: ProxyAppender) {
    loggerNames.foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.addAppender(appender)
    }
  }

  private def detachAppender(appender: ProxyAppender) {
    loggerNames.foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.detachAppender(appender)
    }
  }

  def addLogWriter(writer: LogLinePatternWriter) {
    appender.addWriter(writer)
    lineWriter.addWriter(writer)
  }

  def removeLogWriter(writer: LogLinePatternWriter) {
    appender.removeWriter(writer)
    lineWriter.removeWriter(writer)
  }

  val requirements =
    Set("blkid", "blockdev")

  protected var checkedRequirements: Set[String] = Set.empty

  def checkRequirements(requirements: Set[String]): Set[String] = {
    var unmet: Set[String] = Set.empty

    for (requirement <- requirements if !checkedRequirements.contains(requirement)) {
      val CommandResult(result, stdout, _) = Command.execute(Seq("which", requirement))
      if ((result != 0) || (stdout == ""))
        unmet += requirement
      else
        checkedRequirements += requirement
    }

    unmet
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

    Stages.chooseProfile()
    // Explicitly load the settings
    Settings.load()

    checkRequirements()
  }

  def checkRequirements() {
    try {
      val CommandResult(_, stdout, _) = Command.execute(Seq("id", "-u"))
      if (stdout != "0") {
        Dialogs.warning(
          owner = Some(USBInstall.stage),
          title = Some("Non-privileged user?"),
          headerText = None,
          contentText = Some("Running user may not have the required privileges to execute system commands")
        )
      }

      val unmet = USBInstall.checkRequirements(requirements)
      if (unmet.nonEmpty) {
        Dialogs.warning(
          owner = Some(USBInstall.stage),
          title = Some("Unmet requirements"),
          headerText = Some("The following requirements were not met.\nProgram may not work as expected."),
          contentText = Some(unmet.mkString("Missing executable(s): ", ", ", ""))
        )
      }
    }
    catch {
      case ex: Throwable =>
        Dialogs.error(
          owner = Some(USBInstall.stage),
          title = Some("Missing requirements"),
          headerText = Some("Could not check the requirements were met."),
          ex = Some(ex)
        )
    }

    Settings.profiles.values.foreach { profile =>
      // Accessing this lazy val now will trigger exceptions (error stage) for
      // non-existing paths.
      profile.syslinuxSettings.extraComponents
    }
    ()
  }

  override def stop() {
    InstallSettings.pathTemp.delete(recursive = true)
    detachAppender(appender)
    SystemStreams.restore(systemStreams)
  }

}
