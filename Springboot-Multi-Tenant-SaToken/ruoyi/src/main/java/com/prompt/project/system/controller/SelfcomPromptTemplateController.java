package com.prompt.project.system.controller;

import com.prompt.common.exception.CustomException;
import com.prompt.common.utils.StringUtils;
import com.prompt.common.utils.SecurityUtils;
import com.prompt.framework.aspectj.lang.annotation.Log;
import com.prompt.framework.aspectj.lang.enums.BusinessType;
import com.prompt.framework.web.controller.BaseController;
import com.prompt.framework.web.domain.AjaxResult;
import com.prompt.framework.web.page.TableDataInfo;
import com.prompt.project.system.domain.SysPromptTemplate;
import com.prompt.project.system.service.ISysPromptTemplateService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/selfcom/promptTemplate")
public class SelfcomPromptTemplateController extends BaseController
{
    @Autowired
    private ISysPromptTemplateService sysPromptTemplateService;

    @SaCheckPermission("selfcom:promptTemplate:list")
    @GetMapping("/list")
    public TableDataInfo list(SysPromptTemplate sysPromptTemplate)
    {
        sysPromptTemplate.setComId(resolveComId(sysPromptTemplate.getComId()));
        startPage();
        List<SysPromptTemplate> list = sysPromptTemplateService.selectSysPromptTemplateList(sysPromptTemplate);
        return getDataTable(list);
    }

    @SaCheckPermission("selfcom:promptTemplate:query")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable String id, @RequestParam(required = false) String comId)
    {
        return AjaxResult.success(sysPromptTemplateService.selectSysPromptTemplateById(id, resolveComId(comId)));
    }

    @SaCheckPermission("selfcom:promptTemplate:add")
    @Log(title = "Prompt模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysPromptTemplate sysPromptTemplate)
    {
        sysPromptTemplate.setComId(resolveComId(sysPromptTemplate.getComId()));
        sysPromptTemplate.setCreateBy(SecurityUtils.getUsername());
        sysPromptTemplate.setStatus("0");
        if (sysPromptTemplate.getPrice() == null)
        {
            sysPromptTemplate.setPrice(BigDecimal.ZERO);
        }
        return toAjax(sysPromptTemplateService.insertSysPromptTemplate(sysPromptTemplate));
    }

    @SaCheckPermission("selfcom:promptTemplate:edit")
    @Log(title = "Prompt模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysPromptTemplate sysPromptTemplate)
    {
        sysPromptTemplate.setComId(resolveComId(sysPromptTemplate.getComId()));
        sysPromptTemplate.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(sysPromptTemplateService.updateSysPromptTemplate(sysPromptTemplate));
    }

    @SaCheckPermission("selfcom:promptTemplate:remove")
    @Log(title = "Prompt模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable String[] ids, @RequestParam(required = false) String comId)
    {
        return toAjax(sysPromptTemplateService.deleteSysPromptTemplateByIds(ids, resolveComId(comId)));
    }

    private String resolveComId(String requestComId)
    {
        String sessionComId = SecurityUtils.getCurrComId();
        if (StringUtils.isNotEmpty(sessionComId))
        {
            return sessionComId;
        }
        if (StringUtils.isNotEmpty(requestComId))
        {
            return requestComId;
        }
        throw new CustomException("comId不能为空");
    }
}
