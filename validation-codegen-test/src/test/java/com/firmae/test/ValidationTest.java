package com.firmae.test;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 校验功能完整测试
 * 编译时注解处理器会在 UserService/UserRecord 方法体中注入校验调用
 * 测试直接调用方法验证校验是否生效
 */
public class ValidationTest {

    // ==================== 构造器测试 (@NotNull + @NotBlank) ====================

    @Test
    void testConstructor_valid() {
        assertDoesNotThrow(() -> new UserService("my-service"));
    }

    @Test
    void testConstructor_null() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserService(null));
        assertEquals("服务名称不能为空", ex.getMessage());
    }

    @Test
    void testConstructor_emptyString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserService(""));
        assertEquals("服务名称不能为空白", ex.getMessage());
    }

    @Test
    void testConstructor_blankString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserService("   "));
        assertEquals("服务名称不能为空白", ex.getMessage());
    }

    // ==================== createUser 测试 (@NotNull + @Size + @Email + @Min + @Max)
    // ====================

    private UserService newUserService() {
        return new UserService("test-service");
    }

    @Test
    void testCreateUser_valid() {
        assertDoesNotThrow(() -> newUserService().createUser("john_doe", "john@example.com", 25));
    }

    @Test
    void testCreateUser_boundaryAge_min() {
        assertDoesNotThrow(() -> newUserService().createUser("john_doe", "john@example.com", 0));
    }

    @Test
    void testCreateUser_boundaryAge_max() {
        assertDoesNotThrow(() -> newUserService().createUser("john_doe", "john@example.com", 150));
    }

    @Test
    void testCreateUser_nullUsername() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser(null, "john@example.com", 25));
        assertEquals("用户名不能为空", ex.getMessage());
    }

    @Test
    void testCreateUser_usernameTooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser("A", "john@example.com", 25));
        assertTrue(ex.getMessage().contains("用户名长度必须在"));
    }

    @Test
    void testCreateUser_usernameTooLong() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser("A".repeat(21), "john@example.com", 25));
        assertTrue(ex.getMessage().contains("用户名长度必须在"));
    }

    @Test
    void testCreateUser_nullEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser("john_doe", null, 25));
        assertEquals("邮箱不能为空", ex.getMessage());
    }

    @Test
    void testCreateUser_invalidEmail_noAt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser("john_doe", "invalid-email", 25));
        assertEquals("邮箱格式不正确", ex.getMessage());
    }

    @Test
    void testCreateUser_invalidEmail_noDomain() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser("john_doe", "john@", 25));
        assertEquals("邮箱格式不正确", ex.getMessage());
    }

    @Test
    void testCreateUser_ageTooLow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser("john_doe", "john@example.com", -1));
        assertEquals("年龄不能小于 0", ex.getMessage());
    }

    @Test
    void testCreateUser_ageTooHigh() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().createUser("john_doe", "john@example.com", 151));
        assertEquals("年龄不能大于 150", ex.getMessage());
    }

    // ==================== updateUser 测试 (@NotNull + @NotBlank + @PositiveOrZero)
    // ====================

    @Test
    void testUpdateUser_valid() {
        assertDoesNotThrow(() -> newUserService().updateUser(1L, "John", 100.0));
    }

    @Test
    void testUpdateUser_nullUserId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().updateUser(null, "John", 100.0));
        assertEquals("用户ID不能为空", ex.getMessage());
    }

    @Test
    void testUpdateUser_nullNickname() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().updateUser(1L, null, 100.0));
        assertEquals("昵称不能为空", ex.getMessage());
    }

    @Test
    void testUpdateUser_emptyNickname() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().updateUser(1L, "", 100.0));
        assertEquals("昵称不能为空", ex.getMessage());
    }

    @Test
    void testUpdateUser_blankNickname() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().updateUser(1L, "   ", 100.0));
        assertEquals("昵称不能为空", ex.getMessage());
    }

    @Test
    void testUpdateUser_negativePoints() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().updateUser(1L, "John", -0.1));
        assertEquals("积分不能为负数", ex.getMessage());
    }

    @Test
    void testUpdateUser_zeroPoints() {
        assertDoesNotThrow(() -> newUserService().updateUser(1L, "John", 0.0));
    }

    // ==================== verifyUser 测试 (@NotNull + @Pattern + @AssertTrue)
    // ====================

    @Test
    void testVerifyUser_valid() {
        assertDoesNotThrow(() -> newUserService().verifyUser("abc123def456ghi789jkl012mno345pq", true));
    }

    @Test
    void testVerifyUser_nullToken() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().verifyUser(null, true));
        assertEquals("token不能为空", ex.getMessage());
    }

    @Test
    void testVerifyUser_invalidToken_tooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().verifyUser("short", true));
        assertEquals("token格式不正确", ex.getMessage());
    }

    @Test
    void testVerifyUser_invalidToken_badChars() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().verifyUser("!!!@@@###$$$%%%^^^&&&***(((___+++", true));
        assertEquals("token格式不正确", ex.getMessage());
    }

    @Test
    void testVerifyUser_notAgreed() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().verifyUser("abc123def456ghi789jkl012mno345pq", false));
        assertEquals("必须同意条款", ex.getMessage());
    }

    // ==================== validateUsername 静态方法测试 (@NotNull + @Size)
    // ====================

    @Test
    void testValidateUsername_valid() {
        assertDoesNotThrow(() -> UserService.validateUsername("john_doe"));
    }

    @Test
    void testValidateUsername_boundary_min() {
        assertDoesNotThrow(() -> UserService.validateUsername("AB"));
    }

    @Test
    void testValidateUsername_boundary_max() {
        assertDoesNotThrow(() -> UserService.validateUsername("A".repeat(20)));
    }

    @Test
    void testValidateUsername_null() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UserService.validateUsername(null));
        assertEquals("用户名不能为空", ex.getMessage());
    }

    @Test
    void testValidateUsername_tooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UserService.validateUsername("A"));
        assertTrue(ex.getMessage().contains("必须在 2 到 20 之间"));
    }

    @Test
    void testValidateUsername_tooLong() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UserService.validateUsername("A".repeat(21)));
        assertTrue(ex.getMessage().contains("必须在 2 到 20 之间"));
    }

    // ==================== setTags 测试 (@NotEmpty - List) ====================

    @Test
    void testSetTags_valid() {
        List<String> tags = new ArrayList<>();
        tags.add("java");
        assertDoesNotThrow(() -> newUserService().setTags(tags));
    }

    @Test
    void testSetTags_null() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setTags(null));
        assertEquals("标签列表不能为空", ex.getMessage());
    }

    @Test
    void testSetTags_empty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setTags(new ArrayList<>()));
        assertEquals("标签列表不能为空", ex.getMessage());
    }

    // ==================== setDescription 测试 (@NotEmpty - String)
    // ====================

    @Test
    void testSetDescription_valid() {
        assertDoesNotThrow(() -> newUserService().setDescription("A description"));
    }

    @Test
    void testSetDescription_null() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setDescription(null));
        assertEquals("描述不能为空字符串", ex.getMessage());
    }

    @Test
    void testSetDescription_emptyString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setDescription(""));
        assertEquals("描述不能为空字符串", ex.getMessage());
    }

    // ==================== setSalary 测试 (@Positive) ====================

    @Test
    void testSetSalary_valid() {
        assertDoesNotThrow(() -> newUserService().setSalary(5000.0));
    }

    @Test
    void testSetSalary_zero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setSalary(0.0));
        assertEquals("薪资必须为正数", ex.getMessage());
    }

    @Test
    void testSetSalary_negative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setSalary(-100.0));
        assertEquals("薪资必须为正数", ex.getMessage());
    }

    // ==================== setDebt 测试 (@Negative) ====================

    @Test
    void testSetDebt_valid() {
        assertDoesNotThrow(() -> newUserService().setDebt(-100.0));
    }

    @Test
    void testSetDebt_zero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setDebt(0.0));
        assertEquals("债务必须为负数", ex.getMessage());
    }

    @Test
    void testSetDebt_positive() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setDebt(100.0));
        assertEquals("债务必须为负数", ex.getMessage());
    }

    // ==================== setBalance 测试 (@NegativeOrZero) ====================

    @Test
    void testSetBalance_valid_negative() {
        assertDoesNotThrow(() -> newUserService().setBalance(-100.0));
    }

    @Test
    void testSetBalance_valid_zero() {
        assertDoesNotThrow(() -> newUserService().setBalance(0.0));
    }

    @Test
    void testSetBalance_positive() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setBalance(100.0));
        assertEquals("余额必须为负数或零", ex.getMessage());
    }

    // ==================== disableAccount 测试 (@AssertFalse) ====================

    @Test
    void testDisableAccount_valid() {
        assertDoesNotThrow(() -> newUserService().disableAccount(false));
    }

    @Test
    void testDisableAccount_invalid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().disableAccount(true));
        assertEquals("账户必须已禁用", ex.getMessage());
    }

    // ==================== setMetadata 测试 (@NotNull + @NotEmpty - Map)
    // ====================

    @Test
    void testSetMetadata_valid() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");
        assertDoesNotThrow(() -> newUserService().setMetadata(metadata));
    }

    @Test
    void testSetMetadata_null() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setMetadata(null));
        assertEquals("元数据不能为空", ex.getMessage());
    }

    @Test
    void testSetMetadata_emptyMap() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().setMetadata(new HashMap<>()));
        assertEquals("元数据不能为空Map", ex.getMessage());
    }

    // ==================== UserRecord 测试 (Record 类型) ====================

    @Test
    void testRecord_valid() {
        assertDoesNotThrow(() -> new UserRecord("id1", "john_doe", "john@example.com", 25, 100, true));
    }

    @Test
    void testRecord_nullUsername() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", null, "john@example.com", 25, 100, true));
        assertEquals("用户名不能为空", ex.getMessage());
    }

    @Test
    void testRecord_usernameTooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", "A", "john@example.com", 25, 100, true));
        assertTrue(ex.getMessage().contains("用户名长度必须在"));
    }

    @Test
    void testRecord_nullEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", "john_doe", null, 25, 100, true));
        assertEquals("邮箱不能为空", ex.getMessage());
    }

    @Test
    void testRecord_invalidEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", "john_doe", "invalid", 25, 100, true));
        assertEquals("邮箱格式不正确", ex.getMessage());
    }

    @Test
    void testRecord_negativeAge() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", "john_doe", "john@example.com", -1, 100, true));
        assertEquals("年龄不能小于 0", ex.getMessage());
    }

    @Test
    void testRecord_ageTooHigh() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", "john_doe", "john@example.com", 151, 100, true));
        assertEquals("年龄不能大于 150", ex.getMessage());
    }

    @Test
    void testRecord_negativePoints() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", "john_doe", "john@example.com", 25, -1, true));
        assertEquals("积分必须为正数", ex.getMessage());
    }

    @Test
    void testRecord_notAgreed() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("id1", "john_doe", "john@example.com", 25, 100, false));
        assertEquals("必须同意条款", ex.getMessage());
    }

    // ==================== @Validated 嵌套校验测试 ====================

    @Test
    void testValidated_nullUser() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().saveUser(null));
        assertEquals("user校验失败", ex.getMessage());
    }

    @Test
    void testValidated_nullUsername() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().saveUser(new UserDTO(null, "john@example.com", 25)));
        assertEquals("用户名不能为空", ex.getMessage());
    }

    @Test
    void testValidated_usernameTooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().saveUser(new UserDTO("A", "john@example.com", 25)));
        assertTrue(ex.getMessage().contains("用户名长度必须在"));
    }

    @Test
    void testValidated_nullEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().saveUser(new UserDTO("john", null, 25)));
        assertEquals("邮箱不能为空", ex.getMessage());
    }

    @Test
    void testValidated_invalidEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().saveUser(new UserDTO("john", "not-an-email", 25)));
        assertEquals("邮箱格式不正确", ex.getMessage());
    }

    @Test
    void testValidated_ageTooLow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().saveUser(new UserDTO("john", "john@example.com", -1)));
        assertEquals("年龄不能小于 0", ex.getMessage());
    }

    @Test
    void testValidated_ageTooHigh() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newUserService().saveUser(new UserDTO("john", "john@example.com", 200)));
        assertEquals("年龄不能大于 150", ex.getMessage());
    }

    @Test
    void testValidated_valid() {
        assertDoesNotThrow(() -> newUserService().saveUser(new UserDTO("john", "john@example.com", 25)));
    }

    // ==================== Spring 占位符解析测试 (@Pattern) ====================

    @Test
    void testSpringPlaceholderPatternResolved() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("testProperties", Map.of("token.pattern", "[a-z]{32}")));
        context.register(com.firmae.validation.SpringConfigHolder.class);
        context.refresh();
        try {
            SpringPlaceholderService service = new SpringPlaceholderService();
            assertDoesNotThrow(() -> service.processToken("abcdefghijklmnopqrstuvwxyzabcdef"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.processToken("INVALID_TOKEN"));
            assertEquals("token格式不正确", ex.getMessage());
        } finally {
            context.close();
        }
    }

    @Test
    void testSpringPlaceholderSizeStringResolved() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("testProperties", Map.of("username.min", "3", "username.max", "8")));
        context.register(com.firmae.validation.SpringConfigHolder.class);
        context.refresh();
        try {
            SpringPlaceholderService service = new SpringPlaceholderService();
            assertDoesNotThrow(() -> service.checkSize("abc"));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.checkSize("ab"));
            assertTrue(ex.getMessage().contains("用户名长度必须在"));
        } finally {
            context.close();
        }
    }

    @Test
    void testSpringPlaceholderMinStringResolved() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("testProperties", Map.of("user.age.min", "18")));
        context.register(com.firmae.validation.SpringConfigHolder.class);
        context.refresh();
        try {
            SpringPlaceholderService service = new SpringPlaceholderService();
            assertDoesNotThrow(() -> service.checkMin(18));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.checkMin(17));
            assertTrue(ex.getMessage().contains("年龄不能小于"));
        } finally {
            context.close();
        }
    }

    @Test
    void testSpringPlaceholderMaxStringResolved() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("testProperties", Map.of("user.age.max", "65")));
        context.register(com.firmae.validation.SpringConfigHolder.class);
        context.refresh();
        try {
            SpringPlaceholderService service = new SpringPlaceholderService();
            assertDoesNotThrow(() -> service.checkAge(60));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.checkAge(70));
            assertTrue(ex.getMessage().contains("年龄不能大于"));
        } finally {
            context.close();
        }
    }
}
