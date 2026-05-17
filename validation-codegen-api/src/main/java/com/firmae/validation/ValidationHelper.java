package com.firmae.validation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;

/**
 * 校验工具类 - 提供对应 Jakarta Validation 注解的校验方法
 * 
 * <p>
 * 每个注解对应一个 checkXxx 方法，注解处理器会在标注了 @PreCompile 的方法体中
 * 自动注入对这些方法的调用。
 * </p>
 * 
 * <p>
 * 使用示例（编译后自动生成）：
 * </p>
 * 
 * <pre>
 * public void createUser(@NotNull(message = "用户名不能为空") String username) {
 *     ValidationHelper.checkNotNull(username, "用户名不能为空");
 *     // 原有业务代码
 * }
 * </pre>
 */
public class ValidationHelper {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private ValidationHelper() {
    }

    /**
     * 对应 @NotNull 注解
     */
    public static void checkNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @NotBlank 注解
     */
    public static void checkNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @NotEmpty 注解 - 支持 String, Collection, Map
     */
    public static void checkNotEmpty(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        if (value instanceof String && ((String) value).isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @Size 注解
     */
    public static void checkSize(Object value, int min, int max, String message) {
        if (value == null)
            return;
        int len;
        if (value instanceof String)
            len = ((String) value).length();
        else if (value instanceof Collection)
            len = ((Collection<?>) value).size();
        else if (value instanceof Map)
            len = ((Map<?, ?>) value).size();
        else if (value.getClass().isArray())
            len = java.lang.reflect.Array.getLength(value);
        else
            return;
        if (len < min || len > max) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @Min 注解
     */
    public static void checkMin(long value, long min, String message) {
        if (value < min) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @Max 注解
     */
    public static void checkMax(long value, long max, String message) {
        if (value > max) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @DecimalMin 注解
     */
    public static void checkDecimalMin(Object value, java.math.BigDecimal min, String message) {
        if (value == null) {
            return;
        }
        java.math.BigDecimal actual;
        if (value instanceof java.math.BigDecimal) {
            actual = (java.math.BigDecimal) value;
        } else if (value instanceof java.math.BigInteger) {
            actual = new java.math.BigDecimal((java.math.BigInteger) value);
        } else if (value instanceof Number) {
            actual = new java.math.BigDecimal(value.toString());
        } else {
            actual = new java.math.BigDecimal(value.toString());
        }
        if (actual.compareTo(min) < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @DecimalMax 注解
     */
    public static void checkDecimalMax(Object value, java.math.BigDecimal max, String message) {
        if (value == null) {
            return;
        }
        java.math.BigDecimal actual;
        if (value instanceof java.math.BigDecimal) {
            actual = (java.math.BigDecimal) value;
        } else if (value instanceof java.math.BigInteger) {
            actual = new java.math.BigDecimal((java.math.BigInteger) value);
        } else if (value instanceof Number) {
            actual = new java.math.BigDecimal(value.toString());
        } else {
            actual = new java.math.BigDecimal(value.toString());
        }
        if (actual.compareTo(max) > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @Pattern 注解
     */
    public static void checkPattern(String value, String regexp, String message) {
        if (value != null && !java.util.regex.Pattern.matches(regexp, value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * 对应 @Email 注解
     */
    public static void checkEmail(String value, String message) {
        if (value != null && !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @Positive 注解
     */
    public static void checkPositive(double value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @PositiveOrZero 注解
     */
    public static void checkPositiveOrZero(double value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @Negative 注解
     */
    public static void checkNegative(double value, String message) {
        if (value >= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @NegativeOrZero 注解
     */
    public static void checkNegativeOrZero(double value, String message) {
        if (value > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @AssertTrue 注解
     */
    public static void checkAssertTrue(boolean value, String message) {
        if (!value) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @AssertFalse 注解
     */
    public static void checkAssertFalse(boolean value, String message) {
        if (value) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 对应 @Validated 注解 - 使用 Hibernate Validator 校验嵌套对象（Default 分组）
     *
     * @param value   待校验的对象
     * @param message 校验失败时的消息前缀
     */
    public static void validate(Object value, String message) {
        validate(value, message, new Class<?>[0]);
    }

    /**
     * 对应 @Validated 注解 - 使用 Hibernate Validator 校验嵌套对象（带分组）
     *
     * @param value   待校验的对象
     * @param message 校验失败时的消息前缀
     * @param groups  校验分组
     */
    public static void validate(Object value, String message, Class<?>... groups) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(value, groups);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse(message);
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
