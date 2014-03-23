package usbinstall.os

import scala.language.postfixOps
import suiryc.scala.io.FilesEx
import suiryc.scala.io.PathFinder._
import suiryc.scala.io.RichFile._
import java.nio.file.Paths
import java.nio.file.Files


class GPartedLiveInstall(override val settings: OSSettings)
  extends OSInstall(settings, true)
{

  override def install(isoMount: Option[PartitionMount], partMount: Option[PartitionMount]): Unit = {
    val source = isoMount.get.to
    val sourceRoot = source.toAbsolutePath
    val targetRoot = partMount.get.to.toAbsolutePath
    val finder = source ***

    (for (file <- finder.get) yield file).toList.sortBy(_.getPath) foreach { file =>
      val pathFile = file.toAbsolutePath
      val pathRelative = sourceRoot.relativize(pathFile)
      val pathTarget = targetRoot.resolve(pathRelative)
      if (pathTarget.exists)
        logger.warn(s"Source[$sourceRoot] path[$pathRelative] already processed, skipping")
      else
        FilesEx.copy(
          sourceRoot,
          pathRelative,
          targetRoot,
          followLinks = false
        )
    }

    val syslinuxFile = Paths.get(targetRoot.toString(), "syslinux", settings.syslinuxFile)
    if (!syslinuxFile.exists) {
      val syslinuxCfg = Paths.get(targetRoot.toString(), "syslinux", "syslinux.cfg")
      val isolinuxCfg = Paths.get(targetRoot.toString(), "syslinux", "isolinux.cfg")
      if (syslinuxCfg.exists) {
        debug(s"Rename source[$syslinuxCfg] target[$syslinuxFile]")
        Files.move(syslinuxCfg, syslinuxFile)
      }
      else if (isolinuxCfg.exists) {
        syslinuxFile.getParent().delete(true)
        debug(s"Rename source[${isolinuxCfg.getParent()}] target[${syslinuxFile.getParent()}]")
        Files.move(isolinuxCfg, isolinuxCfg.getParent().relativize(syslinuxFile.getFileName()))
        Files.move(isolinuxCfg.getParent(), syslinuxFile.getParent())
      }
    }
    /* XXX - handle errors */
    /* XXX - can a 'copy' fail ? */
//# Performs GParted installation
//install_gparted_live()
//{
//    local _flavor=$1
//    local syslinuxFile=${install_component_syslinux_file[${_flavor}]}
//
//    # Copy ISO content
//    updateStatus dialogStatus " * Copy ISO content"
//
//    (
//        cd "${dirISOMount}" \
//            && cp -arv . "${dirPartMount}"/ \
//            && sync
//    )
//    checkReturnCode "Failed to copy ISO content" 2
//
//    # Note: the following could be considered part of the setup process, but we
//    # don't need to separate it from the install step
//    if [ ! -e "${dirPartMount}/syslinux/${syslinuxFile}" ]
//    then
//        if [ -e "${dirPartMount}/syslinux/syslinux.cfg" ]
//        then
//            updateStatus dialogStatus " * Rename syslinux"
//
//            mv "${dirPartMount}"/syslinux/syslinux.cfg "${dirPartMount}/syslinux/${syslinuxFile}"
//            checkReturnCode "Failed to rename syslinux" 2
//        elif [ -f "${dirPartMount}/isolinux/isolinux.cfg" ]
//        then
//            updateStatus dialogStatus " * Rename isolinux to syslinux"
//
//            [ -d "${dirPartMount}"/syslinux ] && rm -rf "${dirPartMount}"/syslinux
//            mv "${dirPartMount}"/isolinux/isolinux.cfg "${dirPartMount}/isolinux/${syslinuxFile}" \
//                && mv "${dirPartMount}"/isolinux "${dirPartMount}"/syslinux
//            checkReturnCode "Failed to rename isolinux to syslinux" 2
//        fi
//    fi
//}
  }

}
