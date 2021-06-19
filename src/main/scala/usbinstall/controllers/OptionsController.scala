package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{Button, ComboBox}
import javafx.stage.{Stage, Window}
import scala.annotation.unused
import suiryc.scala.log.LogLevel
import suiryc.scala.settings.SettingsSnapshot
import usbinstall.settings.{ErrorAction, Settings}


class OptionsController extends Initializable {

  @FXML
  protected var logInstallThreshold: ComboBox[LogLevel.LevelValue] = _

  @FXML
  protected var componentInstallError: ComboBox[ErrorAction.Value] = _

  @FXML
  protected var clearButton: Button = _

  @FXML
  protected var cancelButton: Button = _

  protected val snapshot = new SettingsSnapshot()

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {
    logInstallThreshold.getItems.setAll(LogLevel.levels.toList:_*)
    componentInstallError.getItems.setAll(ErrorAction.values.toList:_*)

    Settings.core.snapshot(snapshot)

    update()
  }

  protected def updateCancelButton(): Unit = {
    cancelButton.setDisable(!snapshot.changed())
  }

  protected def update(): Unit = {
    logInstallThreshold.getSelectionModel.select(Settings.core.logInstallThreshold.get)
    componentInstallError.getSelectionModel.select(Settings.core.componentInstallError.get)
    updateCancelButton()
  }

  def onLogInstallThreshold(@unused event: ActionEvent): Unit = {
    Settings.core.logInstallThreshold.set(logInstallThreshold.getValue)
    updateCancelButton()
  }

  def onComponentInstallError(@unused event: ActionEvent): Unit = {
    Settings.core.componentInstallError.set(componentInstallError.getValue)
    updateCancelButton()
  }

  def onReset(@unused event: ActionEvent): Unit = {
    Settings.core.logInstallThreshold.reset()
    Settings.core.componentInstallError.reset()
    // Note: we need to update the pane; alternatively we could make persistent
    // properties out of those persistent settings and update the control upon
    // value changing.
    update()
  }

  def onCancel(@unused event: ActionEvent): Unit = {
    snapshot.reset()
    update()
  }

  def onDone(@unused event: ActionEvent): Unit = {
    window.asInstanceOf[Stage].close()
  }

  protected def window: Window =
    logInstallThreshold.getScene.getWindow

}
