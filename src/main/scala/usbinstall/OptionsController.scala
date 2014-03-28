package usbinstall

import scalafx.event.ActionEvent
import scalafx.scene.control.ComboBox
import scalafxml.core.macros.sfxml
import usbinstall.settings.Settings
import usbinstall.settings.PersistentSetting._


@sfxml
class OptionsController(
  private val componentInstallError: ComboBox[String]
) {

  import scalafx.Includes._

  /* Note: 'onAction' handler *MUST* be added after setting the default value,
   * to prevent initialization failure (changing value triggers callback on
   * object being constructed).
   */
  componentInstallError.items.get().setAll("Ask", "Stop", "Skip")
  componentInstallError.selectionModel().select(Settings.core.componentInstallError)
  componentInstallError.onAction =
    onComponentInstallError _

  def onComponentInstallError(event: ActionEvent) {
    Settings.core.componentInstallError.update(componentInstallError.value())
  }

}
