package usbinstall.os


class OSInstall(val settings: OSSettings) {

  /* XXX - useful ? */
  def prepare: Unit = {}

  def preInstall: Unit = {}

  def install: Unit = {}

  def postInstall: Unit = {}

}

object OSInstall {

  def apply(kind: OSKind.Value): OSInstall = kind match {
    case OSKind.GPartedLive =>
      null
  }

}
