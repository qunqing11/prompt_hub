package com.prompt.project.system.domain;

import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.prompt.framework.aspectj.lang.annotation.Excel;
import com.prompt.framework.web.domain.BaseEntity;

import java.util.List;

/**
 * 权限模板对象 sys_template
 * 
 * @author ruoyi
 * @date 2020-07-09
 */
@Data
public class SysTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private String id;

    /** 模板名称 */
    @Excel(name = "模板名称")
    private String name;

    private List<Integer> menuIds;

}
