package usbinstall.settings

import suiryc.scala.misc.EnumerationEx


abstract class PersistentSetting[T]
{

  protected val path: String

  def apply(): T

  def update(v: T): Unit

}

class PersistentStringSetting(protected val path: String)(implicit settings: Settings) extends PersistentSetting[String]
{

  def apply(): String =
    /* XXX - more efficient way to check whether path exists and only use 'config' if not ? */
    settings.prefs.get(path, settings.config.getString(path))

  def update(v: String) =
    settings.prefs.put(path, v)

}

class PersistentEnumerationExSetting[T <: EnumerationEx](protected val path: String)(implicit settings: Settings, enum: T) extends PersistentSetting[T#Value]
{

  def apply(): T#Value =
    enum(settings.prefs.get(path, settings.config.getString(path)))

  def update(v: T#Value) =
    settings.prefs.put(path, v.toString)

}

object PersistentSetting {

  import scala.language.implicitConversions

  implicit def toValue[T](p :PersistentSetting[T]): T = p()

  implicit def forString(path: String)(implicit settings: Settings): PersistentSetting[String] =
    new PersistentStringSetting(path)

  /* Note: does not work as implicit */
  def forEnumerationEx[T <: EnumerationEx](path: String)(implicit settings: Settings, enum: T): PersistentSetting[T#Value] =
    new PersistentEnumerationExSetting[T](path)

  def apply[T](p: PersistentSetting[T])(implicit settings: Settings) = p

}
