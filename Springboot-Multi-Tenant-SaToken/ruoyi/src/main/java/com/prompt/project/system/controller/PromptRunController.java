package com.prompt.project.system.controller;

import com.prompt.common.exception.CustomException;
import com.prompt.common.utils.SecurityUtils;
import com.prompt.common.utils.StringUtils;
import com.prompt.framework.web.domain.AjaxResult;
import com.prompt.project.system.domain.SysPromptTemplate;
import com.prompt.project.system.domain.SysTenantBalance;
import com.prompt.project.system.service.IPromptAiService;
import com.prompt.project.system.service.ISysPromptTemplateService;
import com.prompt.project.system.service.ISysTenantBalanceService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping({"/prompt", "/dev-api/prompt", "/prod-api/prompt"})
public class PromptRunController
{
    @Autowired
    private ISysPromptTemplateService sysPromptTemplateService;

    @Autowired
    private ISysTenantBalanceService tenantBalanceService;

    @Autowired
    private IPromptAiService promptAiService;

    @PostMapping("/run")
    public AjaxResult run(@Validated @RequestBody PromptRunRequest request)
    {
        String comId = resolveComId();
        SysPromptTemplate template = getAvailableTemplate(request.getTemplateId(), comId);
        BigDecimal price = template.getPrice();
        if (price == null)
        {
            price = BigDecimal.ZERO;
        }
        ensureSufficientBalance(comId, price);
        String result = promptAiService.runPrompt(template, request.getInputText(), request.getVars());
        return AjaxResult.success(result, buildAndSaveRunResult(comId, price, result));
    }

    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(@Validated @RequestBody PromptRunRequest request)
    {
        String comId = resolveComId();
        SysPromptTemplate template = getAvailableTemplate(request.getTemplateId(), comId);
        BigDecimal price = template.getPrice();
        if (price == null)
        {
            price = BigDecimal.ZERO;
        }
        ensureSufficientBalance(comId, price);
        SseEmitter emitter = new SseEmitter(0L);
        final BigDecimal finalPrice = price;
        CompletableFuture.runAsync(() -> {
            try
            {
                String result = promptAiService.runPromptStream(template, request.getInputText(), request.getVars(), chunk -> {
                    if (StringUtils.isBlank(chunk))
                    {
                        return;
                    }
                    sendEvent(emitter, "chunk", chunkEventData(chunk));
                });
                PromptRunResponse response = buildAndSaveRunResult(comId, finalPrice, result);
                sendEvent(emitter, "done", doneEventData(response));
                emitter.complete();
            }
            catch (Exception ex)
            {
                sendEvent(emitter, "error", errorEventData(ex));
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private SysPromptTemplate getAvailableTemplate(String templateId, String comId)
    {
        SysPromptTemplate template = sysPromptTemplateService.selectSysPromptTemplateById(templateId, comId);
        if (template == null)
        {
            throw new CustomException("模板不存在");
        }
        if (!"0".equals(template.getStatus()))
        {
            throw new CustomException("模板已停用");
        }
        return template;
    }

    private PromptRunResponse buildAndSaveRunResult(String comId, BigDecimal price, String result)
    {
        tenantBalanceService.deductBalance(comId, price);
        SysTenantBalance balance = tenantBalanceService.selectByTenantId(comId);
        PromptRunResponse response = new PromptRunResponse();
        response.setResult(result);
        response.setReply(result);
        if (balance != null)
        {
            BalanceInfo balanceInfo = new BalanceInfo();
            balanceInfo.setBalance(balance.getBalance());
            balanceInfo.setUpdateTime(balance.getUpdateTime());
            response.setBalanceInfo(balanceInfo);
        }
        if (StringUtils.isBlank(response.getResult()))
        {
            response.setResult("[AI回复] 已处理完成。");
            response.setReply(response.getResult());
        }
        return response;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data)
    {
        try
        {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private Map<String, Object> chunkEventData(String chunk)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", chunk);
        return data;
    }

    private Map<String, Object> doneEventData(PromptRunResponse response)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", response.getResult());
        data.put("reply", response.getReply());
        data.put("balanceInfo", response.getBalanceInfo());
        return data;
    }

    private Map<String, Object> errorEventData(Exception ex)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        if (ex instanceof CustomException)
        {
            data.put("message", ex.getMessage());
            return data;
        }
        data.put("message", "运行失败：" + ex.getMessage());
        return data;
    }

    private void ensureSufficientBalance(String comId, BigDecimal price)
    {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
        {
            return;
        }
        SysTenantBalance balance = tenantBalanceService.selectByTenantId(comId);
        if (balance == null)
        {
            throw new CustomException("租户余额不存在");
        }
        if (balance.getBalance() == null || balance.getBalance().compareTo(price) < 0)
        {
            throw new CustomException("余额不足");
        }
    }

    private String resolveComId()
    {
        String comId = SecurityUtils.getCurrComId();
        if (StringUtils.isEmpty(comId))
        {
            throw new CustomException("未登录或租户信息缺失");
        }
        return comId;
    }

    @Data
    public static class PromptRunRequest
    {
        @NotBlank
        private String templateId;
        private String inputText;
        private Map<String, Object> vars;
    }

    @Data
    public static class PromptRunResponse
    {
        private String reply;
        private String result;
        private BalanceInfo balanceInfo;
    }

    @Data
    public static class BalanceInfo
    {
        private BigDecimal balance;
        private java.util.Date updateTime;
    }
}
