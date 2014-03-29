package usbinstall

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
  componentInstallError.selectionModel().select(Settings.core.componentInstallError)
  componentInstallError.onAction =
    onComponentInstallError _

  def onComponentInstallError(event: ActionEvent) {
    Settings.core.componentInstallError.update(componentInstallError.value())
  }

}
