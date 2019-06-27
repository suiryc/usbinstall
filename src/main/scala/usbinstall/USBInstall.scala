package usbinstall

import ch.qos.logback.classic.Logger
import java.io.PrintStream
import java.util.Locale
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import suiryc.scala.io.{LineSplitterOutputStream, ProxyLineWriter, SystemStreams}
import suiryc.scala.io.RichFile._
import suiryc.scala.javafx.{JFXApplication, JFXLauncher}
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.log.{LogLinePatternWriter, LogWriter, Loggers, ProxyAppender}
import suiryc.scala.sys.{Command, CommandResult}
import usbinstall.settings.{InstallSettings, Settings}


object USBInstall extends JFXLauncher[USBInstallApp] {

  // Set locale to english as application is not i18n
  Locale.setDefault(Locale.ENGLISH)

  private val loggerNames = List("usbinstall", "suiryc")

  var stage: Stage = _

  var firstScene = true

  protected[usbinstall] var appender: ProxyAppender = _
  protected[usbinstall] var lineWriter: ProxyLineWriter = _
  protected[usbinstall] var systemStreams: SystemStreams = _

  private[usbinstall] def newAppender(writers: Seq[LogWriter]) = {
    val appender = new ProxyAppender(writers)
    appender.setContext(Loggers.loggerContext)
    appender.start()
    appender
  }

  private[usbinstall] def addAppender(appender: ProxyAppender): Unit = {
    loggerNames.foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.addAppender(appender)
    }
  }

  private[usbinstall] def detachAppender(appender: ProxyAppender): Unit = {
    loggerNames.foreach { name =>
      val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
      logger.detachAppender(appender)
    }
  }

  def addLogWriter(writer: LogLinePatternWriter): Unit = {
    appender.addWriter(writer)
    lineWriter.addWriter(writer)
  }

  def removeLogWriter(writer: LogLinePatternWriter): Unit = {
    appender.removeWriter(writer)
    lineWriter.removeWriter(writer)
  }

  private[usbinstall] val requirements =
    Set("blkid", "blockdev")

  protected var checkedRequirements: Set[String] = Set.empty

  def checkRequirements(requirements: Set[String]): Set[String] = {
    var unmet: Set[String] = Set.empty

    for (requirement <- requirements if !checkedRequirements.contains(requirement)) {
      val CommandResult(result, stdout, _) = Command.execute(Seq("which", requirement))
      if ((result != 0) || (stdout == "")) {
        unmet += requirement
      } else {
        checkedRequirements += requirement
      }
    }

    unmet
  }

}

class USBInstallApp extends JFXApplication {

  import USBInstall._

  override def start(primaryStage: Stage): Unit = {
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

  def checkRequirements(): Unit = {
    try {
      val CommandResult(_, stdout, _) = Command.execute(Seq("id", "-u"))
      if (stdout != "0") {
        Dialogs.warning(
          owner = Some(USBInstall.stage),
          title = Some("Non-privileged user?"),
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
      case ex: Exception =>
        Dialogs.error(
          owner = Some(USBInstall.stage),
          title = Some("Missing requirements"),
          contentText = Some("Could not check the requirements were met."),
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

  override def stop(): Unit = {
    InstallSettings.pathTemp.delete(recursive = true)
    detachAppender(appender)
    SystemStreams.restore(systemStreams)
  }

}
