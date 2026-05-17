package com.firmae.test;

import com.firmae.validation.annotation.PreCompile;
import jakarta.validation.constraints.*;

/**
 * Java Record 测试类 - 测试 Record 的 canonical constructor 参数校验
 */
@PreCompile
public record UserRecord(
        @NotNull(message = "用户ID不能为空")
        @NotEmpty(message = "用户ID不能为空字符串")
        String userId,

        @NotNull(message = "用户名不能为空")
        @Size(min = 2, max = 20, message = "用户名长度必须在 {min} 到 {max} 之间")
        String username,

        @NotNull(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @Min(value = 0, message = "年龄不能小于 {value}")
        @Max(value = 150, message = "年龄不能大于 {value}")
        int age,

        @Positive(message = "积分必须为正数")
        double points,

        @AssertTrue(message = "必须同意条款")
        boolean agreedToTerms
) {
}
