package com.prompt.framework.aspectj;

import cn.dev33.satoken.stp.StpUtil;
import com.prompt.common.exception.CustomException;
import com.prompt.common.utils.SecurityUtils;
import com.prompt.common.utils.StringUtils;
import com.prompt.framework.aspectj.lang.annotation.TenantRateLimiter;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 租户维度分布式限流切面
 *
 * @author admin
 */
@Aspect
@Order(2)
@Component
public class TenantRateLimiterAspect
{
    private static final Logger log = LoggerFactory.getLogger(TenantRateLimiterAspect.class);

    private static final int RATE_LIMIT_HTTP_CODE = 429;

    /**
     * 滑动窗口限流 Lua 脚本：ZSET 按时间戳计分，原子清理过期记录并判断是否放行
     */
    private static final String SLIDING_WINDOW_LUA =
            "local key = KEYS[1] "
                    + "local window = tonumber(ARGV[1]) "
                    + "local limit = tonumber(ARGV[2]) "
                    + "local now = tonumber(ARGV[3]) "
                    + "local clearBefore = now - window * 1000 "
                    + "redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore) "
                    + "local current = redis.call('ZCARD', key) "
                    + "if current < limit then "
                    + "  redis.call('ZADD', key, now, now .. '-' .. math.random(1000000)) "
                    + "  redis.call('PEXPIRE', key, window * 1000) "
                    + "  return 1 "
                    + "else "
                    + "  return 0 "
                    + "end";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();

    static
    {
        RATE_LIMIT_SCRIPT.setScriptText(SLIDING_WINDOW_LUA);
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Pointcut("@annotation(com.prompt.framework.aspectj.lang.annotation.TenantRateLimiter)")
    public void rateLimiterPointCut()
    {
    }

    @Around("rateLimiterPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable
    {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        TenantRateLimiter rateLimiter = method.getAnnotation(TenantRateLimiter.class);
        if (rateLimiter == null)
        {
            return point.proceed();
        }

        String tenantId = resolveTenantId();
        String limitKey = buildLimitKey(tenantId, rateLimiter, method);
        Long allowed = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                java.util.Collections.singletonList((Object) limitKey),
                String.valueOf(rateLimiter.window()),
                String.valueOf(rateLimiter.limit()),
                String.valueOf(System.currentTimeMillis()));

        if (allowed == null || allowed == 0L)
        {
            log.warn("租户 [{}] 触发限流, key={}", tenantId, limitKey);
            throw new CustomException("访问过于频繁，请稍后再试", RATE_LIMIT_HTTP_CODE);
        }

        return point.proceed();
    }

    private String resolveTenantId()
    {
        try
        {
            if (StpUtil.isLogin())
            {
                String comId = SecurityUtils.getCurrComId();
                if (StringUtils.isNotEmpty(comId))
                {
                    return comId;
                }
            }
        }
        catch (Exception ex)
        {
            log.debug("限流切面未获取到租户上下文，使用 anonymous: {}", ex.getMessage());
        }
        return "anonymous";
    }

    private String buildLimitKey(String tenantId, TenantRateLimiter rateLimiter, Method method)
    {
        String suffix = rateLimiter.key();
        if (StringUtils.isEmpty(suffix))
        {
            suffix = method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        }
        return "rate_limit:tenant:" + tenantId + ":" + suffix;
    }
}
