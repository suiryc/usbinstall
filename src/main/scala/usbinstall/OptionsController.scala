package usbinstall

import java.util.prefs.Preferences
import scalafx.event.ActionEvent
import scalafx.scene.control.ComboBox
import scalafxml.core.macros.sfxml
import suiryc.scala.settings.PersistentSetting._
import usbinstall.settings.{ErrorAction, Settings}


@sfxml
class OptionsController(
  private val componentInstallError: ComboBox[ErrorAction.Value]
) {

  import scalafx.Includes._

  /* Note: 'onAction' handler *MUST* be added after setting the default value,
   * to prevent initialization failure (changing value triggers callback on
   * object being constructed).
   */
  componentInstallError.items.get().setAll(ErrorAction.values.toList:_*)
  componentInstallError.onAction =
    onComponentInstallError _

  update()

  def update() {
    componentInstallError.selectionModel().select(Settings.core.componentInstallError)
  }

  def onComponentInstallError(event: ActionEvent) {
    Settings.core.componentInstallError.update(componentInstallError.value())
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
