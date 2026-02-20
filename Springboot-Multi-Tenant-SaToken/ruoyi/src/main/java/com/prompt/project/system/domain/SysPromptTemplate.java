package com.prompt.project.system.domain;

import com.prompt.framework.web.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class SysPromptTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String id;

    private String comId;

    private String title;

    private String content;

    private BigDecimal price;

    private String status;
}

