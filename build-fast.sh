#!/bin/bash
set -e

echo "=== Building with Aliyun Maven mirror ==="
cd /workspace/validation-codegen

# Use local settings.xml with Aliyun mirror
mvn clean install -DskipTests --settings settings.xml -Dmaven.test.skip=true

echo ""
echo "=== Build completed ==="
