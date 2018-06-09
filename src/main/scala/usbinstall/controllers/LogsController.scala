package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.scene.control.TextArea
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.log.LogLevel
import usbinstall.settings.Settings


class LogsController extends Initializable {

  @FXML
  protected[usbinstall] var logThreshold: ComboBox[LogLevel.Value] = _

  @FXML
  protected var textArea: TextArea = _

  protected[usbinstall] var logArea: LogArea = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    logArea = LogArea(textArea)
    logThreshold.getItems.setAll(LogLevel.values.toList:_*)

    update()
  }

  def update() {
    logThreshold.getSelectionModel.select(Settings.core.logDebugThreshold())
  }

  def onLogThreshold(@deprecated("unused","") event: ActionEvent) {
    Settings.core.logDebugThreshold.update(logThreshold.getValue)
  }

  def onClear(@deprecated("unused","") event: ActionEvent) {
    logArea.clear()
  }

}
