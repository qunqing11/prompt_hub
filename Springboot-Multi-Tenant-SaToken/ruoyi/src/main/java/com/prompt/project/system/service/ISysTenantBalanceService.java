package com.prompt.project.system.service;

import com.prompt.project.system.domain.SysTenantBalance;
import java.math.BigDecimal;

public interface ISysTenantBalanceService
{
    SysTenantBalance selectByTenantId(String tenantId);

    int insertTenantBalance(SysTenantBalance tenantBalance);

    void deductBalance(String tenantId, BigDecimal amount);
}

