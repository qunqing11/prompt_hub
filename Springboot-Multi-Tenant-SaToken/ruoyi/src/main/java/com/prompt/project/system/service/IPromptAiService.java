package com.prompt.project.system.service;

import com.prompt.project.system.domain.SysPromptTemplate;
import java.util.Map;
import java.util.function.Consumer;

public interface IPromptAiService
{
    String runPrompt(SysPromptTemplate template, String inputText, Map<String, Object> vars);

    String runPromptStream(SysPromptTemplate template, String inputText, Map<String, Object> vars, Consumer<String> chunkConsumer);
}
