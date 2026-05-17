#!/bin/bash
set -e

echo "=== Building validation-codegen ==="
cd /workspace/validation-codegen

# Clean and install all modules
mvn clean install -DskipTests

echo ""
echo "=== Checking if processor jar contains service file ==="
jar tf validation-codegen-processor/target/validation-codegen-processor-*.jar | grep -i processor || echo "No processor file found in jar!"

echo ""
echo "=== Compiling test module with verbose processor output ==="
cd validation-codegen-test
mvn clean compile -X 2>&1 | grep -i "validation\|processor\|annotation" | head -50 || echo "No processor-related output found"

echo ""
echo "=== Checking generated files ==="
find . -name "ValidationHelper.java" 2>/dev/null || echo "No ValidationHelper.java found"
find . -name "validation-meta*.json" 2>/dev/null || echo "No meta files found"

echo ""
echo "=== Checking if UserService.java was modified ==="
grep -n "ValidationHelper" src/main/java/com/firmae/test/UserService.java 2>/dev/null || echo "UserService.java not modified"

echo ""
echo "Done!"
