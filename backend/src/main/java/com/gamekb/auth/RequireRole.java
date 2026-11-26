package com.gamekb.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解，配合 AuthInterceptor 使用。
 * 可标在类上（整个 Controller 生效）或方法上（单个接口生效，优先级更高）。
 *
 * <pre>
 * &#64;RequireRole("ADMIN")
 * public Result&lt;?&gt; delete(...) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色，默认仅管理员 */
    String[] value() default {"ADMIN"};
}
