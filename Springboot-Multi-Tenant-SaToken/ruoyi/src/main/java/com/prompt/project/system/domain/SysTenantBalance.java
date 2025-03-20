package com.prompt.project.system.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
public class SysTenantBalance
        implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String tenantId;

    private BigDecimal balance;

    private Integer version;

    private Date updateTime;
}
