package com.prompt.project.system.service.impl;

import com.prompt.common.exception.CustomException;
import com.prompt.common.utils.StringUtils;
import com.prompt.project.system.domain.SysPromptTemplate;
import com.prompt.project.system.service.IPromptAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PromptAiServiceImpl implements IPromptAiService
{
    private static final Logger log = LoggerFactory.getLogger(PromptAiServiceImpl.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean proxyEnabled;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyFallbackPorts;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public PromptAiServiceImpl(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${gemini.model:gemini-1.5-flash-latest}") String model,
            @Value("${gemini.proxy.enabled:true}") boolean proxyEnabled,
            @Value("${gemini.proxy.host:127.0.0.1}") String proxyHost,
            @Value("${gemini.proxy.port:7890}") int proxyPort,
            @Value("${gemini.proxy.fallback-ports:7897,10809,1080}") String proxyFallbackPorts,
            @Value("${gemini.timeout.connect-ms:20000}") int connectTimeoutMs,
            @Value("${gemini.timeout.read-ms:120000}") int readTimeoutMs)
    {
        this.apiKey = normalizeApiKey(apiKey);
        this.baseUrl = baseUrl;
        this.model = normalizeModel(model);
        this.proxyEnabled = proxyEnabled;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyFallbackPorts = proxyFallbackPorts;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        log.info("Gemini 配置已就绪，当前 Key：{}", maskApiKey(this.apiKey));
        log.info("Gemini 当前模型：{}", this.model);
        log.info("Gemini 代理配置：enabled={}, host={}, port={}, fallbackPorts={}", this.proxyEnabled, this.proxyHost, this.proxyPort, this.proxyFallbackPorts);
    }

    @Override
    public String runPrompt(SysPromptTemplate template, String inputText, Map<String, Object> vars)
    {
        if (StringUtils.isBlank(apiKey))
        {
            throw new CustomException("请先在配置中填写 gemini.api-key，或设置环境变量 GEMINI_API_KEY");
        }
        String context = template == null ? null : template.getContent();
        context = applyTemplateVariables(context, vars);
        if (StringUtils.isBlank(context))
        {
            throw new CustomException("模板内容为空，无法生成上下文");
        }
        String userMessage = resolveUserMessage(inputText, vars);
        if (StringUtils.isBlank(userMessage))
        {
            throw new CustomException("测试内容不能为空");
        }
        try
        {
            String content = callGemini(context.trim(), userMessage.trim());
            if (StringUtils.isBlank(content))
            {
                throw new CustomException("Gemini 未返回有效内容");
            }
            return sanitizeResponseText(content);
        }
        catch (CustomException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new CustomException("Gemini 调用失败：" + ex.getMessage());
        }
    }

    @Override
    public String runPromptStream(SysPromptTemplate template, String inputText, Map<String, Object> vars, Consumer<String> chunkConsumer)
    {
        if (StringUtils.isBlank(apiKey))
        {
            throw new CustomException("请先在配置中填写 gemini.api-key，或设置环境变量 GEMINI_API_KEY");
        }
        String context = template == null ? null : template.getContent();
        context = applyTemplateVariables(context, vars);
        if (StringUtils.isBlank(context))
        {
            throw new CustomException("模板内容为空，无法生成上下文");
        }
        String userMessage = resolveUserMessage(inputText, vars);
        if (StringUtils.isBlank(userMessage))
        {
            throw new CustomException("测试内容不能为空");
        }
        try
        {
            String content = callGeminiStream(context.trim(), userMessage.trim(), chunkConsumer);
            if (StringUtils.isBlank(content))
            {
                throw new CustomException("Gemini 未返回有效内容");
            }
            return sanitizeResponseText(content);
        }
        catch (CustomException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new CustomException("Gemini 调用失败：" + ex.getMessage());
        }
    }

    private String callGemini(String context, String userMessage) throws IOException
    {
        OkHttpClient client = createClient();
        String requestBody = buildRequestBody(context, userMessage);
        String requestUrl = buildRequestUrl();
        System.out.println("请求的完整URL是：" + requestUrl);
        Request request = new Request.Builder()
                .url(requestUrl)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JSON, requestBody))
                .build();
        Response response = null;
        try
        {
            response = client.newCall(request).execute();
            String responseBody = response.body() == null ? "" : response.body().string();
            log.info("Gemini Raw Response: {}", responseBody);
            if (response.isSuccessful())
            {
                String text = extractText(responseBody);
                if (StringUtils.isBlank(text))
                {
                    throw new CustomException("Gemini 未返回可展示文本");
                }
                return text;
            }
            throw new CustomException(resolveErrorMessage(response.code(), responseBody));
        }
        finally
        {
            if (response != null && response.body() != null)
            {
                response.body().close();
            }
        }
    }

    private String callGeminiStream(String context, String userMessage, Consumer<String> chunkConsumer) throws IOException
    {
        OkHttpClient client = createClient();
        String requestBody = buildRequestBody(context, userMessage);
        String requestUrl = buildStreamRequestUrl();
        Request request = new Request.Builder()
                .url(requestUrl)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JSON, requestBody))
                .build();
        Response response = null;
        try
        {
            response = client.newCall(request).execute();
            if (!response.isSuccessful())
            {
                String responseBody = response.body() == null ? "" : response.body().string();
                throw new CustomException(resolveErrorMessage(response.code(), responseBody));
            }
            if (response.body() == null)
            {
                throw new CustomException("Gemini 流式响应为空");
            }
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (!line.startsWith("data:"))
                {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (StringUtils.isBlank(payload) || "[DONE]".equalsIgnoreCase(payload))
                {
                    continue;
                }
                String chunk = extractStreamChunkText(payload);
                if (StringUtils.isBlank(chunk))
                {
                    continue;
                }
                builder.append(chunk);
                if (chunkConsumer != null)
                {
                    chunkConsumer.accept(chunk);
                }
            }
            return builder.toString();
        }
        finally
        {
            if (response != null && response.body() != null)
            {
                response.body().close();
            }
        }
    }

    private OkHttpClient createClient()
    {
        OkHttpClient client = new OkHttpClient();
        client.setConnectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS);
        client.setReadTimeout(readTimeoutMs, TimeUnit.MILLISECONDS);
        client.setWriteTimeout(readTimeoutMs, TimeUnit.MILLISECONDS);
        Proxy proxy = resolveProxy();
        if (proxy != null)
        {
            client.setProxy(proxy);
        }
        return client;
    }

    private Proxy resolveProxy()
    {
        if (!proxyEnabled || StringUtils.isBlank(proxyHost))
        {
            return null;
        }
        InetSocketAddress address = resolveProxyAddress();
        if (address == null)
        {
            log.warn("Gemini 未探测到可用代理。host={}, configuredPort={}, fallbackPorts={}", proxyHost, proxyPort, proxyFallbackPorts);
            return null;
        }
        log.info("Gemini 使用代理 {}:{}", address.getHostString(), address.getPort());
        return new Proxy(Proxy.Type.HTTP, address);
    }

    private InetSocketAddress resolveProxyAddress()
    {
        LinkedHashSet<Integer> candidatePorts = new LinkedHashSet<>();
        addCandidatePort(candidatePorts, proxyPort);
        if (StringUtils.isNotBlank(proxyFallbackPorts))
        {
            String[] values = proxyFallbackPorts.split(",");
            for (String value : values)
            {
                if (StringUtils.isBlank(value))
                {
                    continue;
                }
                try
                {
                    addCandidatePort(candidatePorts, Integer.parseInt(value.trim()));
                }
                catch (NumberFormatException ex)
                {
                    log.warn("Gemini 代理端口配置无效: {}", value);
                }
            }
        }
        if (candidatePorts.isEmpty())
        {
            addCandidatePort(candidatePorts, 7890);
            addCandidatePort(candidatePorts, 7897);
            addCandidatePort(candidatePorts, 10809);
            addCandidatePort(candidatePorts, 1080);
        }
        for (Integer port : candidatePorts)
        {
            if (isPortReachable(proxyHost, port))
            {
                return new InetSocketAddress(proxyHost, port);
            }
        }
        return null;
    }

    private void addCandidatePort(LinkedHashSet<Integer> candidatePorts, int port)
    {
        if (port > 0)
        {
            candidatePorts.add(port);
        }
    }

    private boolean isPortReachable(String host, int port)
    {
        Socket socket = new Socket();
        try
        {
            socket.connect(new InetSocketAddress(host, port), Math.min(connectTimeoutMs, 1500));
            return true;
        }
        catch (IOException ex)
        {
            return false;
        }
        finally
        {
            try
            {
                socket.close();
            }
            catch (IOException ex)
            {
                log.debug("关闭代理探测连接失败", ex);
            }
        }
    }

    private String buildRequestUrl()
    {
        return trimTrailingSlash(baseUrl) + "/v1/models/" + model + ":generateContent?key=" + apiKey.trim();
    }

    private String buildStreamRequestUrl()
    {
        return trimTrailingSlash(baseUrl) + "/v1/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey.trim();
    }

    private String normalizeModel(String model)
    {
        if (StringUtils.isBlank(model))
        {
            return "gemini-1.5-flash-latest";
        }
        String value = model.trim();
        if (value.startsWith("models/"))
        {
            value = value.substring("models/".length());
        }
        if ("gemini-1.5-flash".equalsIgnoreCase(value))
        {
            return "gemini-1.5-flash-latest";
        }
        return value;
    }

    private String trimTrailingSlash(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return "https://generativelanguage.googleapis.com";
        }
        String result = value.trim();
        while (result.endsWith("/"))
        {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String buildRequestBody(String context, String userMessage) throws IOException
    {
        String promptText = context + "\n\n" + userMessage;
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", promptText);
        return objectMapper.writeValueAsString(root);
    }

    private String resolveErrorMessage(int statusCode, String body)
    {
        try
        {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText();
            if (StringUtils.isNotBlank(message))
            {
                return "Gemini 请求失败：" + message;
            }
        }
        catch (Exception ex)
        {
            log.warn("解析 Gemini 错误响应失败", ex);
        }
        return "Gemini 请求失败，HTTP " + statusCode;
    }

    private String extractText(String body) throws IOException
    {
        JsonNode root = objectMapper.readTree(body);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.size() == 0)
        {
            JsonNode promptFeedback = root.path("promptFeedback");
            if (!promptFeedback.isMissingNode() && !promptFeedback.isNull())
            {
                log.info("Gemini promptFeedback: {}", promptFeedback.toString());
            }
            String blockReason = root.path("promptFeedback").path("blockReason").asText();
            if (StringUtils.isNotBlank(blockReason))
            {
                throw new CustomException("Gemini 返回被拦截：" + blockReason);
            }
            throw new CustomException("Gemini 未返回有效内容");
        }
        JsonNode firstCandidate = candidates.get(0);
        JsonNode content = firstCandidate.path("content");
        JsonNode parts = content.path("parts");
        if (!content.isObject() || !parts.isArray() || parts.size() == 0)
        {
            throw new CustomException("Gemini 未返回有效内容");
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts)
        {
            String text = part.path("text").asText("");
            if (StringUtils.isBlank(text))
            {
                continue;
            }
            if (builder.length() > 0)
            {
                builder.append('\n');
            }
            builder.append(text.trim());
        }
        if (builder.length() > 0)
        {
            return builder.toString().trim();
        }
        throw new CustomException("Gemini 未返回有效内容");
    }

    private String extractStreamChunkText(String body) throws IOException
    {
        JsonNode root = objectMapper.readTree(body);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.size() == 0)
        {
            throw new CustomException("Gemini 未返回有效内容");
        }
        JsonNode firstCandidate = candidates.get(0);
        JsonNode content = firstCandidate.path("content");
        JsonNode parts = content.path("parts");
        if (!content.isObject() || !parts.isArray() || parts.size() == 0)
        {
            throw new CustomException("Gemini 未返回有效内容");
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts)
        {
            String text = part.path("text").asText("");
            if (StringUtils.isBlank(text))
            {
                continue;
            }
            builder.append(text);
        }
        if (builder.length() > 0)
        {
            return builder.toString();
        }
        throw new CustomException("Gemini 未返回有效内容");
    }

    private String sanitizeResponseText(String text)
    {
        String result = text == null ? "" : text.trim();
        result = result.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        result = result.replaceFirst("\\s*```$", "");
        return result.trim();
    }

    private String resolveUserMessage(String inputText, Map<String, Object> vars)
    {
        if (vars != null)
        {
            Object inputValue = vars.get("input");
            if (inputValue != null && StringUtils.isNotBlank(String.valueOf(inputValue)))
            {
                return String.valueOf(inputValue).trim();
            }
        }
        if (StringUtils.isNotBlank(inputText))
        {
            return inputText.trim();
        }
        return "请根据模板和变量生成最终文案，只输出成品文案。";
    }

    private String applyTemplateVariables(String context, Map<String, Object> vars)
    {
        if (StringUtils.isBlank(context) || vars == null || vars.isEmpty())
        {
            return context;
        }
        String result = context;
        for (Map.Entry<String, Object> entry : vars.entrySet())
        {
            if (entry == null || StringUtils.isBlank(entry.getKey()) || entry.getValue() == null)
            {
                continue;
            }
            String key = entry.getKey().trim();
            if ("input".equalsIgnoreCase(key))
            {
                continue;
            }
            String value = String.valueOf(entry.getValue()).trim();
            result = result.replace("{{" + key + "}}", value);
            result = result.replace("{{ " + key + " }}", value);
        }
        return result;
    }

    private String normalizeApiKey(String apiKey)
    {
        String key = StringUtils.isBlank(apiKey) ? System.getenv("GEMINI_API_KEY") : apiKey;
        if (StringUtils.isBlank(key))
        {
            return key;
        }
        return key.trim();
    }

    private String maskApiKey(String apiKey)
    {
        if (StringUtils.isBlank(apiKey))
        {
            return "(empty)";
        }
        if (apiKey.length() <= 6)
        {
            return apiKey + "...";
        }
        return apiKey.substring(0, 6) + "...";
    }
}
