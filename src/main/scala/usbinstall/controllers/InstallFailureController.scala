package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.CheckBox
import javafx.stage.Stage
import suiryc.scala.unused
import usbinstall.settings.ErrorAction


class InstallFailureController extends Initializable {

  @FXML
  protected var applyDefault: CheckBox = _

  private var action = ErrorAction.Ask

  def getAction: ErrorAction.Value = action

  def getAsDefault: Boolean = applyDefault.isSelected

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {
  }

  def onStop(@unused event: ActionEvent): Unit = {
    action = ErrorAction.Stop
    window.asInstanceOf[Stage].close()
  }

  def onContinue(@unused event: ActionEvent): Unit = {
    action = ErrorAction.Skip
    window.asInstanceOf[Stage].close()
  }

  private def window =
    applyDefault.getScene.getWindow

}
