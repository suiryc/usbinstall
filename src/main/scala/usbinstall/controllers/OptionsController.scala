package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{Button, ButtonType, ComboBox}
import javafx.stage.{Stage, Window}
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.log.LogLevel
import suiryc.scala.settings.SettingsSnapshot
import usbinstall.Stages
import usbinstall.settings.{ErrorAction, Settings}


class OptionsController extends Initializable {

  @FXML
  protected var logInstallThreshold: ComboBox[LogLevel.Value] = _

  @FXML
  protected var componentInstallError: ComboBox[ErrorAction.Value] = _

  @FXML
  protected var clearButton: Button = _

  @FXML
  protected var cancelButton: Button = _

  protected var listener: SettingsClearedListener = _

  protected val snapshot = new SettingsSnapshot()

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    logInstallThreshold.getItems.setAll(LogLevel.values.toList:_*)
    componentInstallError.getItems.setAll(ErrorAction.values.toList:_*)

    Settings.core.snapshot(snapshot)

    update()
  }

  def setListener(listener: SettingsClearedListener) {
    this.listener = listener

    //Note: tooltip are not shown for disabled controls
    clearButton.setDisable(!listener.canClearSettings)
  }

  protected def updateCancelButton() {
    cancelButton.setDisable(!snapshot.changed())
  }

  protected def update() {
    logInstallThreshold.getSelectionModel.select(Settings.core.logInstallThreshold())
    componentInstallError.getSelectionModel.select(Settings.core.componentInstallError())
    updateCancelButton()
  }

  def onLogInstallThreshold(event: ActionEvent) {
    Settings.core.logInstallThreshold.update(logInstallThreshold.getValue)
    updateCancelButton()
  }

  def onComponentInstallError(event: ActionEvent) {
    Settings.core.componentInstallError.update(componentInstallError.getValue)
    updateCancelButton()
  }

  def onReset(event: ActionEvent) {
    Settings.core.logInstallThreshold.resetDefault()
    Settings.core.componentInstallError.resetDefault()
    // Note: we need to update the pane; alternatively we could make persistent
    // properties out of those persistent settings and update the control upon
    // value changing.
    update()
  }

  def onClear(event: ActionEvent) {
    val action = Dialogs.confirmation(
      owner = Some(window),
      title = Some("Clear settings"),
      headerText = Some("Are you sure?"),
      contentText = Some("You are about to clear all settings and get back to initial or default values"),
      buttons = Stages.DialogButtons.Ok_Cancel
    )

    if (action.contains(ButtonType.OK)) {
      Settings.core.prefs.removeNode()
      Settings.core.reset()
      update()
      Option(listener).foreach(_.settingsCleared(window))
    }
  }

  def onCancel(event: ActionEvent) {
    snapshot.reset()
    update()
  }

  def onDone(event: ActionEvent) {
    window.asInstanceOf[Stage].close()
  }

  protected def window =
    logInstallThreshold.getScene.getWindow

}


trait SettingsClearedListener {

  def canClearSettings = true

  def settingsCleared(source: Window): Unit

}
