#!/bin/sh
# Starts the Firebase Local Emulator Suite (Authentication + Cloud Firestore with
# firestore.rules) for the demo project the app uses when app/google-services.json is absent.
# Data is saved to ./emulator-data when you stop the emulators (Ctrl+C) and loaded next time.
#
# Requires: Node.js, Java 11+ (Java 21 recommended), and firebase-tools
#   npm install -g firebase-tools
cd "$(dirname "$0")" || exit 1
mkdir -p emulator-data
if [ -f emulator-data/firebase-export-metadata.json ]; then
  IMPORT="--import=./emulator-data"
else
  IMPORT=""
fi
exec firebase emulators:start --only auth,firestore --project demo-trojanrideshare \
  $IMPORT --export-on-exit=./emulator-data
