package com.prompt.framework.datasource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.prompt.common.utils.StringUtils;
import com.prompt.framework.aspectj.lang.enums.DataSourceType;
import com.prompt.framework.redis.RedisCache;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 多租户数据源路由解析器（Caffeine 本地缓存 + Redis 二级映射）
 *
 * @author admin
 */
@Component
public class TenantDataSourceRouter
{
    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceRouter.class);

    private static final String REDIS_DS_KEY_PREFIX = "tenant:ds:";

    private final Cache<String, String> dataSourceCache;

    @Autowired
    private RedisCache redisCache;

    public TenantDataSourceRouter(
            @Value("${tenant.datasource.cache.max-size:1000}") int maxSize,
            @Value("${tenant.datasource.cache.expire-seconds:300}") int expireSeconds)
    {
        this.dataSourceCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 解析租户对应的数据源 Key，优先命中 Caffeine，未命中则查 Redis 并回填
     */
    public String resolveDataSourceKey(String comId)
    {
        if (StringUtils.isEmpty(comId))
        {
            return DataSourceType.MASTER.name();
        }

        String cached = dataSourceCache.getIfPresent(comId);
        if (StringUtils.isNotEmpty(cached))
        {
            log.debug("Caffeine 命中租户数据源路由: comId={}, ds={}", comId, cached);
            return cached;
        }

        String redisKey = REDIS_DS_KEY_PREFIX + comId;
        String redisValue = redisCache.getCacheObject(redisKey);
        if (StringUtils.isNotEmpty(redisValue))
        {
            dataSourceCache.put(comId, redisValue);
            log.debug("Redis 命中租户数据源路由: comId={}, ds={}", comId, redisValue);
            return redisValue;
        }

        String resolved = lookupDataSourceKey(comId);
        dataSourceCache.put(comId, resolved);
        log.debug("底层解析租户数据源路由: comId={}, ds={}", comId, resolved);
        return resolved;
    }

    /**
     * 底层路由策略：默认主库，可按租户扩展至独立物理库
     */
    protected String lookupDataSourceKey(String comId)
    {
        return DataSourceType.MASTER.name();
    }

    public void evictCache(String comId)
    {
        if (StringUtils.isNotEmpty(comId))
        {
            dataSourceCache.invalidate(comId);
        }
    }
}
