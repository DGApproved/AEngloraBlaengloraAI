#!/bin/bash
echo "Installing python3 and pip with apt..."
sudo apt-get update
sudo apt-get install -y python3 python3-pip libpcre3

echo "Installing python dependencies..."
sudo pip install llama-cpp-python --break-system-packages
sudo pip install PyMuPDF --break-system-packages
sudo pip install ebooklib --break-system-packages
sudo pip install beautifulsoup4 --break-system-packages
sudo pip install py-cpuinfo --break-system-packages
sudo pip install psutil --break-system-packages

echo "Simulating Java Matrix Boot Sequence..."
# 1. Create a fake tenant folder just for this test
mkdir -p test_tenant_node
cd test_tenant_node

# 2. Launch the script while standing INSIDE the tenant folder
sudo python3 ../GoddessAPI.py
cd ..
sudo cp test_tenant_node/dgapi/system/hardware.cache ../../fn/fn*/dgapi/system/hardware.cache
