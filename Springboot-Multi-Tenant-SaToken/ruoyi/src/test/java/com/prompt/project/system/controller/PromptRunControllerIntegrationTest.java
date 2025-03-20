package com.prompt.project.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prompt.common.exception.CustomException;
import com.prompt.framework.security.LoginUser;
import com.prompt.project.system.domain.SysPromptTemplate;
import com.prompt.project.system.domain.SysTenantBalance;
import com.prompt.project.system.domain.SysUser;
import com.prompt.project.system.service.IPromptAiService;
import com.prompt.project.system.service.ISysPromptTemplateService;
import com.prompt.project.system.service.ISysTenantBalanceService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;

@WebMvcTest(controllers = PromptRunController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(locations = "classpath:application.yml")
public class PromptRunControllerIntegrationTest
{
    private static final String TENANT_ID = "tenant_test";
    private static final String TEMPLATE_ID = "tpl_adcopy";
    private static final BigDecimal TEMPLATE_PRICE = new BigDecimal("6");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ISysPromptTemplateService sysPromptTemplateService;

    @MockBean
    private ISysTenantBalanceService tenantBalanceService;

    @MockBean
    private IPromptAiService promptAiService;

    @BeforeEach
    void initLoginContext()
    {
        SysUser user = new SysUser();
        user.setComId(TENANT_ID);
        LoginUser loginUser = new LoginUser(user);
        StpUtil.login(10001L);
        StpUtil.getTokenSession().set(SaSession.USER, loginUser);
    }

    @Test
    void caseA_success_shouldGenerateAndDeductSixPoints() throws Exception
    {
        AtomicReference<SysTenantBalance> balanceRef = new AtomicReference<>(buildBalance("20"));
        mockCommonTemplateAndBalance(balanceRef);
        when(promptAiService.runPrompt(any(SysPromptTemplate.class), anyString(), anyMap())).thenReturn("这是高转化成品文案");

        mockMvc.perform(post("/prompt/run")
                        .contentType("application/json")
                        .content(buildRunRequestJson("智能台灯", "学生与办公人群", "护眼无频闪", "专业可信")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("这是高转化成品文案"))
                .andExpect(jsonPath("$.data.result").value("这是高转化成品文案"))
                .andExpect(jsonPath("$.data.balanceInfo.balance").value(14));

        ArgumentCaptor<BigDecimal> captor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(tenantBalanceService).deductBalance(eq(TENANT_ID), captor.capture());
        assertEquals(0, captor.getValue().compareTo(TEMPLATE_PRICE));
        assertEquals(0, balanceRef.get().getBalance().compareTo(new BigDecimal("14")));
    }

    @Test
    void caseB_apiFail_shouldReturnFriendlyErrorAndNoDeduct() throws Exception
    {
        AtomicReference<SysTenantBalance> balanceRef = new AtomicReference<>(buildBalance("20"));
        mockCommonTemplateAndBalance(balanceRef);
        when(promptAiService.runPrompt(any(SysPromptTemplate.class), anyString(), anyMap()))
                .thenThrow(new CustomException("Gemini 请求失败：404 Not Found"));

        mockMvc.perform(post("/prompt/run")
                        .contentType("application/json")
                        .content(buildRunRequestJson("智能台灯", "学生与办公人群", "护眼无频闪", "专业可信")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg", containsString("Gemini 请求失败：404 Not Found")));

        verify(tenantBalanceService, never()).deductBalance(anyString(), any(BigDecimal.class));
        assertEquals(0, balanceRef.get().getBalance().compareTo(new BigDecimal("20")));
    }

    @Test
    void caseC_emptyResponse_shouldReturnFriendlyErrorAndNoDeduct() throws Exception
    {
        AtomicReference<SysTenantBalance> balanceRef = new AtomicReference<>(buildBalance("20"));
        mockCommonTemplateAndBalance(balanceRef);
        when(promptAiService.runPrompt(any(SysPromptTemplate.class), anyString(), anyMap()))
                .thenThrow(new CustomException("Gemini 未返回有效内容"));

        mockMvc.perform(post("/prompt/run")
                        .contentType("application/json")
                        .content(buildRunRequestJson("智能台灯", "学生与办公人群", "护眼无频闪", "专业可信")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg", containsString("Gemini 未返回有效内容")));

        verify(tenantBalanceService, never()).deductBalance(anyString(), any(BigDecimal.class));
        assertEquals(0, balanceRef.get().getBalance().compareTo(new BigDecimal("20")));
    }

    private void mockCommonTemplateAndBalance(AtomicReference<SysTenantBalance> balanceRef)
    {
        SysPromptTemplate template = new SysPromptTemplate();
        template.setId(TEMPLATE_ID);
        template.setStatus("0");
        template.setPrice(TEMPLATE_PRICE);
        template.setContent("产品：{{product}}，受众：{{audience}}，卖点：{{USP}}，风格：{{style}}");
        when(sysPromptTemplateService.selectSysPromptTemplateById(eq(TEMPLATE_ID), eq(TENANT_ID))).thenReturn(template);
        when(tenantBalanceService.selectByTenantId(eq(TENANT_ID))).thenAnswer(invocation -> balanceRef.get());
        doAnswer(invocation -> {
            BigDecimal amount = invocation.getArgument(1);
            SysTenantBalance current = balanceRef.get();
            SysTenantBalance updated = buildBalance(current.getBalance().subtract(amount).toPlainString());
            balanceRef.set(updated);
            return null;
        }).when(tenantBalanceService).deductBalance(eq(TENANT_ID), any(BigDecimal.class));
    }

    private SysTenantBalance buildBalance(String amount)
    {
        SysTenantBalance balance = new SysTenantBalance();
        balance.setTenantId(TENANT_ID);
        balance.setBalance(new BigDecimal(amount));
        balance.setVersion(0);
        return balance;
    }

    private String buildRunRequestJson(String product, String audience, String usp, String style) throws Exception
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateId", TEMPLATE_ID);
        payload.put("inputText", "根据变量生成高转化投放文案");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("product", product);
        vars.put("audience", audience);
        vars.put("USP", usp);
        vars.put("style", style);
        payload.put("vars", vars);
        String json = objectMapper.writeValueAsString(payload);
        assertTrue(json.contains("\"product\":\"智能台灯\""));
        assertTrue(json.contains("\"USP\":\"护眼无频闪\""));
        return json;
    }
}
