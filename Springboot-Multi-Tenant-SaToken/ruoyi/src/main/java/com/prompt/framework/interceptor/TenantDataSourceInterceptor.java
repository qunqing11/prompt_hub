package com.prompt.framework.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.prompt.common.utils.SecurityUtils;
import com.prompt.common.utils.StringUtils;
import com.prompt.framework.datasource.DynamicDataSourceContextHolder;
import com.prompt.framework.datasource.TenantDataSourceRouter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 多租户数据源路由拦截器：请求入口解析租户并切换数据源
 *
 * @author admin
 */
@Component
public class TenantDataSourceInterceptor implements HandlerInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceInterceptor.class);

    private static final ThreadLocal<Boolean> TENANT_DS_BOUND = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Autowired
    private TenantDataSourceRouter tenantDataSourceRouter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        String comId = resolveTenantId();
        if (StringUtils.isEmpty(comId))
        {
            return true;
        }

        String dataSourceKey = tenantDataSourceRouter.resolveDataSourceKey(comId);
        DynamicDataSourceContextHolder.setDataSourceType(dataSourceKey);
        TENANT_DS_BOUND.set(Boolean.TRUE);
        log.debug("租户 [{}] 路由至数据源 [{}]", comId, dataSourceKey);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    {
        if (Boolean.TRUE.equals(TENANT_DS_BOUND.get()))
        {
            DynamicDataSourceContextHolder.clearDataSourceType();
            TENANT_DS_BOUND.remove();
        }
    }

    private String resolveTenantId()
    {
        try
        {
            if (StpUtil.isLogin())
            {
                return SecurityUtils.getCurrComId();
            }
        }
        catch (Exception ignored)
        {
            // 未登录请求跳过租户数据源绑定
        }
        return null;
    }
}
