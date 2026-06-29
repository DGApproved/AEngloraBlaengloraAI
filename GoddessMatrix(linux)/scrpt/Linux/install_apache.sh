#!/bin/bash
# Goddess Matrix Integration Script: Apache Install
echo "SYSTEM> INITIATING APACHE INSTALLATION PROTOCOL..."

# Check for root privileges (required for apt)
if [ "$EUID" -ne 0 ]; then
  echo "SYSTEM_ERR> PLEASE RUN WITH ROOT/SUDO PRIVILEGES."
  exit 1
fi

echo "SYSTEM> UPDATING PACKAGE LISTS..."
apt-get update -qq

echo "SYSTEM> INSTALLING APACHE2..."
apt-get install apache2 -y -qq

echo "SYSTEM> ENABLING APACHE2 SERVICE..."
systemctl enable apache2
systemctl start apache2

echo "SYSTEM> APACHE WEBSERVER ONLINE."
exit 0
