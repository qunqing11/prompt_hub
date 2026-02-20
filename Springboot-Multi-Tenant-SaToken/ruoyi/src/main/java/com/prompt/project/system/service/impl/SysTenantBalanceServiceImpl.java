package com.prompt.project.system.service.impl;

import com.prompt.common.exception.CustomException;
import com.prompt.project.system.domain.SysTenantBalance;
import com.prompt.project.system.mapper.SysTenantBalanceMapper;
import com.prompt.project.system.service.ISysTenantBalanceService;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysTenantBalanceServiceImpl implements ISysTenantBalanceService
{
    private static final int MAX_DEDUCT_RETRY = 3;

    @Autowired
    private SysTenantBalanceMapper tenantBalanceMapper;

    @Override
    public SysTenantBalance selectByTenantId(String tenantId)
    {
        return tenantBalanceMapper.selectByTenantId(tenantId);
    }

    @Override
    public int insertTenantBalance(SysTenantBalance tenantBalance)
    {
        if (tenantBalance.getBalance() == null)
        {
            tenantBalance.setBalance(BigDecimal.ZERO);
        }
        if (tenantBalance.getVersion() == null)
        {
            tenantBalance.setVersion(0);
        }
        return tenantBalanceMapper.insertTenantBalance(tenantBalance);
    }

    @Override
    public void deductBalance(String tenantId, BigDecimal amount)
    {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            return;
        }
        for (int i = 0; i < MAX_DEDUCT_RETRY; i++)
        {
            SysTenantBalance balance = tenantBalanceMapper.selectByTenantId(tenantId);
            if (balance == null)
            {
                throw new CustomException("租户余额不存在");
            }
            if (balance.getBalance() == null || balance.getBalance().compareTo(amount) < 0)
            {
                throw new CustomException("余额不足");
            }
            int rows = tenantBalanceMapper.deductBalance(tenantId, amount, balance.getVersion());
            if (rows > 0)
            {
                return;
            }
        }
        throw new CustomException("扣费失败，请重试");
    }
}

