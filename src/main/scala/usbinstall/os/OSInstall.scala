package usbinstall.os

import usbinstall.settings.InstallSettings


class OSInstall(val settings: OSSettings) {

  /* XXX - useful ? */
  def prepare: Unit = {}

  /* XXX - merge preInstall+install, or install+postInstall ? */
  def preInstall: Unit = {}

  def install: Unit = {}

  def postInstall: Unit = {}

}

object OSInstall {

  def apply(settings: OSSettings): OSInstall = settings.kind match {
    case OSKind.GPartedLive =>
      new GPartedLiveInstall(settings)

      /* XXX */
  }

  /* XXX - useful ? */
  def prepare(os: OSInstall): Unit =
    os.prepare

  private def mountAndDo(os: OSInstall, todo: => Unit): Unit = {
    val iso = os.settings.iso() map { pathISO =>
      new PartitionMount(pathISO, InstallSettings.pathMountISO)
    }
    val part = os.settings.partition() map { partition =>
      new PartitionMount(partition.dev, InstallSettings.pathMountPartition)
    }

    try {
      iso foreach { _.mount }
      part foreach { _.mount }
      todo
    }
    /* XXX - catch and log */
    finally {
      /* XXX - sync ? */
      /* XXX - try/catch ? */
      part foreach { _.umount }
      iso foreach { _.umount }
    }
  }

  def preInstall(os: OSInstall): Unit = {
    /* XXX - prepare syslinux (find files) */
    /* XXX - prepare partition (format, set type, set label) */
    mountAndDo(os, os.preInstall)
  }

  def install(os: OSInstall): Unit = {
    mountAndDo(os, os.install)
  }

  def postInstall(os: OSInstall): Unit = {
    mountAndDo(os, os.postInstall)
  }

}
