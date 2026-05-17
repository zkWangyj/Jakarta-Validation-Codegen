#!/bin/bash

# 验证脚本 - 检查项目结构和关键文件

echo "========================================="
echo "验证 Jakarta Validation 代码生成工具"
echo "========================================="

# 检查项目结构
echo ""
echo "1. 检查项目结构..."

DIRS=(
    "validation-codegen-api/src/main/java/com/example/validation/api"
    "validation-codegen-processor/src/main/java/com/example/validation/processor"
    "validation-codegen-processor/src/main/java/com/example/validation/processor/asm"
    "validation-codegen-processor/src/main/java/com/example/validation/processor/model"
    "validation-codegen-processor/src/main/java/com/example/validation/processor/parser"
    "validation-codegen-test/src/main/java/com/example/test"
    "validation-codegen-test/src/test/java/com/example/test"
)

for dir in "${DIRS[@]}"; do
    if [ -d "/workspace/validation-codegen/$dir" ]; then
        echo "  ✓ $dir"
    else
        echo "  ✗ $dir (缺失)"
    fi
done

# 检查关键文件
echo ""
echo "2. 检查关键文件..."

FILES=(
    "validation-codegen-api/src/main/java/com/example/validation/api/ConstraintViolation.java"
    "validation-codegen-api/src/main/java/com/example/validation/api/Validatable.java"
    "validation-codegen-processor/src/main/java/com/example/validation/processor/ValidationProcessor.java"
    "validation-codegen-processor/src/main/java/com/example/validation/processor/asm/BytecodeInjector.java"
    "validation-codegen-processor/src/main/java/com/example/validation/processor/model/ClassValidationModel.java"
    "validation-codegen-processor/src/main/java/com/example/validation/processor/model/MethodValidationModel.java"
    "validation-codegen-test/src/main/java/com/example/test/UserService.java"
    "validation-codegen-test/src/test/java/com/example/test/ValidationTest.java"
    "pom.xml"
    "README.md"
    "USAGE.md"
)

for file in "${FILES[@]}"; do
    if [ -f "/workspace/validation-codegen/$file" ]; then
        echo "  ✓ $file"
    else
        echo "  ✗ $file (缺失)"
    fi
done

# 检查注解处理器配置
echo ""
echo "3. 检查注解处理器配置..."

if [ -f "/workspace/validation-codegen/validation-codegen-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor" ]; then
    echo "  ✓ Processor 配置文件"
    cat "/workspace/validation-codegen/validation-codegen-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor"
else
    echo "  ✗ Processor 配置文件缺失"
fi

# 统计代码行数
echo ""
echo "4. 代码统计..."

find /workspace/validation-codegen -name "*.java" -type f | wc -l | xargs echo "  Java 文件数量:"

find /workspace/validation-codegen -name "*.java" -type f -exec wc -l {} + | tail -1 | awk '{print "  总代码行数: " $1}'

echo ""
echo "========================================="
echo "验证完成"
echo "========================================="
echo ""
echo "注意: 运行测试需要 Maven 3.8+ 和 JDK 17+"
echo "当前环境: $(java -version 2>&1 | head -1)"
