package com.prompt.project.system.service;

import com.prompt.project.system.domain.SysPromptTemplate;
import java.util.List;

public interface ISysPromptTemplateService
{
    SysPromptTemplate selectSysPromptTemplateById(String id, String comId);

    List<SysPromptTemplate> selectSysPromptTemplateList(SysPromptTemplate sysPromptTemplate);

    int insertSysPromptTemplate(SysPromptTemplate sysPromptTemplate);

    int updateSysPromptTemplate(SysPromptTemplate sysPromptTemplate);

    int deleteSysPromptTemplateById(String id, String comId);

    int deleteSysPromptTemplateByIds(String[] ids, String comId);
}

