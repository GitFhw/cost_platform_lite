package com.costplatform.lite.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 基于 JDK HttpURLConnection 的默认实现，保证 Java 8 和常见 Spring Boot 版本都能使用。
 */
public class DefaultCostLiteClient implements CostLiteClient {
    private static final String ADMIN_TOKEN_HEADER = "X-Cost-Lite-Token";
    private static final String OPEN_TOKEN_HEADER = "X-Cost-Open-Token";

    private final CostLiteClientProperties properties;
    private final ObjectMapper objectMapper;

    public DefaultCostLiteClient(CostLiteClientProperties properties) {
        this(properties, new ObjectMapper());
    }

    public DefaultCostLiteClient(CostLiteClientProperties properties, ObjectMapper objectMapper) {
        if (properties == null) {
            throw new IllegalArgumentException("cost-lite 客户端配置不能为空");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper 不能为空");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public CostLiteResponse execute(CostLiteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        properties.validate();
        validatePath(request.getPath());

        int attempts = properties.getMaxRetries() + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return executeOnce(request);
            } catch (CostLiteClientException exception) {
                if (!request.isRetryable() || !exception.isRetryable() || attempt + 1 >= attempts) {
                    throw exception;
                }
                sleepBeforeRetry();
            }
        }
        throw new CostLiteClientException("轻量计费请求未执行", 0, null, request.getPath(), null, false);
    }

    private CostLiteResponse executeOnce(CostLiteRequest request) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(buildUrl(request));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.getMethod().name());
            connection.setConnectTimeout(properties.getConnectTimeoutMillis());
            connection.setReadTimeout(properties.getReadTimeoutMillis());
            connection.setUseCaches(false);
            connection.setDoInput(true);
            applyHeaders(connection, request);

            if (request.getBody() != null && request.getMethod() != CostLiteRequest.Method.GET
                    && request.getMethod() != CostLiteRequest.Method.DELETE) {
                byte[] body = serializeBody(request.getBody());
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }

            int httpStatus = connection.getResponseCode();
            String rawBody = readResponse(connection, httpStatus);
            JsonNode bodyNode = parseBody(rawBody);
            CostLiteResponse response = new CostLiteResponse(httpStatus, rawBody, bodyNode);
            if (!response.isSuccess()) {
                String message = response.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = "轻量计费服务调用失败，HTTP " + httpStatus;
                }
                throw new CostLiteClientException(
                        message,
                        httpStatus,
                        response.getCode(),
                        request.getPath(),
                        rawBody,
                        isRetryableStatus(httpStatus));
            }
            return response;
        } catch (CostLiteClientException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new CostLiteClientException("轻量计费请求体序列化失败", exception);
        } catch (MalformedURLException exception) {
            throw new CostLiteClientException("轻量计费服务地址无效", exception);
        } catch (IOException exception) {
            throw new CostLiteClientException(
                    "轻量计费服务网络调用失败",
                    0,
                    null,
                    request.getPath(),
                    null,
                    true,
                    exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void applyHeaders(HttpURLConnection connection, CostLiteRequest request) {
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        if (isBlank(connection.getRequestProperty("Accept"))) {
            connection.setRequestProperty("Accept", "application/json");
        }
        if (isBlank(connection.getRequestProperty("User-Agent")) && !isBlank(properties.getUserAgent())) {
            connection.setRequestProperty("User-Agent", properties.getUserAgent());
        }
        if (request.getBody() != null && request.getMethod() != CostLiteRequest.Method.GET
                && request.getMethod() != CostLiteRequest.Method.DELETE
                && isBlank(connection.getRequestProperty("Content-Type"))) {
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        }
        if (request.getAuth() == CostLiteAuth.MANAGEMENT && !isBlank(properties.getAdminToken())) {
            connection.setRequestProperty(ADMIN_TOKEN_HEADER, properties.getAdminToken());
        } else if (request.getAuth() == CostLiteAuth.OPEN && !isBlank(properties.getOpenToken())) {
            connection.setRequestProperty(OPEN_TOKEN_HEADER, properties.getOpenToken());
        }
    }

    private byte[] serializeBody(Object body) throws JsonProcessingException {
        if (body instanceof String) {
            return ((String) body).getBytes(StandardCharsets.UTF_8);
        }
        return objectMapper.writeValueAsBytes(body);
    }

    private String readResponse(HttpURLConnection connection, int httpStatus) throws IOException {
        InputStream stream = httpStatus >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > properties.getMaxResponseBytes()) {
                    throw new IOException("轻量计费响应超过允许大小");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private JsonNode parseBody(String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty()) {
            return NullNode.getInstance();
        }
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            return node == null ? NullNode.getInstance() : node;
        } catch (IOException ignored) {
            return TextNode.valueOf(rawBody);
        }
    }

    private String buildUrl(CostLiteRequest request) throws MalformedURLException {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl().trim());
        String path = request.getPath().trim();
        StringBuilder url = new StringBuilder(baseUrl);
        if (!path.startsWith("/")) {
            url.append('/');
        }
        url.append(path);
        appendQuery(url, request.getQueryParameters());
        return new URL(url.toString()).toString();
    }

    private void appendQuery(StringBuilder url, Map<String, Object> queryParameters) {
        boolean first = true;
        for (Map.Entry<String, Object> entry : queryParameters.entrySet()) {
            List<Object> values = expandValues(entry.getValue());
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                url.append(first ? '?' : '&');
                first = false;
                url.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(value)));
            }
        }
    }

    private List<Object> expandValues(Object value) {
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        if (value == null) {
            return values;
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                values.add(item);
            }
            return values;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                values.add(Array.get(value, index));
            }
            return values;
        }
        values.add(value);
        return values;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exception) {
            throw new IllegalStateException("查询参数编码失败", exception);
        }
    }

    private void validatePath(String path) {
        String value = path == null ? "" : path.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("://")) {
            throw new IllegalArgumentException("轻量计费请求路径必须是相对路径：" + path);
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private void sleepBeforeRetry() {
        if (properties.getRetryBackoffMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.getRetryBackoffMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CostLiteClientException("轻量计费重试被中断", exception);
        }
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 0) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
