#!/bin/bash

echo "setting to current dir"
cd $PWD
echo "changing to ../HTML dir"
cd ../HTML
echo "changing to HTML dir if prev line failed"
cd HTML
echo "setting ownersip"
sudo chown root:www-data *
echo "setting permissions"
sudo chmod -R 755 *
