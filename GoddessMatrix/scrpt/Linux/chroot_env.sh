#!/bin/bash
CHROOT_DIR="/home/root/AI/osDev"
echo 'SYSTEM> MOUNTING CHROOT VOLUMES...'
sudo mount -t proc /proc "$CHROOT_DIR/proc"
sudo mount -t sysfs /sys "$CHROOT_DIR/sys"
sudo mount -o bind /dev "$CHROOT_DIR/dev"
sudo mount -o bind /dev/pts "$CHROOT_DIR/dev/pts"
echo 'SYSTEM> ENTERING CHROOT ENVIRONMENT...'
sudo chroot "$CHROOT_DIR" /bin/bash
echo 'SYSTEM> CHROOT EXITED. UNMOUNTING VOLUMES...'
sudo umount "$CHROOT_DIR/dev/pts"
sudo umount "$CHROOT_DIR/dev"
sudo umount "$CHROOT_DIR/sys"
sudo umount "$CHROOT_DIR/proc"
echo 'SYSTEM> CLEANUP COMPLETE. CLOSING TERMINAL.'
sleep 2
