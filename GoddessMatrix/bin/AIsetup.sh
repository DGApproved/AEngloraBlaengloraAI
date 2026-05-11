#!/bin/bash
echo "installing python3 with apt"
sudo apt-get install python3
echo "installing python dependencies"
sudo pip install llama-cpp-python --break-system-packages
sudo pip install PyMuPDF --break-system-packages
sudo pip install ebooklib --break-system-packages
sudo pip install beautifulsoup4 --break-system-packages
sudo pip install py-cpuinfo --break-system-packages
sudo pip install psutil --break-system-packages
sudo touch dgapi/datas/dictionary.txt
cd intscripts
sudo python3 GoddessAPI.py
