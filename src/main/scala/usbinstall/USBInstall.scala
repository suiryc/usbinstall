package usbinstall

import ch.qos.logback.classic.{Logger, LoggerContext}
import org.slf4j.LoggerFactory
import scalafx.application.JFXApp
import suiryc.scala.io.RichFile._
import usbinstall.settings.InstallSettings
import usbinstall.util.ProxyAppender
import usbinstall.util.DebugStage


object USBInstall extends JFXApp {

  val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
  val proxyAppender = new ProxyAppender(List(DebugStage.areaWriter, DebugStage.listViewWriter))
  proxyAppender.setContext(lc)
  proxyAppender.start()

  List("usbinstall", "suiryc") foreach { name =>
    val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
    logger.addAppender(proxyAppender)
  }

  DebugStage.show()

  Stages.chooseDevice()

  override def stopApp() {
    InstallSettings.pathTemp.delete(true)
  }

}
