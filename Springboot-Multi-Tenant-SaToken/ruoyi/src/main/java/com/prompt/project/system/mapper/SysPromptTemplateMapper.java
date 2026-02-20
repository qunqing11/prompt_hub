package com.prompt.project.system.mapper;

import com.prompt.project.system.domain.SysPromptTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysPromptTemplateMapper
{
    SysPromptTemplate selectSysPromptTemplateById(@Param("id") String id, @Param("comId") String comId);

    List<SysPromptTemplate> selectSysPromptTemplateList(SysPromptTemplate sysPromptTemplate);

    int insertSysPromptTemplate(SysPromptTemplate sysPromptTemplate);

    int updateSysPromptTemplate(SysPromptTemplate sysPromptTemplate);

    int deleteSysPromptTemplateById(@Param("id") String id, @Param("comId") String comId);

    int deleteSysPromptTemplateByIds(@Param("ids") String[] ids, @Param("comId") String comId);
}

