package com.prompt.project.monitor.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.TokenSign;
import cn.dev33.satoken.stp.StpUtil;
import com.prompt.framework.web.page.PageDomain;
import com.prompt.framework.web.page.TableSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.prompt.common.constant.Constants;
import com.prompt.common.utils.StringUtils;
import com.prompt.framework.aspectj.lang.annotation.Log;
import com.prompt.framework.aspectj.lang.enums.BusinessType;
import com.prompt.framework.redis.RedisCache;
import com.prompt.framework.security.LoginUser;
import com.prompt.framework.web.controller.BaseController;
import com.prompt.framework.web.domain.AjaxResult;
import com.prompt.framework.web.page.TableDataInfo;
import com.prompt.project.monitor.domain.SysUserOnline;
import com.prompt.project.system.service.ISysUserOnlineService;

/**
 * 在线用户监控
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/monitor/online")
public class SysUserOnlineController extends BaseController
{
    @Autowired
    private ISysUserOnlineService userOnlineService;


    @SaCheckPermission("monitor:online:list")
    @GetMapping("/list")
    public TableDataInfo list(String userId)
    {
        List<SysUserOnline> userOnlineList = userOnlineService.getOnlineUser(userId, TableSupport.buildPageRequest());
        Collections.reverse(userOnlineList);
        userOnlineList.removeAll(Collections.singleton(null));
        return getDataTable(userOnlineList);
    }

    /**
     * 强退用户，按用户ID踢
     */
    @SaCheckPermission("monitor:online:forceLogout")
    @Log(title = "在线用户", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userId}/{device}")
    public AjaxResult forceLogout(@PathVariable String userId, @PathVariable String device)
    {
        //按用户ID踢下线（所有设备下线）
        if (StringUtils.isEmpty(device)) {
            StpUtil.kickout(userId);
        }else {
            StpUtil.kickout(userId, device);
        }
        return AjaxResult.success();
    }
}
