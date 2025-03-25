#!/bin/bash
set -e  # Exit if any command fails

# Ensure the Gradle Wrapper is executable.
chmod +x ./gradlew

echo "🚀 Running Gradle packageCore task..."
if ./gradlew packageCore; then
  echo "✅ Gradle packaging successful!"
else
  echo "❌ ERROR: Gradle packaging failed!"
  exit 1
fi