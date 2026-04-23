#!/bin/bash
# Goddess Matrix Integration Script: Apache Configuration V1.4
# Feature Update: Pristine VirtualHost Generation (The 403 Annihilator)

HTML_DIR="$(pwd)/HTML"
CONF_FILE="/etc/apache2/sites-available/goddess.conf"

echo "SYSTEM> TARGETING DOCUMENT ROOT: $HTML_DIR"

if [ ! -d "$HTML_DIR" ]; then
  mkdir -p "$HTML_DIR"
fi

# 1. UNLOCK ALL PARENT DIRECTORIES FOR TRAVERSAL
echo "SYSTEM> FORCING PARENT DIRECTORY TRAVERSAL..."
CURRENT_DIR="$HTML_DIR"
while [ "$CURRENT_DIR" != "/" ]; do
    sudo chmod o+rx "$CURRENT_DIR"
    CURRENT_DIR=$(dirname "$CURRENT_DIR")
done

# 2. SECURE HTML FOLDER PERMISSIONS
echo "SYSTEM> SECURING HTML FOLDER PERMISSIONS..."
sudo chown -R $USER:www-data "$HTML_DIR"
sudo chmod -R 755 "$HTML_DIR"

# 3. GENERATE A PRISTINE VIRTUAL HOST (Bypassing default configs)
echo "SYSTEM> GENERATING DEDICATED GODDESS VIRTUAL HOST..."
echo "<VirtualHost *:80>
    ServerAdmin webmaster@localhost
    DocumentRoot \"$HTML_DIR\"

    <Directory \"$HTML_DIR\">
        Options Indexes FollowSymLinks
        AllowOverride All
        Require all granted
    </Directory>

    ErrorLog \${APACHE_LOG_DIR}/error.log
    CustomLog \${APACHE_LOG_DIR}/access.log combined
</VirtualHost>" | sudo tee "$CONF_FILE" > /dev/null

# 4. SWAP THE ACTIVE SITES
echo "SYSTEM> RE-ROUTING APACHE TRAFFIC..."
sudo a2dissite 000-default.conf -q
sudo a2ensite goddess.conf -q

# 5. GENERATE THE HUB
INDEX_FILE="$HTML_DIR/index.html"
echo "SYSTEM> GENERATING 'coonle' HUB..."
echo "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>coonle</title><style>body{background:#0a0a0c;color:#94a3b8;font-family:sans-serif;padding:3rem;}h1{color:#facd68;letter-spacing:0.2em;border-bottom:1px solid #1a1a20;padding-bottom:1rem;}a{color:#9d50bb;display:block;margin:0.8rem 0;text-decoration:none;font-size:1.2rem;}a:hover{color:#facd68;text-decoration:underline;}</style></head><body><h1>GODDESS_HUB: coonle</h1>" > "$INDEX_FILE"

for f in "$HTML_DIR"/*.html; do
    fname=$(basename "$f")
    if [ "$fname" != "index.html" ] && [ "$fname" != "*.html" ]; then
        echo "<a href='$fname'>[OPEN] $fname</a>" >> "$INDEX_FILE"
    fi
done

echo "</body></html>" >> "$INDEX_FILE"

# 6. PREPARE LOGS FOR NEURAL SENTRY
echo "SYSTEM> OPENING LOG STREAMS FOR NEURAL SENTRY..."
sudo touch /var/log/apache2/access.log
sudo chmod 644 /var/log/apache2/access.log

# 7. RESTART
sudo systemctl restart apache2

echo "SYSTEM> APACHE ONLINE: UNIVERSAL DEPLOYMENT RESOLVED."
exit 0