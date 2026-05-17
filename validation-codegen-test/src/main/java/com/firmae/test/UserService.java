package com.firmae.test;

import com.firmae.validation.annotation.PreCompile;
import com.firmae.validation.annotation.Validated;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

/**
 * 用户服务类 - 测试方法参数校验（包含构造器）
 * 使用 @PreCompile 启用编译时校验
 */
public class UserService {

    /**
     * 构造器 - 带参数校验
     */
    @PreCompile
    public UserService(
            @NotNull(message = "服务名称不能为空")
            @NotBlank(message = "服务名称不能为空白")
            String serviceName) {
        System.out.println("UserService initialized: " + serviceName);
    }

    /**
     * 创建用户 - 带参数校验
     */
    @PreCompile
    public void createUser(
            @NotNull(message = "用户名不能为空")
            @Size(min = 2, max = 20, message = "用户名长度必须在 {min} 到 {max} 之间")
            String username,

            @NotNull(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            String email,

            @Min(value = 0, message = "年龄不能小于 {value}")
            @Max(value = 150, message = "年龄不能大于 {value}")
            int age) {
        System.out.println("Creating user: " + username + ", " + email + ", " + age);
    }

    /**
     * 更新用户信息
     */
    @PreCompile
    public void updateUser(
            @NotNull(message = "用户ID不能为空")
            Long userId,

            @NotBlank(message = "昵称不能为空")
            String nickname,

            @PositiveOrZero(message = "积分不能为负数")
            double points) {
        System.out.println("Updating user: " + userId + ", " + nickname + ", " + points);
    }

    /**
     * 验证用户状态
     */
    @PreCompile
    public void verifyUser(
            @NotNull(message = "token不能为空")
            @Pattern(regexp = "[a-zA-Z0-9]{32}", message = "token格式不正确")
            String token,

            @AssertTrue(message = "必须同意条款")
            boolean agreedToTerms) {
        System.out.println("Verifying user with token: " + token);
    }

    /**
     * 静态方法测试
     */
    @PreCompile
    public static void validateUsername(
            @NotNull(message = "用户名不能为空")
            @Size(min = 2, max = 20)
            String username) {
        System.out.println("Username is valid: " + username);
    }

    /**
     * NotEmpty 注解测试
     */
    @PreCompile
    public void setTags(
            @NotEmpty(message = "标签列表不能为空")
            List<String> tags) {
        System.out.println("Tags set: " + tags);
    }

    /**
     * NotEmpty 注解测试 - String
     */
    @PreCompile
    public void setDescription(
            @NotEmpty(message = "描述不能为空字符串")
            String description) {
        System.out.println("Description set: " + description);
    }

    /**
     * Positive 注解测试
     */
    @PreCompile
    public void setSalary(
            @Positive(message = "薪资必须为正数")
            double salary) {
        System.out.println("Salary set: " + salary);
    }

    /**
     * Negative 注解测试
     */
    @PreCompile
    public void setDebt(
            @Negative(message = "债务必须为负数")
            double debt) {
        System.out.println("Debt set: " + debt);
    }

    /**
     * NegativeOrZero 注解测试
     */
    @PreCompile
    public void setBalance(
            @NegativeOrZero(message = "余额必须为负数或零")
            double balance) {
        System.out.println("Balance set: " + balance);
    }

    /**
     * AssertFalse 注解测试
     */
    @PreCompile
    public void disableAccount(
            @AssertFalse(message = "账户必须已禁用")
            boolean isActive) {
        System.out.println("Account disabled");
    }

    /**
     * NotNull + NotEmpty 组合测试 - Map
     */
    @PreCompile
    public void setMetadata(
            @NotNull(message = "元数据不能为空")
            @NotEmpty(message = "元数据不能为空Map")
            Map<String, String> metadata) {
        System.out.println("Metadata set: " + metadata);
    }

    /**
     * @Validated 嵌套校验测试
     */
    @PreCompile
    public void saveUser(@Validated UserDTO user) {
        System.out.println("Saving user: " + user);
    }

    /**
     * 没有 @PreCompile 的方法 - 不会被处理
     */
    public void noValidation(String param) {
        System.out.println("No validation: " + param);
    }
}
