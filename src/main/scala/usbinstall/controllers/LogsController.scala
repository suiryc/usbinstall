package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.scene.control.TextArea
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import scala.annotation.unused
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.log.LogLevel
import usbinstall.settings.Settings


class LogsController extends Initializable {

  @FXML
  protected[usbinstall] var logThreshold: ComboBox[LogLevel.LevelValue] = _

  @FXML
  protected var textArea: TextArea = _

  protected[usbinstall] var logArea: LogArea = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {
    logArea = LogArea(textArea)
    logThreshold.getItems.setAll(LogLevel.levels.toList:_*)

    update()
  }

  def update(): Unit = {
    logThreshold.getSelectionModel.select(Settings.core.logDebugThreshold.get)
  }

  def onLogThreshold(@unused event: ActionEvent): Unit = {
    Settings.core.logDebugThreshold.set(logThreshold.getValue)
  }

  def onClear(@unused event: ActionEvent): Unit = {
    logArea.clear()
  }

}
