package com.prompt.project.system.controller;

import com.prompt.common.utils.SecurityUtils;
import com.prompt.framework.web.controller.BaseController;
import com.prompt.framework.web.domain.AjaxResult;
import com.prompt.project.system.domain.SysTenantBalance;
import com.prompt.project.system.service.ISysTenantBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/selfcom/tenantBalance")
public class SelfcomTenantBalanceController extends BaseController {

    @Autowired
    private ISysTenantBalanceService sysTenantBalanceService;

    @GetMapping("/info")
    public AjaxResult getInfo() {
        String comId = SecurityUtils.getCurrComId();
        SysTenantBalance balance = sysTenantBalanceService.selectByTenantId(comId);
        if (balance == null) {
            balance = new SysTenantBalance();
            balance.setTenantId(comId);
            balance.setBalance(new java.math.BigDecimal("100.00")); // Give default balance
            balance.setVersion(0);
            sysTenantBalanceService.insertTenantBalance(balance);
        }
        return AjaxResult.success(balance);
    }
}
