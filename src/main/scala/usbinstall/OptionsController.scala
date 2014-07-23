package usbinstall

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import javafx.stage.Stage
import suiryc.scala.misc.MessageLevel
import usbinstall.settings.{ErrorAction, Settings}


class OptionsController extends Initializable {

  @FXML
  protected var logInstallThreshold: ComboBox[MessageLevel.Value] = _

  @FXML
  protected var componentInstallError: ComboBox[ErrorAction.Value] = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    logInstallThreshold.getItems().setAll(MessageLevel.values.toList:_*)
    componentInstallError.getItems().setAll(ErrorAction.values.toList:_*)

    update()
  }

  def update() {
    logInstallThreshold.getSelectionModel().select(Settings.core.logInstallThreshold())
    componentInstallError.getSelectionModel().select(Settings.core.componentInstallError)
  }

  def onLogInstallThreshold(event: ActionEvent) {
    Settings.core.logInstallThreshold.update(logInstallThreshold.getValue())
  }

  def onComponentInstallError(event: ActionEvent) {
    Settings.core.componentInstallError.update(componentInstallError.getValue())
  }

  def onReset(event: ActionEvent) {
    Settings.core.logDebugThreshold.update(Settings.default.logDebugThreshold)
    Settings.core.logInstallThreshold.update(Settings.default.logInstallThreshold)
    Settings.core.componentInstallError.update(Settings.default.componentInstallError)
    /* Note: we need to update the pane; alternatively we could make a
     * PersistentProperty out of this PersistentSetting and update the control
     * upon value changing.
     */
    update()
  }

  def onClear(event: ActionEvent) {
    Settings.core.prefs.removeNode()
    Settings.core.reset()
  }

  def onDone(event: ActionEvent) {
    logInstallThreshold.getScene().getWindow().asInstanceOf[Stage].close()
  }

}
