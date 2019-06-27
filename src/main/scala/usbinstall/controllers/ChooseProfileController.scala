package usbinstall.controllers

import java.net.URL
import java.util.ResourceBundle
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.ComboBox
import suiryc.scala.unused
import usbinstall.settings.{InstallSettings, Settings}


class ChooseProfileController
  extends Initializable
{

  @FXML
  protected var installationProfile: ComboBox[String] = _

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {
    val profileNames = Settings.profiles.keys.toList.sorted
    installationProfile.getItems.setAll(profileNames:_*)
    Settings.core.profile.opt.filter(profileNames.contains).foreach { profileName =>
      installationProfile.getSelectionModel.select(profileName)
      setProfile(profileName)
    }
    ()
  }

  def onInstallationProfile(@unused event: ActionEvent): Unit = {
    val profileName = installationProfile.getValue
    Settings.core.profile.set(profileName)
    setProfile(profileName)
  }

  protected def setProfile(profileName: String): Unit = {
    InstallSettings.profile.set(Option(profileName).flatMap(Settings.profiles.get))
  }

}
