package com.firmae.test;

import jakarta.validation.constraints.*;

/**
 * 用户 DTO - 用于测试 @Validated 嵌套校验
 * 
 * 字段上的校验注解会在 @Validated 标注时被递归校验
 */
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

    public UserDTO() {
    }

    public UserDTO(String username, String email, int age) {
        this.username = username;
        this.email = email;
        this.age = age;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
