#!/system/bin/sh
# Test-only adaptation of pinned Alpha scripts/live_setup.sh (GPL-3.0).
# Run via an explicitly selected, disposable AVD using adb root and busybox sh.
# Does not edit SDK images, flash partitions, or remove existing backend data.
set -eu
[ "$(getprop ro.kernel.qemu)" = 1 ] || exit 10
[ "$(getprop ro.build.version.sdk)" = 34 ] || exit 11
[ "$(id -u)" = 0 ] || exit 12
cd /data/local/tmp/jwsk-alpha-live-validation
[ "$(pwd -P)" = /data/local/tmp/jwsk-alpha-live-validation ] || exit 13
[ ! -e /debug_ramdisk/magisk ] || exit 14
[ ! -e /data/adb/magisk ] || exit 15
dumpsys package cn.jiangwang.jwsk | grep -q 'pkgFlags=.*DEBUGGABLE' || exit 16
sha256sum -c SHA256SUMS || exit 17
for file in magisk magiskinit magiskboot magiskpolicy busybox stub.apk util_functions.sh; do
  [ -s "$file" ] || exit 18
done
chmod 755 magisk magiskinit magiskboot magiskpolicy busybox
export ASH_STANDALONE=1 BOOTMODE=true
. ./util_functions.sh

# All checks precede the temporary zygote stop. Always restart it on failure.
trap 'start' EXIT
stop
setprop sys.boot_completed 0
if ! grep -q ' /cache ' /proc/mounts; then
  mount -t tmpfs -o mode=0755 tmpfs /cache
fi
mount -t tmpfs -o mode=0755 jwsk-alpha-test /debug_ramdisk
MAGISKTMP=/debug_ramdisk
export MAGISKTMP
mkdir -p /data/adb/magisk /data/adb/modules /data/adb/post-fs-data.d /data/adb/service.d
chmod 700 /data/adb
for file in magisk magiskpolicy stub.apk; do
  cp -af "$file" "$MAGISKTMP/$file"
  cp -af "$file" "/data/adb/magisk/$file"
done
for file in magiskboot magiskinit busybox util_functions.sh; do
  cp -af "$file" "/data/adb/magisk/$file"
done
ln -s ./magisk "$MAGISKTMP/su"
ln -s ./magisk "$MAGISKTMP/resetprop"
ln -s ./magiskpolicy "$MAGISKTMP/supolicy"
mkdir -p "$MAGISKTMP/.magisk/device" "$MAGISKTMP/.magisk/worker"
mount -t tmpfs -o mode=0755 jwsk-alpha-worker "$MAGISKTMP/.magisk/worker"
mount -o private none "$MAGISKTMP/.magisk/worker"
touch "$MAGISKTMP/.magisk/config" "$MAGISKTMP/.magisk/live"
MAKEDEV=1 "$MAGISKTMP/magisk" --preinit-device
if [ -f /vendor/etc/selinux/precompiled_sepolicy ]; then
  ./magiskpolicy --load /vendor/etc/selinux/precompiled_sepolicy --live --magisk
elif [ -f /sepolicy ]; then
  ./magiskpolicy --load /sepolicy --live --magisk
else
  ./magiskpolicy --live --magisk
fi
"$MAGISKTMP/magisk" --post-fs-data
start
"$MAGISKTMP/magisk" --service
sleep 2
"$MAGISKTMP/magisk" --boot-complete
echo 'Dedicated AVD Alpha live setup completed; not a boot/physical-device validation'
