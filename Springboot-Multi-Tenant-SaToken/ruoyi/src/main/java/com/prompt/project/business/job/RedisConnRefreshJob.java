package com.prompt.project.business.job;

import com.prompt.framework.redis.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("RedisConnRefreshJob")
public class RedisConnRefreshJob {

    @Autowired
    private RedisCache redisCache;

    /**
     * 刷新redis连接
     *
     * @throws Exception
     */
    public void doExecute() throws Exception {
        redisCache.getCacheMap("1");
    }
    
}
