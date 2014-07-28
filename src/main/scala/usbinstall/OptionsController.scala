package usbinstall

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import javafx.stage.Stage
import org.controlsfx.dialog.Dialog
import suiryc.scala.log.LogLevel
import usbinstall.settings.{ErrorAction, Settings}


class OptionsController extends Initializable {

  @FXML
  protected var logInstallThreshold: ComboBox[LogLevel.Value] = _

  @FXML
  protected var componentInstallError: ComboBox[ErrorAction.Value] = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    logInstallThreshold.getItems().setAll(LogLevel.values.toList:_*)
    componentInstallError.getItems().setAll(ErrorAction.values.toList:_*)

    update()
  }

  def update() {
    logInstallThreshold.getSelectionModel().select(Settings.core.logInstallThreshold())
    componentInstallError.getSelectionModel().select(Settings.core.componentInstallError())
  }

  def onLogInstallThreshold(event: ActionEvent) {
    Settings.core.logInstallThreshold.update(logInstallThreshold.getValue())
  }

  def onComponentInstallError(event: ActionEvent) {
    Settings.core.componentInstallError.update(componentInstallError.getValue())
  }

  def onReset(event: ActionEvent) {
    Settings.core.logInstallThreshold.resetDefault()
    Settings.core.componentInstallError.resetDefault()
    /* Note: we need to update the pane; alternatively we could make persistent
     * properties out of those persistent settings and update the control upon
     * value changing.
     */
    update()
  }

  def onClear(event: ActionEvent) {
    val action = Stages.confirmStage(Some(window), "Clear settings", Some("Are you sure?"),
      "You are about to clear all settings and get back to initial or default values",
      Stages.DialogActions.Ok_Cancel)

    if (action == Dialog.Actions.OK) {
      Settings.core.prefs.removeNode()
      Settings.core.reset()
      update()
    }
  }

  def onDone(event: ActionEvent) {
    window.asInstanceOf[Stage].close()
  }

  protected def window =
    logInstallThreshold.getScene().getWindow()

}
