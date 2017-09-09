package usbinstall.os

import usbinstall.InstallUI


class GenericInstall(
  override val settings: OSSettings,
  override val ui: InstallUI,
  override val checkCancelled: () => Unit
) extends OSInstall(settings, ui, checkCancelled)
