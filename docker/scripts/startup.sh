#!/bin/bash
set -e  # Exit if any command fails

HOME_DIR="/usr/src/app"
TEMP_DIR="${HOME_DIR}/temp"
SQL_DIR="${HOME_DIR}/sql"

# Ensure directories exist
mkdir -p "${TEMP_DIR}"
mkdir -p "${SQL_DIR}"

# Debug: Check if SQL_HOST is set
if [ -z "${SQL_HOST}" ]; then
  echo "⚠️ Warning: SQL_HOST is not set. Running in standalone mode."
  
  # ✅ Fetch the web-api-commander JAR if it doesn’t exist
  if [ -e "${TEMP_DIR}/web-api-commander.jar" ]; then
      echo "✅ web-api-commander.jar already exists. Skipping download."
  else
      echo "⬇️ Downloading web-api-commander.jar..."
      wget https://resostuff.blob.core.windows.net/refserverfiles/web-api-commander.jar -O "${TEMP_DIR}/web-api-commander.jar" || exit 1
  fi

  # ✅ Fetch the RESO Data Dictionary if needed
  if [ -e "RESODataDictionary-1.7.metadata-report.json" ]; then
      echo "✅ RESODataDictionary-1.7.metadata-report.json already exists. Skipping download."
  else
      echo "⬇️ Downloading RESODataDictionary-1.7.metadata-report.json..."
      wget https://resostuff.blob.core.windows.net/refserverfiles/RESODataDictionary-1.7.metadata-report.json -O "RESODataDictionary-1.7.metadata-report.json" || exit 1
  fi
fi  # <-- Correctly closing the if statement

# ✅ Ensure Gradle Wrapper is executable
chmod +x ./gradlew

# ✅ Initialize Gradle project if missing
if [ ! -f "build.gradle" ]; then
  echo "⚠️ build.gradle is missing. Running 'gradlew init'..."
  ./gradlew init || exit 1
fi

# ✅ Run Gradle build
echo "🚀 Running Gradle build..."
if ./gradlew war; then
  mv ./build/libs/RESOservice-1.0.war ./build/libs/core.war
  echo "✅ Gradle build successful!"
  
    # ✅ Copy JSON metadata file
  if [ -f "RESODataDictionary-1.7.metadata-report.json" ]; then
    cp RESODataDictionary-1.7.metadata-report.json ./build/libs/
    echo "✅ Copied RESODataDictionary-1.7.metadata-report.json to build/libs/"
  else
    echo "❌ ERROR: JSON file not found: RESODataDictionary-1.7.metadata-report.json"
    exit 1
  fi

else
  echo "❌ ERROR: Gradle build failed!"
  exit 1
fi
