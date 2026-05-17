package com.firmae.validation.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 启用编译时校验的标记注解
 *
 * <p>只有标注了 @PreCompile 的方法、构造器或 Record 才会被处理，
 * 生成对应的校验调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 标记在方法上
 * &#64;PreCompile
 * public void createUser(&#64;NotNull String username, &#64;Email String email) { ... }
 *
 * // 标记在构造器上
 * &#64;PreCompile
 * public UserService(&#64;NotNull String serviceName) { ... }
 *
 * // 标记在 Record 上（对 canonical constructor 生效）
 * &#64;PreCompile
 * public record UserRecord(&#64;NotNull String userId, &#64;Email String email) { }
 * </pre>
 */
@Retention(RetentionPolicy.SOURCE)
public @interface PreCompile {
}
