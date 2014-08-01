package usbinstall


case class InstallationException(msg: String = null, cause: Throwable = null, notified: Boolean = false)
extends Exception(msg, cause)
