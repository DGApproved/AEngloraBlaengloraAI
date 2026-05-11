#!/bin/bash
# Goddess Matrix Automated Debian Packager
set -e

PKG_NAME="goddess-matrix"
VERSION="1.1"
ARCH="all"
STAGING_DIR="${PKG_NAME}_${VERSION}_${ARCH}"

echo "SYSTEM> INITIATING DEBIAN BUILD SEQUENCE..."

# 1. Verification
if [ ! -f "GoddessMatrix.jar" ]; then
    echo "SYSTEM_ERR> GoddessMatrix.jar not found! Run build_matrix.sh first."
    exit 1
fi

# 2. Forge Staging Architecture
echo "SYSTEM> CREATING STAGING DIRECTORIES..."
mkdir -p "${STAGING_DIR}/DEBIAN"
mkdir -p "${STAGING_DIR}/opt/goddess"

# 3. Compile the Payload
echo "SYSTEM> ZIPPING PAYLOAD ASSETS..."
# Dynamically include folders if they exist
ASSETS="GoddessMatrix.jar"
[ -d "intscripts" ] && ASSETS="$ASSETS intscripts"
[ -d "HTML" ] && ASSETS="$ASSETS HTML"

zip -r payload.zip $ASSETS > /dev/null
mv payload.zip "${STAGING_DIR}/opt/goddess/"

# 4. Generate the Control File
echo "SYSTEM> WRITING PACKAGE METADATA..."
cat <<EOF > "${STAGING_DIR}/DEBIAN/control"
Package: $PKG_NAME
Version: $VERSION
Architecture: $ARCH
Maintainer: Derek <derek@localhost>
Depends: default-jre, unzip, bash
Description: The Goddess Input Matrix Orchestrator
 A unified multi-session terminal, sandbox runtime, and AI orchestrator.
EOF

# 5. Generate the Post-Installation Script
echo "SYSTEM> WRITING POST-INSTALL ENGINE..."
# Using quoted 'EOF' so bash doesn't evaluate the inner variables
cat <<'EOF' > "${STAGING_DIR}/DEBIAN/postinst"
#!/bin/bash
set -e

echo "SYSTEM> INITIALIZING GODDESS MATRIX DEPLOYMENT..."

# Forge the Root Architecture
mkdir -p /home/root/AI

# Extract the Payload
unzip -o /opt/goddess/payload.zip -d /home/root/AI/ > /dev/null

# Secure Permissions
chmod -R 755 /home/root/AI/

# Generate the System Application Launcher
cat <<'APP_EOF' > /usr/share/applications/goddess-matrix.desktop
[Desktop Entry]
Name=Goddess Matrix
Comment=A.I. Orchestration Sandbox
Path=/home/root/AI
Exec=java -jar /home/root/AI/GoddessMatrix.jar
Icon=utilities-terminal
Terminal=false
Type=Application
Categories=Development;Utility;System;
APP_EOF

chmod 644 /usr/share/applications/goddess-matrix.desktop

echo "SYSTEM> DEPLOYMENT COMPLETE. THE MATRIX IS ONLINE."
exit 0
EOF

# 6. Secure Debian Permissions (CRITICAL)
chmod 755 "${STAGING_DIR}/DEBIAN/postinst"

# 7. Compile the Package
echo "SYSTEM> COMPILING .DEB INSTALLER..."
dpkg-deb --build "${STAGING_DIR}"

# 8. Cleanup
echo "SYSTEM> PURGING TEMPORARY STAGING FILES..."
rm -rf "${STAGING_DIR}"

echo "SYSTEM> BUILD COMPLETE: ${PKG_NAME}_${VERSION}_${ARCH}.deb IS READY."
exit 0
