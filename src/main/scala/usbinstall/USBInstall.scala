package usbinstall

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import org.slf4j.LoggerFactory
import scalafx.application.JFXApp
import suiryc.scala.io.RichFile._
import usbinstall.settings.InstallSettings
import usbinstall.util.ProxyAppender


object USBInstall extends JFXApp {

  val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
  val proxyAppender = new ProxyAppender()
  proxyAppender.setContext(lc)
  proxyAppender.start()

  val logger = LoggerFactory.getLogger("usbinstall").asInstanceOf[Logger]
  logger.addAppender(proxyAppender)

  usbinstall.os.SyslinuxInstall.get(4)

  stage = Stages.chooseDevice

  override def stopApp() {
    InstallSettings.pathTemp.delete(true)
  }

}
