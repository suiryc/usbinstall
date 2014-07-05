package usbinstall

import java.net.URL
import java.util.ResourceBundle
import java.util.prefs.Preferences
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.settings.PersistentSetting._
import usbinstall.settings.{ErrorAction, Settings}


class OptionsController extends Initializable {

  @FXML
  protected var componentInstallError: ComboBox[ErrorAction.Value] = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    componentInstallError.getItems().setAll(ErrorAction.values.toList:_*)
    componentInstallError.setOnAction {
      onComponentInstallError _
    }

    update()
  }

  def update() {
    componentInstallError.getSelectionModel().select(Settings.core.componentInstallError)
  }

  def onComponentInstallError(event: ActionEvent) {
    Settings.core.componentInstallError.update(componentInstallError.getValue())
  }

  def onReset(event: ActionEvent) {
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

}
