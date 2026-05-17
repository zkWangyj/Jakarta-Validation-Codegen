#!/bin/bash

# Jakarta Validation 代码生成工具构建脚本
# 需要: JDK 17+, Maven 3.8+

set -e

echo "========================================="
echo "Building Validation Codegen Tool"
echo "========================================="

# 检查 Java 版本
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Java version: $JAVA_VERSION"

# 检查 Maven
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed"
    echo "Please install Maven 3.8+ and try again"
    exit 1
fi

echo "Maven version:"
mvn -version | head -1

echo ""
echo "========================================="
echo "Step 1: Building API module"
echo "========================================="
cd validation-codegen-api
mvn clean install -DskipTests
cd ..

echo ""
echo "========================================="
echo "Step 2: Building Processor module"
echo "========================================="
cd validation-codegen-processor
mvn clean install -DskipTests
cd ..

echo ""
echo "========================================="
echo "Step 3: Building and Testing"
echo "========================================="
cd validation-codegen-test
mvn clean test
cd ..

echo ""
echo "========================================="
echo "Build Complete!"
echo "========================================="
echo ""
echo "Artifacts installed to local Maven repository:"
echo "  - com.firmae:validation-codegen-api:1.0.0-SNAPSHOT"
echo "  - com.firmae:validation-codegen-processor:1.0.0-SNAPSHOT"
echo ""
echo "To use in your project, add these dependencies:"
echo ""
echo "  <dependency>"
echo "      <groupId>com.firmae</groupId>"
echo "      <artifactId>validation-codegen-api</artifactId>"
echo "      <version>1.0.0-SNAPSHOT</version>"
echo "  </dependency>"
echo ""
echo "  <dependency>"
echo "      <groupId>com.firmae</groupId>"
echo "      <artifactId>validation-codegen-processor</artifactId>"
echo "      <version>1.0.0-SNAPSHOT</version>"
echo "      <scope>provided</scope>"
echo "  </dependency>"
