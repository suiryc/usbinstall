package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import javafx.stage.Window
import suiryc.scala.unused
import usbinstall.settings.{InstallSettings, Settings}


class ChooseProfileController
  extends Initializable
  with SettingsClearedListener
{

  @FXML
  protected var installationProfile: ComboBox[String] = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    val profileNames = Settings.profiles.keys.toList.sorted
    installationProfile.getItems.setAll(profileNames:_*)
    Option(Settings.core.profile()).filter(profileNames.contains).foreach { profileName =>
      installationProfile.getSelectionModel.select(profileName)
      setProfile(profileName)
    }
    ()
  }

  override def settingsCleared(source: Window): Unit = {
    installationProfile.getSelectionModel.select(-1)
    InstallSettings.profile.setValue(None)
  }

  def onInstallationProfile(@unused event: ActionEvent) {
    val profileName = installationProfile.getValue
    Settings.core.profile.update(profileName)
    setProfile(profileName)
  }

  protected def setProfile(profileName: String): Unit = {
    InstallSettings.profile.set(Option(profileName).flatMap(Settings.profiles.get))
  }

}
