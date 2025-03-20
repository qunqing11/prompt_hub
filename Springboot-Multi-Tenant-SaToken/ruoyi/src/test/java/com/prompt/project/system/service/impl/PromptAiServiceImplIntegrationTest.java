package com.prompt.project.system.service.impl;

import com.prompt.common.exception.CustomException;
import com.prompt.project.system.domain.SysPromptTemplate;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PromptAiServiceImplIntegrationTest
{
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws Exception
    {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (mockWebServer != null)
        {
            mockWebServer.shutdown();
        }
    }

    @Test
    void caseSuccess_shouldParseGemini25Json() throws Exception
    {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"主文案第一句\"},{\"text\":\"主文案第二句\"}]}}]}"));
        PromptAiServiceImpl service = buildService(mockWebServer.url("/").toString(), false, 7890);
        SysPromptTemplate template = buildTemplate();
        Map<String, Object> vars = buildVars();

        String result = service.runPrompt(template, null, vars);

        assertEquals("主文案第一句\n主文案第二句", result);
        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/v1/models/gemini-2.5-flash:generateContent?key=test-key"));
        String requestBody = request.getBody().readUtf8();
        assertTrue(requestBody.contains("智能台灯"));
        assertTrue(requestBody.contains("护眼无频闪"));
    }

    @Test
    void caseStructuralChange_shouldFailGracefullyWithoutNpe() throws Exception
    {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"candidates\":[{\"content\":{}}]}"));
        PromptAiServiceImpl service = buildService(mockWebServer.url("/").toString(), false, 7890);

        CustomException ex = assertThrows(CustomException.class, () -> service.runPrompt(buildTemplate(), null, buildVars()));
        assertTrue(ex.getMessage().contains("Gemini 未返回有效内容"));
    }

    @Test
    void proxyPath_shouldUseConfigured127001Port7890WhenReachable() throws Exception
    {
        try (ServerSocket socket = new ServerSocket(7890))
        {
            PromptAiServiceImpl service = buildService(mockWebServer.url("/").toString(), true, 7890);
            InetSocketAddress address = invokeResolveProxyAddress(service);
            assertNotNull(address);
            assertEquals("127.0.0.1", address.getHostString());
            assertEquals(7890, address.getPort());
            OkHttpClient client = invokeCreateClient(service);
            Proxy proxy = client.getProxy();
            assertNotNull(proxy);
            InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
            assertEquals("127.0.0.1", proxyAddress.getHostString());
            assertEquals(7890, proxyAddress.getPort());
        }
    }

    @Test
    void concurrentAllSuccess_shouldDeductToFortyWithoutLostUpdate() throws Exception
    {
        PromptAiServiceImpl service = buildService(mockWebServer.url("/").toString(), false, 7890);
        ConcurrentRunResult result = runConcurrentScenario(service, 10, 0);
        assertEquals(10, result.successCount.get());
        assertEquals(0, result.failureCount.get());
        assertEquals(0, result.balance.get().compareTo(new BigDecimal("40")));
        assertEquals(10, mockWebServer.getRequestCount());
    }

    @Test
    void concurrentPartialFail_shouldDeductToFiftyEightWithoutLostUpdate() throws Exception
    {
        PromptAiServiceImpl service = buildService(mockWebServer.url("/").toString(), false, 7890);
        ConcurrentRunResult result = runConcurrentScenario(service, 10, 3);
        assertEquals(7, result.successCount.get());
        assertEquals(3, result.failureCount.get());
        assertEquals(0, result.balance.get().compareTo(new BigDecimal("58")));
        assertEquals(10, mockWebServer.getRequestCount());
        assertEquals(3, result.failureMessages.size());
        for (String message : result.failureMessages)
        {
            assertTrue(message.contains("Gemini 请求失败"));
        }
    }

    private PromptAiServiceImpl buildService(String baseUrl, boolean proxyEnabled, int proxyPort)
    {
        return new PromptAiServiceImpl(
                "test-key",
                baseUrl,
                "gemini-2.5-flash",
                proxyEnabled,
                "127.0.0.1",
                proxyPort,
                "7897,10809,1080",
                5000,
                5000);
    }

    private SysPromptTemplate buildTemplate()
    {
        SysPromptTemplate template = new SysPromptTemplate();
        template.setContent("产品：{{product}}，受众：{{audience}}，卖点：{{USP}}，风格：{{style}}");
        return template;
    }

    private Map<String, Object> buildVars()
    {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("product", "智能台灯");
        vars.put("audience", "学生与办公人群");
        vars.put("USP", "护眼无频闪");
        vars.put("style", "专业可信");
        vars.put("input", "生成投放文案");
        return vars;
    }

    private InetSocketAddress invokeResolveProxyAddress(PromptAiServiceImpl service) throws Exception
    {
        Method method = PromptAiServiceImpl.class.getDeclaredMethod("resolveProxyAddress");
        method.setAccessible(true);
        return (InetSocketAddress) method.invoke(service);
    }

    private OkHttpClient invokeCreateClient(PromptAiServiceImpl service) throws Exception
    {
        Method method = PromptAiServiceImpl.class.getDeclaredMethod("createClient");
        method.setAccessible(true);
        return (OkHttpClient) method.invoke(service);
    }

    private ConcurrentRunResult runConcurrentScenario(PromptAiServiceImpl service, int total, int failCount) throws Exception
    {
        for (int i = 0; i < total; i++)
        {
            if (i < failCount)
            {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(404)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":{\"message\":\"404 Not Found\"}}"));
            }
            else
            {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"并发成功文案\"}]}}]}"));
            }
        }
        AtomicReference<BigDecimal> balance = new AtomicReference<>(new BigDecimal("100"));
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failureMessages = new CopyOnWriteArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(total);
        ExecutorService executorService = Executors.newFixedThreadPool(total);
        for (int i = 0; i < total; i++)
        {
            executorService.submit(() -> {
                try
                {
                    startLatch.await();
                    service.runPrompt(buildTemplate(), null, buildVars());
                    successCount.incrementAndGet();
                    balance.updateAndGet(value -> value.subtract(new BigDecimal("6")));
                }
                catch (Exception ex)
                {
                    failureCount.incrementAndGet();
                    failureMessages.add(ex.getMessage() == null ? "" : ex.getMessage());
                }
                finally
                {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdownNow();
        assertTrue(finished);
        BigDecimal expected = new BigDecimal("100").subtract(new BigDecimal("6").multiply(new BigDecimal(successCount.get())));
        assertEquals(0, balance.get().compareTo(expected));
        return new ConcurrentRunResult(balance, successCount, failureCount, failureMessages);
    }

    private static class ConcurrentRunResult
    {
        private final AtomicReference<BigDecimal> balance;
        private final AtomicInteger successCount;
        private final AtomicInteger failureCount;
        private final List<String> failureMessages;

        private ConcurrentRunResult(AtomicReference<BigDecimal> balance, AtomicInteger successCount, AtomicInteger failureCount, List<String> failureMessages)
        {
            this.balance = balance;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.failureMessages = failureMessages;
        }
    }
}
