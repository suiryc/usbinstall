package usbinstall.os


class GPartedLiveInstall(override val settings: OSSettings)
  extends OSInstall(settings)
{

  override def install: Unit = {
    /*
# Performs GParted installation
install_gparted_live()
{
    local _flavor=$1
    local syslinuxFile=${install_component_syslinux_file[${_flavor}]}

    # Copy ISO content
    updateStatus dialogStatus " * Copy ISO content"

    (
        cd "${dirISOMount}" \
            && cp -arv . "${dirPartMount}"/ \
            && sync
    )
    checkReturnCode "Failed to copy ISO content" 2

    # Note: the following could be considered part of the setup process, but we
    # don't need to separate it from the install step
    if [ ! -e "${dirPartMount}/syslinux/${syslinuxFile}" ]
    then
        if [ -e "${dirPartMount}/syslinux/syslinux.cfg" ]
        then
            updateStatus dialogStatus " * Rename syslinux"

            mv "${dirPartMount}"/syslinux/syslinux.cfg "${dirPartMount}/syslinux/${syslinuxFile}"
            checkReturnCode "Failed to rename syslinux" 2
        elif [ -f "${dirPartMount}/isolinux/isolinux.cfg" ]
        then
            updateStatus dialogStatus " * Rename isolinux to syslinux"

            [ -d "${dirPartMount}"/syslinux ] && rm -rf "${dirPartMount}"/syslinux
            mv "${dirPartMount}"/isolinux/isolinux.cfg "${dirPartMount}/isolinux/${syslinuxFile}" \
                && mv "${dirPartMount}"/isolinux "${dirPartMount}"/syslinux
            checkReturnCode "Failed to rename isolinux to syslinux" 2
        fi
    fi
}
     */
  }

}
