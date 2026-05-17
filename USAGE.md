# 使用指南

本文档详细介绍 validation-codegen 的各种使用场景和配置选项。

## 目录

- [基本使用](#基本使用)
- [支持的注解详解](#支持的注解详解)
- [各种场景示例](#各种场景示例)
- [嵌套校验 @Validated](#嵌套校验-validated)
- [占位符使用](#占位符使用)
- [故障排除](#故障排除)

## 基本使用

### 1. 配置 pom.xml

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

### 2. 在方法上添加 @PreCompile

```java
import com.firmae.validation.annotation.PreCompile;
import jakarta.validation.constraints.*;

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

### 3. 编译

```bash
mvn clean compile
```

插件会在 `generate-sources` 阶段自动将校验代码注入到方法体开头，输出到 `target/generated-sources/validation-injected`。

## 支持的注解详解

### 空值检查

| 注解 | 说明 | 适用类型 |
|------|------|----------|
| `@NotNull` | 值不能为 null | 任意类型 |
| `@NotEmpty` | 不能为空（null、空字符串、空集合、空 Map） | String, Collection, Map, 数组 |
| `@NotBlank` | 不能为空白（null、空字符串或纯空白字符） | String |

```java
@PreCompile
public void setName(
        @NotNull(message = "名称不能为空")
        @NotBlank(message = "名称不能为空白")
        String name) {}

@PreCompile
public void setTags(
        @NotEmpty(message = "标签列表不能为空")
        List<String> tags) {}
```

### 范围检查

| 注解 | 说明 | 占位符 |
|------|------|--------|
| `@Min(value)` | 数值最小值 | `{value}` |
| `@Max(value)` | 数值最大值 | `{value}` |
| `@Size(min, max)` | 字符串长度/集合大小 | `{min}`, `{max}` |

```java
@PreCompile
public void setAge(
        @Min(value = 0, message = "年龄不能小于 {value}")
        @Max(value = 150, message = "年龄不能大于 {value}")
        int age) {}
```

### 模式检查

| 注解 | 说明 |
|------|------|
| `@Pattern(regexp)` | 正则匹配 |
| `@Email` | 邮箱格式（内置正则） |

```java
@PreCompile
public void setToken(
        @Pattern(regexp = "[a-zA-Z0-9]{32}", message = "token格式不正确")
        String token) {}
```

### 数值符号检查

| 注解 | 说明 |
|------|------|
| `@Positive` | 必须为正数（> 0） |
| `@PositiveOrZero` | 必须为正数或零（>= 0） |
| `@Negative` | 必须为负数（< 0） |
| `@NegativeOrZero` | 必须为负数或零（<= 0） |

### 布尔检查

| 注解 | 说明 |
|------|------|
| `@AssertTrue` | 必须为 true |
| `@AssertFalse` | 必须为 false |

## 各种场景示例

### 实例方法

```java
@PreCompile
public void createUser(
        @NotNull(message = "用户名不能为空")
        @Size(min = 2, max = 20)
        String username,
        @Email(message = "邮箱格式不正确")
        String email) {
    // 编译后自动注入：
    // ValidationHelper.checkNotNull(username, "用户名不能为空");
    // ValidationHelper.checkSize(username, 2, 20, "username长度必须在 2 到 20 之间");
    // ValidationHelper.checkEmail(email, "邮箱格式不正确");
}
```

### 构造器

```java
@PreCompile
public UserService(
        @NotNull(message = "服务名称不能为空")
        @NotBlank(message = "服务名称不能为空白")
        String serviceName) {
    // 编译后自动注入校验代码
}
```

### 静态方法

```java
@PreCompile
public static void validateUsername(
        @NotNull(message = "用户名不能为空")
        @Size(min = 2, max = 20)
        String username) {
    // 编译后自动注入校验代码
}
```

### Java Record

`@PreCompile` 放在 Record 声明上，自动生成 compact constructor：

```java
@PreCompile
public record UserRecord(
        @NotNull(message = "用户ID不能为空")
        String userId,

        @NotNull(message = "用户名不能为空")
        @Size(min = 2, max = 20, message = "用户名长度必须在 {min} 到 {max} 之间")
        String username,

        @Email(message = "邮箱格式不正确")
        String email
) {}
```

编译后生成：

```java
public record UserRecord(
        @NotNull(message = "用户ID不能为空")
        String userId,

        @NotNull(message = "用户名不能为空")
        @Size(min = 2, max = 20, message = "用户名长度必须在 {min} 到 {max} 之间")
        String username,

        @Email(message = "邮箱格式不正确")
        String email
) {
    public UserRecord {
        ValidationHelper.checkNotNull(userId, "用户ID不能为空");
        ValidationHelper.checkNotNull(username, "用户名不能为空");
        ValidationHelper.checkSize(username, 2, 20, "用户名长度必须在 2 到 20 之间");
        ValidationHelper.checkEmail(email, "邮箱格式不正确");
    }
}
```

### 已有 compact constructor 的 Record

校验代码会插入到已有 compact constructor 的最前面：

```java
@PreCompile
public record OrderRecord(
        @NotNull(message = "订单号不能为空")
        String orderNo,
        @Positive(message = "金额必须为正数")
        Double amount
) {
    public OrderRecord {
        // ↓ 自动注入的校验代码
        ValidationHelper.checkNotNull(orderNo, "订单号不能为空");
        ValidationHelper.checkPositive(amount, "金额必须为正数");

        // ↓ 原有业务逻辑
        if (orderNo != null) {
            orderNo = orderNo.trim();
        }
    }
}
```

### 没有 @PreCompile 的方法

不会被处理：

```java
// 没有 @PreCompile，不会注入校验代码
public void noValidation(String param) {
    System.out.println("No validation: " + param);
}
```

## 嵌套校验 @Validated

当方法参数是自定义对象时，使用 `@Validated` 注解可以自动校验该对象字段上的 Jakarta Validation 注解。

### 原理

`@Validated` 生成 `ValidationHelper.validate(param, "message")` 调用，内部使用 Hibernate Validator 对对象进行标准 Bean Validation 校验。

### 使用步骤

**1. 定义 DTO，在字段上添加校验注解：**

```java
public class UserDTO {

    @NotNull(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在 {min} 到 {max} 之间")
    private String username;

    @NotNull(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Min(value = 0, message = "年龄不能小于 {value}")
    @Max(value = 150, message = "年龄不能大于 {value}")
    private int age;

    // getter/setter ...
}
```

**2. 在方法参数上使用 @Validated：**

```java
@PreCompile
public void saveUser(@Validated UserDTO user) {
    // 业务逻辑
}
```

**3. 编译后生成：**

```java
public void saveUser(UserDTO user) {
    ValidationHelper.validate(user, "user校验失败");
    // 原有业务逻辑
}
```

**4. 运行时效果：**

- `user` 为 null → 抛出 `IllegalArgumentException("user校验失败")`
- `user.username` 为 null → 抛出 `IllegalArgumentException("用户名不能为空")`
- `user.email` 格式错误 → 抛出 `IllegalArgumentException("邮箱格式不正确")`
- 所有字段合法 → 正常执行

### 与直接校验注解的区别

| 特性 | 直接校验注解 (`@NotNull` 等) | `@Validated` |
|------|------|------|
| 适用位置 | 方法参数 | 方法参数（自定义对象） |
| 校验方式 | 生成 `ValidationHelper.checkXxx()` 静态调用 | 生成 `ValidationHelper.validate()` 使用 Hibernate Validator |
| 性能 | 极快（纯静态代码） | 稍慢（反射 + Bean Validation） |
| 适用场景 | 基本类型、String、集合 | 自定义对象（DTO） |

### 混合使用

`@Validated` 可以与直接校验注解一起使用：

```java
@PreCompile
public void saveUser(
        @NotNull(message = "操作类型不能为空")
        String operationType,
        @Validated UserDTO user) {
    // 编译后生成：
    // ValidationHelper.checkNotNull(operationType, "操作类型不能为空");
    // ValidationHelper.validate(user, "user校验失败");
}
```

## 占位符使用

在 `message` 中可以使用以下占位符：

| 占位符 | 说明 | 适用注解 |
|--------|------|----------|
| `{min}` | 最小值 | `@Size` |
| `{max}` | 最大值 | `@Size` |
| `{value}` | 约束值 | `@Min`, `@Max` |
| `{regexp}` | 正则表达式 | `@Pattern` |

示例：

```java
@Size(min = 2, max = 20, message = "长度必须在 {min} 到 {max} 之间")
// → "长度必须在 2 到 20 之间"

@Min(value = 0, message = "不能小于 {value}")
// → "不能小于 0"

@Pattern(regexp = "[a-z]+", message = "格式不正确: {regexp}")
// → "格式不正确: [a-z]+"
```

如果不指定 `message`，会使用默认消息（中文）：

| 注解 | 默认消息 |
|------|----------|
| `@NotNull` | `{参数名}不能为空` |
| `@NotBlank` | `{参数名}不能为空白` |
| `@NotEmpty` | `{参数名}不能为空` |
| `@Size` | `{参数名}长度必须在 {min} 到 {max} 之间` |
| `@Min` | `{参数名}不能小于最小值` |

### Spring 占位符与 String-valued 注解

如果你希望在注解中使用 Spring 配置占位符（例如 `${app.limit}`），请使用项目提供的字符串型注解：

- `@MinString("${app.min}")`
- `@MaxString("${app.max}")`
- `@SizeString(min = "${app.size.min}", max = "${app.size.max}")`

插件在扫描源代码时会尝试检测 Spring 相关类路径并自动启用 Spring 支持（注入日志会显示 `Spring support enabled`）。生成的代码在运行时会通过 `SpringConfigHolder.getProperty("app.min")` 获取配置值并将其转换为数值后再执行校验。

示例：

```java
@PreCompile
public void setLimits(
        @MinString("${app.limit.min}") String min,
        @MaxString("${app.limit.max}") String max) {
    // 运行时会解析占位符并进行数值校验
}
```

如果项目并未包含 Spring，插件仍会接受 `*String` 注解，但运行时会尝试直接解析字面值为数字并在无法解析时抛出明确异常。
| `@Max` | `{参数名}不能大于最大值` |
| `@Pattern` | `{参数名}格式不正确` |
| `@Email` | `{参数名}邮箱格式不正确` |
| `@Positive` | `{参数名}必须为正数` |
| `@PositiveOrZero` | `{参数名}不能为负数` |
| `@Negative` | `{参数名}必须为负数` |
| `@NegativeOrZero` | `{参数名}必须为负数或零` |
| `@AssertTrue` | `{参数名}必须为 true` |
| `@AssertFalse` | `{参数名}必须为 false` |
| `@Validated` | `{参数名}校验失败` |

## 故障排除

### 校验代码未注入

1. 确认方法/构造器/Record 上有 `@PreCompile` 注解
2. 确认参数上有 Jakarta Validation 注解或 `@Validated`
3. 运行 `mvn clean compile` 重新构建
4. 检查 `target/generated-sources/validation-injected` 目录中的生成代码

### 找不到 ValidationHelper

1. 确认 `validation-codegen-api` 依赖已添加
2. 运行 `mvn clean install -DskipTests` 安装 API 模块

### Record 校验不生效

1. 确保使用 JDK 17+
2. 确认 `@PreCompile` 在 Record 声明上
3. 检查生成的代码中是否有 compact constructor

### @Validated 校验不生效

1. 确认参数类型是自定义对象（非基本类型/String）
2. 确认 DTO 字段上有 Jakarta Validation 注解
3. 确认 `validation-codegen-api` 依赖包含 Hibernate Validator（传递依赖）

### 类重复编译错误

1. 确认 `<sourceDirectory>` 指向 `generated-sources/validation-injected`
2. 不要同时配置 `build-helper-maven-plugin` 添加源码目录
