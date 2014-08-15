package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.CheckBox
import javafx.stage.Stage
import usbinstall.settings.ErrorAction


class InstallFailureController extends Initializable {

  @FXML
  protected var applyDefault: CheckBox = _

  protected var action = ErrorAction.Ask

  def getAction = action

  def getAsDefault = applyDefault.isSelected

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
  }

  def onStop(event: ActionEvent) {
    action = ErrorAction.Stop
    window.asInstanceOf[Stage].close()
  }

  def onContinue(event: ActionEvent) {
    action = ErrorAction.Skip
    window.asInstanceOf[Stage].close()
  }

  protected def window =
    applyDefault.getScene.getWindow

}
