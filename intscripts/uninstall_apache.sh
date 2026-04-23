#!/bin/bash
# Goddess Matrix Integration Script: Apache Uninstall
echo "SYSTEM> INITIATING APACHE PURGE PROTOCOL..."

if [ "$EUID" -ne 0 ]; then
  echo "SYSTEM_ERR> PLEASE RUN WITH ROOT/SUDO PRIVILEGES."
  exit 1
fi

echo "SYSTEM> STOPPING APACHE2 SERVICE..."
systemctl stop apache2

echo "SYSTEM> REMOVING APACHE2 PACKAGES..."
apt-get remove --purge apache2 apache2-utils apache2-bin apache2.2-common -y -qq
apt-get autoremove -y -qq

echo "SYSTEM> CLEANING REMAINING DIRECTORIES..."
rm -rf /etc/apache2

echo "SYSTEM> APACHE PURGE COMPLETE."
exit 0