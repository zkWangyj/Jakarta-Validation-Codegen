package com.firmae.validation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 嵌套校验标记注解
 *
 * <p>标注在自定义类类型的参数上，表示需要对该参数对象的字段进行递归校验。
 * 内部使用 Hibernate Validator 进行标准 Bean Validation 校验。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 基本用法
 * &#64;PreCompile
 * public void createUser(&#64;Validated UserDTO user) {
 *     // 校验 UserDTO 所有字段
 * }
 *
 * // 分组校验
 * public interface CreateGroup {}
 * public interface UpdateGroup {}
 *
 * &#64;PreCompile
 * public void createUser(&#64;Validated(groups = CreateGroup.class) UserDTO user) {
 *     // 只校验 UserDTO 中属于 CreateGroup 分组的字段
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
public @interface Validated {

    /**
     * 校验分组，用于选择性校验字段
     *
     * @see jakarta.validation.groups.Default
     */
    Class<?>[] groups() default {};
}
