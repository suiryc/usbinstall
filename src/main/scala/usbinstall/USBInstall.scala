package usbinstall

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import org.slf4j.LoggerFactory
import scalafx.application.JFXApp
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.layout.VBox
import scalafx.stage.Stage
import suiryc.scala.io.RichFile._
import usbinstall.settings.InstallSettings
import usbinstall.util.{LogArea, ProxyAppender}


object USBInstall extends JFXApp {

  /* XXX - close other windows when main window is closed */
  /* XXX - have debugArea height follow parent window resizing */
  /* XXX - debugArea has scrollbar if resizing down, and scrollbar disappears when resizing up */

  val debugArea = new LogArea
  val debugPane = new VBox {
    padding = Insets(5)
    spacing = 5
    alignment = Pos.TOP_CENTER
    maxHeight = Double.MaxValue
    content = List(debugArea)
  }
  val debugScene = new Scene {
    root = debugPane
  }
  val debugStage = new Stage {
    scene = debugScene
  }
  val debugAreaWriter = new usbinstall.util.LineWriter {
    override def write(line: String) =
      suiryc.scala.javafx.concurrent.JFXSystem.schedule {
        debugArea.write(line)
      }
  }

  val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
  val proxyAppender = new ProxyAppender(List(debugAreaWriter))
  proxyAppender.setContext(lc)
  proxyAppender.start()

  List("usbinstall", "suiryc") foreach { name =>
    val logger = LoggerFactory.getLogger(name).asInstanceOf[Logger]
    logger.addAppender(proxyAppender)
  }

  stage = Stages.chooseDevice

  debugStage.show
  suiryc.scala.javafx.concurrent.JFXSystem.schedule {
    debugArea.write("Test")
  }

  override def stopApp() {
    InstallSettings.pathTemp.delete(true)
  }

}
