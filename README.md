# Jakarta Validation 静态代码生成工具

基于 Maven 插件 + JavaParser 的编译时校验代码注入工具，在 `generate-sources` 阶段自动将 `ValidationHelper.checkXxx()` 调用注入到方法体中。

## 核心特性

- **编译时注入**: Maven 插件在编译前自动注入校验代码到方法体
- **零运行时依赖**: 生成的代码直接调用 `ValidationHelper` 静态方法，无反射
- **高性能**: 静态代码执行，性能接近手写代码
- **15 种注解**: 完整支持 Jakarta Validation 标准注解 + `@Validated` 嵌套校验
- **Java Record**: 支持 Record 的 compact constructor 校验（自动生成或注入已有）
- **@PreCompile 标记**: 只有标注 `@PreCompile` 的方法/构造器/Record 才会被处理
- **嵌套校验**: `@Validated` 注解使用 Hibernate Validator 校验自定义对象字段

## 快速开始

### 1. 安装插件

```bash
mvn clean install -DskipTests
```

### 2. 配置项目

```xml
<dependencies>
    <dependency>
        <groupId>com.firmae</groupId>
        <artifactId>validation-codegen-api</artifactId>
        <scope>provided</scope>
        <version>${validation-codegen-api.version}</version>
    </dependency>
</dependencies>

<build>
    <!-- 主源码目录指向生成目录 -->
    <sourceDirectory>${project.build.directory}/generated-sources/validation-injected</sourceDirectory>

    <plugins>
        <!-- 校验代码注入插件 -->
        <plugin>
            <groupId>com.firmae</groupId>
            <artifactId>validation-codegen-plugin</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <id>inject-validation</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>inject-validation</goal>
                    </goals>
                </execution>
                <execution>
                    <id>clean-generated-sources</id>
                    <phase>process-classes</phase>
                    <goals>
                        <goal>clean-generated-sources</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <release>17</release>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 3. 使用注解

```java
public class UserService {

    @PreCompile
    public void createUser(
            @NotNull(message = "用户名不能为空")
            @Size(min = 2, max = 20, message = "用户名长度必须在 {min} 到 {max} 之间")
            String username,

            @NotNull(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            String email) {
        // 业务逻辑
    }
}
```

编译后自动注入校验代码：

```java
public class UserService {

    public void createUser(
            @NotNull(message = "用户名不能为空")
            @Size(min = 2, max = 20, message = "用户名长度必须在 {min} 到 {max} 之间")
            String username,

            @NotNull(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            String email) {
        ValidationHelper.checkNotNull(username, "用户名不能为空");
        ValidationHelper.checkSize(username, 2, 20, "用户名长度必须在 2 到 20 之间");
        ValidationHelper.checkNotNull(email, "邮箱不能为空");
        ValidationHelper.checkEmail(email, "邮箱格式不正确");
        // 原有业务逻辑
    }
}
```

## 工作原理

```
generate-sources 阶段
─────────────────────────────────────────
src/main/java          JavaParser          generated-sources/
(UserService.java)  →  (AST 解析)  →     validation-injected/
                      (注入校验)         (UserService.java)
                                           ↓
compile 阶段                              ↓
─────────────────────────────────────────
只编译 generated-sources/ 目录  ←────────┘
```

1. **generate-sources**: 插件扫描 `src/main/java`，用 JavaParser 解析 AST
2. **注入校验**: 找到 `@PreCompile` 标记的方法/构造器/Record，在方法体开头注入 `ValidationHelper.checkXxx()` 调用
3. **输出**: 所有源码（包括未修改的）输出到 `generated-sources/validation-injected`
4. **编译**: Maven 只编译 `generated-sources/validation-injected` 目录

## 支持的注解

### 参数校验注解

| 类别 | 注解 | ValidationHelper 方法 |
|------|------|----------------------|
| 空值检查 | `@NotNull` | `checkNotNull(Object, String)` |
| 空值检查 | `@NotBlank` | `checkNotBlank(String, String)` |
| 空值检查 | `@NotEmpty` | `checkNotEmpty(Object, String)` |
| 范围检查 | `@Size` | `checkSize(Object, int, int, String)` |
| 范围检查 | `@Min` | `checkMin(long, long, String)` |
| 范围检查 | `@Max` | `checkMax(long, long, String)` |
| 模式检查 | `@Pattern` | `checkPattern(String, String, String)` |
| 模式检查 | `@Email` | `checkEmail(String, String)` |
| 数值符号 | `@Positive` | `checkPositive(double, String)` |
| 数值符号 | `@PositiveOrZero` | `checkPositiveOrZero(double, String)` |
| 数值符号 | `@Negative` | `checkNegative(double, String)` |
| 数值符号 | `@NegativeOrZero` | `checkNegativeOrZero(double, String)` |
| 布尔检查 | `@AssertTrue` | `checkAssertTrue(boolean, String)` |
| 布尔检查 | `@AssertFalse` | `checkAssertFalse(boolean, String)` |

### 嵌套校验注解

| 注解 | 说明 | ValidationHelper 方法 |
|------|------|----------------------|
| `@Validated` | 使用 Hibernate Validator 校验自定义对象字段 | `validate(Object, String)` |

支持占位符：`{min}`, `{max}`, `{value}`, `{regexp}`

## 项目结构

```
validation-codegen/
├── validation-codegen-api/          # API 模块
│   ├── ValidationHelper.java        # 校验工具类（14 个 checkXxx + 1 个 validate）
│   └── annotation/
│       ├── PreCompile.java          # @PreCompile 标记注解
│       └── Validated.java           # @Validated 嵌套校验注解
├── validation-codegen-plugin/       # Maven 插件模块
│   └── ValidationCodegenMojo.java   # 插件入口（JavaParser AST 注入）
├── validation-codegen-test/         # 测试模块
│   ├── UserService.java             # 普通类测试（13 个方法）
│   ├── UserRecord.java              # Record 测试
│   ├── UserDTO.java                 # 嵌套校验测试 DTO
│   └── ValidationTest.java          # 70 个单元测试
└── pom.xml
```

## 构建要求

- **JDK**: 17+
- **Maven**: 3.6+

```bash
# 安装
mvn clean install -DskipTests

# 运行测试（70 个用例）
mvn test
```

## 感谢
本项目只是突然有一个想法，主要代码使用 TRAE SOLO CN 完成，感谢字节

如果有人有用，我会很感激。

如果有人有用，我会很感激。

## 许可证

MIT License
