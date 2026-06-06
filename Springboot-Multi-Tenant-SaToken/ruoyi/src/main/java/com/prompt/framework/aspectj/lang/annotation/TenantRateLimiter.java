package com.prompt.framework.aspectj.lang.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户维度分布式限流注解（Redis + Lua 滑动窗口）
 *
 * @author admin
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantRateLimiter
{
    /**
     * 限流时间窗口（秒）
     */
    int window() default 60;

    /**
     * 窗口内最大请求次数
     */
    int limit() default 100;

    /**
     * 自定义限流 Key 后缀，为空时使用类名+方法名
     */
    String key() default "";
}
