#!/usr/bin/env bash
# Builds EasyNotes92 for macOS: build/EasyNotes92-<version>.dmg (and the .app inside it).
# Run on a Mac with a JDK 25+ (javac, jar, jpackage):   ./build-mac.sh
# GitHub Actions runs this automatically (.github/workflows/build-macos.yml).
# Heap is 96 MB here (48 MB on Windows): Retina screens draw everything at 2x.
set -euo pipefail
cd "$(dirname "$0")"

NAME=EasyNotes92
VERSION="${VERSION:-1.0.0}"
CP="lib/flatlaf-3.7.1.jar:lib/flatlaf-intellij-themes-3.7.1.jar"

echo "1/4 Compiling..."
rm -rf build && mkdir -p build/classes build/input
javac --release 25 -encoding UTF-8 -cp "$CP" -d build/classes $(find src -name '*.java')

echo "2/4 Jar + icon..."
jar --create --file build/input/easynotes92.jar --main-class com.esisa.notepad.Main -C build/classes .
cp lib/*.jar build/input/
java -cp "build/classes:$CP" com.esisa.notepad.AppIcon --iconset build/$NAME.iconset
iconutil -c icns build/$NAME.iconset -o build/$NAME.icns

echo "3/4 jpackage (.app inside a .dmg)..."
jpackage --type dmg --name "$NAME" --app-version "$VERSION" \
    --input build/input --main-jar easynotes92.jar --main-class com.esisa.notepad.Main \
    --icon build/$NAME.icns --dest build \
    --mac-package-identifier com.sg92.easynotes92 --mac-package-name "$NAME" \
    --java-options '-Dfile.encoding=UTF-8' --java-options '--enable-native-access=ALL-UNNAMED' \
    --java-options '-Xms16m' --java-options '-Xmx96m' --java-options '-XX:+UseSerialGC' \
    --java-options '-XX:TieredStopAtLevel=1' --java-options '-XX:ReservedCodeCacheSize=24m' \
    --java-options '-XX:MaxMetaspaceSize=64m' --java-options '-Xss512k' \
    --java-options '-XX:+UseStringDeduplication' --java-options '-XX:+UseCompactObjectHeaders' \
    --java-options '-XX:MinHeapFreeRatio=10' --java-options '-XX:MaxHeapFreeRatio=30' \
    --description 'Bloc-notes pour la prise de note' \
    --vendor 'Mohamed Iraqi Houssaini' \
    --copyright 'Copyright (c) 2026 Mohamed Iraqi Houssaini. PolyForm Noncommercial 1.0.0 - free, never for sale.'

echo "4/4 Done:"
ls -lh build/*.dmg
