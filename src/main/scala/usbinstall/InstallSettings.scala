package usbinstall

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}


object InstallSettings {

  private var _device: Option[DeviceInfo] = None
  def device = _device
  def device_=(v: Option[DeviceInfo]) {
    _device = v
    _deviceProperty.update(v.orNull)
  }
  private val _deviceProperty = ObjectProperty(null:DeviceInfo)
  val deviceProperty: ReadOnlyObjectProperty[DeviceInfo] = _deviceProperty

}
