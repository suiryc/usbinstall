#!/bin/bash

set -o nounset

idGroup=
idUser=

# Restart program as root giving current user/group as parameter
if [ $(id -u) -ne 0 ]
then
    sbt assembly || exit $?
    exec sudo $0 "##sudoed##" "$(id -un)" "$(id -gn)" "$@"
fi

if [ $# -gt 0 ] && [ "$1" = "##sudoed##" ]
then
    shift
    idUser=$1
    shift
    idGroup=$1
    shift
fi


# Under Arch, HOME is set according to running user. Which means it is /root
# even upon sudo (unlike ubuntu which keeps the original user HOME).
eval userHome=~${idUser:-root}
export HOME=${userHome}

# Make sure we have access to ISOs
$(mount | grep -q win_d) || mount /mnt/win_d

java -Dfile.encoding=UTF-8 -classpath src/main/resources:target/scala-2.13/usbinstall-assembly-0.0.3-SNAPSHOT.jar usbinstall.USBInstall

