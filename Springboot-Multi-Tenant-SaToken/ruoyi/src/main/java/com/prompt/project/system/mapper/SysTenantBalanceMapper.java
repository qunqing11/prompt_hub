package com.prompt.project.system.mapper;

import com.prompt.project.system.domain.SysTenantBalance;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface SysTenantBalanceMapper
{
    SysTenantBalance selectByTenantId(@Param("tenantId") String tenantId);

    int insertTenantBalance(SysTenantBalance tenantBalance);

    int deductBalance(@Param("tenantId") String tenantId, @Param("amount") BigDecimal amount, @Param("version") Integer version);
}

