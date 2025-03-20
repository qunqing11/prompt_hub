package com.prompt.project.system.service.impl;

import com.prompt.common.utils.KeyUtils;
import com.prompt.project.system.domain.SysPromptTemplate;
import com.prompt.project.system.mapper.SysPromptTemplateMapper;
import com.prompt.project.system.service.ISysPromptTemplateService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysPromptTemplateServiceImpl implements ISysPromptTemplateService
{
    @Autowired
    private SysPromptTemplateMapper sysPromptTemplateMapper;

    @Override
    public SysPromptTemplate selectSysPromptTemplateById(String id, String comId)
    {
        return sysPromptTemplateMapper.selectSysPromptTemplateById(id, comId);
    }

    @Override
    public List<SysPromptTemplate> selectSysPromptTemplateList(SysPromptTemplate sysPromptTemplate)
    {
        return sysPromptTemplateMapper.selectSysPromptTemplateList(sysPromptTemplate);
    }

    @Override
    public int insertSysPromptTemplate(SysPromptTemplate sysPromptTemplate)
    {
        sysPromptTemplate.setId(KeyUtils.genUniqueKey());
        return sysPromptTemplateMapper.insertSysPromptTemplate(sysPromptTemplate);
    }

    @Override
    public int updateSysPromptTemplate(SysPromptTemplate sysPromptTemplate)
    {
        return sysPromptTemplateMapper.updateSysPromptTemplate(sysPromptTemplate);
    }

    @Override
    public int deleteSysPromptTemplateById(String id, String comId)
    {
        return sysPromptTemplateMapper.deleteSysPromptTemplateById(id, comId);
    }

    @Override
    public int deleteSysPromptTemplateByIds(String[] ids, String comId)
    {
        return sysPromptTemplateMapper.deleteSysPromptTemplateByIds(ids, comId);
    }
}

