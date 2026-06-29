#!/bin/bash
# Goddess Matrix Automated JAR Compiler

echo "SYSTEM> COMPILING JAVA SOURCE..."
javac GoddessMatrix.java

if [ $? -ne 0 ]; then
    echo "SYSTEM_ERR> COMPILATION FAILED. ABORTING."
    exit 1
fi

echo "SYSTEM> GENERATING MANIFEST..."
echo "Manifest-Version: 1.0" > MANIFEST.MF
echo "Main-Class: GoddessMatrix" >> MANIFEST.MF

echo "SYSTEM> PACKAGING EXECUTABLE JAR..."
# cvfm = Create, Verbose, specify File name, specify Manifest
jar cvfm GoddessMatrix.jar MANIFEST.MF GoddessMatrix.class GoddessMatrix\$*.class

echo "SYSTEM> PURGING TEMPORARY CLASS FILES..."
rm *.class MANIFEST.MF

echo "SYSTEM> BUILD COMPLETE: GoddessMatrix.jar IS READY."
exit 0