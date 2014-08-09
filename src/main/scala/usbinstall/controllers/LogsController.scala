package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import suiryc.scala.javafx.scene.control.LogArea
import suiryc.scala.log.LogLevel
import usbinstall.settings.Settings


class LogsController extends Initializable {

  /* XXX - drop 'LogArea' in favor of wrapper class/functions to bind extra features to a plain 'TextArea' ? */

  @FXML
  protected[usbinstall] var logThreshold: ComboBox[LogLevel.Value] = _

  @FXML
  protected[usbinstall] var logArea: LogArea = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    logThreshold.getItems().setAll(LogLevel.values.toList:_*)

    update()
  }

  def update() {
    logThreshold.getSelectionModel().select(Settings.core.logDebugThreshold())
  }

  def onLogThreshold(event: ActionEvent) {
    Settings.core.logDebugThreshold.update(logThreshold.getValue())
  }


}
