package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade pipeline for third-party variable endpoint access.
 */
@Service
public class RemoteVariableAccessPipeline {
    public static final String ADAPTER_STANDARD = "STANDARD";
    public static final String ADAPTER_ROOT_ARRAY = "ROOT_ARRAY";
    public static final String ADAPTER_PAGE_ENVELOPE = "PAGE_ENVELOPE";
    public static final String ADAPTER_SINGLE_OBJECT = "SINGLE_OBJECT";

    private static final Logger log = LoggerFactory.getLogger(RemoteVariableAccessPipeline.class);
    private static final int PREVIEW_ROW_LIMIT = 20;
    private static final List<String> DEFAULT_LIST_PATHS = List.of(
            "rows", "list", "items", "data.rows", "data.list", "data.items", "data.records", "records", "data");
    private static final List<String> DEFAULT_MESSAGE_PATHS = List.of("msg", "message", "data.msg", "data.message");
    private static final List<String> DEFAULT_SUCCESS_VALUES = List.of("0", "200", "true", "success", "ok");

    private final RestTemplate restTemplate;
    private final RemoteRequestBuilder requestBuilder;
    private final RemoteAuthHandler authHandler;
    private final RemoteEndpointGuard endpointGuard;
    private final ObjectMapper objectMapper;

    public RemoteVariableAccessPipeline(@Qualifier("costAccessRestTemplate") RestTemplate restTemplate,
                                        RemoteRequestBuilder requestBuilder,
                                        RemoteAuthHandler authHandler,
                                        RemoteEndpointGuard endpointGuard) {
        this.restTemplate = restTemplate;
        this.requestBuilder = requestBuilder;
        this.authHandler = authHandler;
        this.endpointGuard = endpointGuard;
        this.objectMapper = new ObjectMapper();
    }

    public RemoteInvokeResult invoke(RemoteVariableConfig config) {
        long startedAt = System.nanoTime();
        URI uri = requestBuilder.buildUri(config);
        endpointGuard.validate(uri);
        HttpHeaders headers = requestBuilder.buildHeaders(config);
        String authHeaderName = authHandler.resolveAuthHeaderName(config);
        boolean authHeaderApplied = StringUtils.isNotEmpty(authHeaderName) && headers.containsKey(authHeaderName);
        boolean authTokenPresent = authHandler.hasConfiguredAuthToken(config);
        log.info("第三方变量开始调用: variableCode={}, method={}, uri={}, authType={}, authHeaderApplied={}, authTokenPresent={}",
                config.variableCode, config.requestMethod, uri, config.authType, authHeaderApplied, authTokenPresent);
        try {
            HttpMethod httpMethod = HttpMethod.valueOf(config.requestMethod);
            HttpEntity<?> entity = requestBuilder.buildHttpEntity(config, headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, httpMethod, entity, String.class);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String responseBody = StringUtils.defaultString(response.getBody());
            String contentType = response.getHeaders().getContentType() == null ? "" : response.getHeaders().getContentType().toString();
            JsonNode bodyNode = parseResponseBody(responseBody);
            ObjectNode responseConfig = config.responseConfig;
            List<JsonNode> items = extractItemNodes(bodyNode, config);
            boolean success = isRemoteResponseSuccess(response, bodyNode, responseConfig);
            String message = firstNonBlank(
                    extractFirstText(bodyNode, textValue(responseConfig, "messagePath"), DEFAULT_MESSAGE_PATHS),
                    response.getStatusCode().is2xxSuccessful() ? "接口调用成功" : "接口调用失败");
            log.info("第三方变量调用完成: variableCode={}, statusCode={}, elapsedMs={}, rowCount={}, success={}",
                    config.variableCode, response.getStatusCodeValue(), elapsedMs, items == null ? 0 : items.size(), success);
            return new RemoteInvokeResult(success, message, response.getStatusCodeValue(), elapsedMs, contentType,
                    responseBody.length(), bodyNode, items,
                    uri.toString(), new ArrayList<>(headers.keySet()), authHeaderApplied, authHeaderName, authTokenPresent,
                    "", "", "");
        } catch (RestClientResponseException ex) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String responseBody = StringUtils.defaultString(ex.getResponseBodyAsString());
            String contentType = ex.getResponseHeaders() == null || ex.getResponseHeaders().getContentType() == null
                    ? "" : ex.getResponseHeaders().getContentType().toString();
            JsonNode bodyNode = parseResponseBody(responseBody);
            ObjectNode responseConfig = config.responseConfig;
            String message = firstNonBlank(
                    extractFirstText(bodyNode, textValue(responseConfig, "messagePath"), DEFAULT_MESSAGE_PATHS),
                    ex.getStatusText(),
                    "第三方接口返回异常");
            log.warn("第三方变量调用返回异常响应: variableCode={}, statusCode={}, elapsedMs={}, uri={}, message={}",
                    config.variableCode, ex.getRawStatusCode(), elapsedMs, uri, message);
            return new RemoteInvokeResult(false, message, ex.getRawStatusCode(), elapsedMs, contentType,
                    responseBody.length(), bodyNode, Collections.emptyList(),
                    uri.toString(), new ArrayList<>(headers.keySet()), authHeaderApplied, authHeaderName, authTokenPresent,
                    "REMOTE_RESPONSE", ex.getClass().getSimpleName(), firstNonBlank(responseBody, ex.getMessage()));
        } catch (RestClientException ex) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            Throwable rootCause = rootCauseOf(ex);
            String diagnosticMessage = firstNonBlank(rootCause == null ? "" : rootCause.getMessage(), ex.getMessage(), "远程服务不可用");
            String failureStage = determineFailureStage(ex, rootCause);
            log.warn("第三方变量调用网络异常: variableCode={}, elapsedMs={}, uri={}, failureStage={}, errorType={}, message={}",
                    config.variableCode, elapsedMs, uri, failureStage, ex.getClass().getSimpleName(), diagnosticMessage);
            return new RemoteInvokeResult(false, "第三方接口调用失败：" + diagnosticMessage, 0, elapsedMs,
                    "", 0, JsonNodeFactory.instance.objectNode(), Collections.emptyList(), uri.toString(), new ArrayList<>(headers.keySet()),
                    authHeaderApplied, authHeaderName, authTokenPresent, failureStage, ex.getClass().getSimpleName(), diagnosticMessage);
        }
    }

    public List<Map<String, Object>> buildPreviewRawRows(List<JsonNode> itemNodes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode itemNode : itemNodes) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("sourceCode", extractFirstText(itemNode, "", List.of("sourceCode", "code", "id", "bizNo")));
            row.put("sourceName", extractFirstText(itemNode, "", List.of("sourceName", "name", "label", "title")));
            row.put("businessDomain", extractFirstText(itemNode, "", List.of("businessDomain", "companyCode", "domain")));
            row.put("value", extractFirstText(itemNode, "", List.of("value", "amount", "code", "id")));
            row.put("rawJson", toCompactJson(itemNode));
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> buildPreviewMappedRows(RemoteVariableConfig config, List<JsonNode> itemNodes) {
        List<Map<String, Object>> mappedRows = new ArrayList<>();
        ObjectNode mappingConfig = config.mappingConfig;
        for (JsonNode itemNode : itemNodes) {
            LinkedHashMap<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("variableCode", config.variableCode);
            mapped.put("sourceCode", resolveMappedValue(itemNode, mappingConfig, "sourceCode", List.of("sourceCode", "code", "id", "bizNo")));
            mapped.put("sourceName", resolveMappedValue(itemNode, mappingConfig, "sourceName", List.of("sourceName", "name", "label", "title")));
            mapped.put("businessDomain", resolveMappedValue(itemNode, mappingConfig, "businessDomain", List.of("businessDomain", "companyCode", "domain")));
            mapped.put("mappedValue", resolveMappedValue(itemNode, mappingConfig, "mappedValue", List.of("value", "amount", "code", "id")));
            mapped.put("dataPath", firstNonBlank(textValue(config.responseConfig, "listPath"), config.dataPath));
            mapped.put("sourceSystem", config.sourceSystem);
            mapped.put("rawJson", toCompactJson(itemNode));
            mappedRows.add(mapped);
        }
        return mappedRows;
    }

    private JsonNode parseResponseBody(String responseBody) {
        if (StringUtils.isEmpty(responseBody)) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
            wrapper.put("rawBody", responseBody);
            return wrapper;
        }
    }

    private boolean isRemoteResponseSuccess(ResponseEntity<String> response, JsonNode bodyNode, ObjectNode responseConfig) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            return false;
        }
        String successPath = textValue(responseConfig, "successPath");
        if (StringUtils.isEmpty(successPath)) {
            return true;
        }
        String actual = normalizeComparableValue(extractFirstText(bodyNode, successPath, Collections.emptyList()));
        if (StringUtils.isEmpty(actual)) {
            return false;
        }
        List<String> expectedValues = extractExpectedSuccessValues(responseConfig);
        return expectedValues.stream().map(this::normalizeComparableValue).anyMatch(actual::equals);
    }

    private List<String> extractExpectedSuccessValues(ObjectNode responseConfig) {
        if (responseConfig == null) {
            return DEFAULT_SUCCESS_VALUES;
        }
        JsonNode successValues = responseConfig.get("successValues");
        if (successValues == null || successValues.isNull()) {
            return DEFAULT_SUCCESS_VALUES;
        }
        List<String> values = new ArrayList<>();
        if (successValues.isArray()) {
            successValues.forEach(item -> values.add(item.isTextual() ? item.asText() : item.toString()));
        } else {
            values.add(successValues.isTextual() ? successValues.asText() : successValues.toString());
        }
        return values.isEmpty() ? DEFAULT_SUCCESS_VALUES : values;
    }

    private List<JsonNode> extractItemNodes(JsonNode bodyNode, RemoteVariableConfig config) {
        if (bodyNode == null || bodyNode.isNull()) {
            return Collections.emptyList();
        }
        if (ADAPTER_ROOT_ARRAY.equals(config.adapterType) && bodyNode.isArray()) {
            return limitPreviewRows(bodyNode);
        }
        if (ADAPTER_SINGLE_OBJECT.equals(config.adapterType)) {
            return List.of(bodyNode);
        }
        String configuredListPath = firstNonBlank(textValue(config.responseConfig, "listPath"), config.dataPath);
        List<String> candidates = new ArrayList<>();
        if (StringUtils.isNotEmpty(configuredListPath)) {
            candidates.add(configuredListPath);
        }
        if (config.adapterConfig != null) {
            JsonNode listCandidates = config.adapterConfig.get("listPathCandidates");
            if (listCandidates != null && listCandidates.isArray()) {
                listCandidates.forEach(item -> {
                    if (item != null && !item.isNull() && StringUtils.isNotEmpty(item.asText())) {
                        candidates.add(item.asText());
                    }
                });
            }
        }
        if (ADAPTER_PAGE_ENVELOPE.equals(config.adapterType)) {
            candidates.addAll(List.of("data.rows", "data.list", "rows", "list"));
        }
        candidates.addAll(DEFAULT_LIST_PATHS);
        for (String candidate : candidates) {
            List<JsonNode> nodes = extractNodes(bodyNode, candidate);
            if (!nodes.isEmpty()) {
                JsonNode first = nodes.get(0);
                if (first.isArray()) {
                    return limitPreviewRows(first);
                }
                return nodes.size() > PREVIEW_ROW_LIMIT ? nodes.subList(0, PREVIEW_ROW_LIMIT) : nodes;
            }
        }
        if (bodyNode.isArray()) {
            return limitPreviewRows(bodyNode);
        }
        return List.of(bodyNode);
    }

    private List<JsonNode> limitPreviewRows(JsonNode arrayNode) {
        List<JsonNode> rows = new ArrayList<>();
        int size = Math.min(arrayNode.size(), PREVIEW_ROW_LIMIT);
        for (int i = 0; i < size; i++) {
            rows.add(arrayNode.get(i));
        }
        return rows;
    }

    private String resolveMappedValue(JsonNode itemNode, ObjectNode mappingConfig, String fieldName, List<String> fallbackPaths) {
        if (mappingConfig != null) {
            JsonNode mappedNode = mappingConfig.get(fieldName);
            if (mappedNode == null && mappingConfig.has("fields")) {
                JsonNode fieldsNode = mappingConfig.get("fields");
                if (fieldsNode != null && fieldsNode.isObject()) {
                    mappedNode = fieldsNode.get(fieldName);
                }
            }
            if (mappedNode != null && !mappedNode.isNull()) {
                String path = mappedNode.isTextual() ? mappedNode.asText() : mappedNode.toString();
                String value = extractFirstText(itemNode, path, Collections.emptyList());
                if (StringUtils.isNotEmpty(value)) {
                    return value;
                }
            }
        }
        return extractFirstText(itemNode, "", fallbackPaths);
    }

    private List<JsonNode> extractNodes(JsonNode root, String path) {
        if (root == null || root.isNull()) {
            return Collections.emptyList();
        }
        if (StringUtils.isEmpty(path)) {
            return List.of(root);
        }
        List<JsonNode> current = new ArrayList<>();
        current.add(root);
        for (String rawSegment : path.split("\\.")) {
            if (StringUtils.isEmpty(rawSegment)) {
                continue;
            }
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : current) {
                next.addAll(traverseSegment(node, rawSegment));
            }
            current = next;
            if (current.isEmpty()) {
                return Collections.emptyList();
            }
        }
        return current;
    }

    private List<JsonNode> traverseSegment(JsonNode node, String segment) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        String fieldName = segment;
        String arrayMarker = null;
        int bracketIndex = segment.indexOf('[');
        if (bracketIndex >= 0) {
            fieldName = segment.substring(0, bracketIndex);
            arrayMarker = segment.substring(bracketIndex);
        }
        JsonNode current = StringUtils.isEmpty(fieldName) ? node : node.get(fieldName);
        if (current == null || current.isNull()) {
            return Collections.emptyList();
        }
        if (arrayMarker == null) {
            return List.of(current);
        }
        if (!current.isArray()) {
            return Collections.emptyList();
        }
        if ("[]".equals(arrayMarker)) {
            List<JsonNode> nodes = new ArrayList<>();
            current.forEach(nodes::add);
            return nodes;
        }
        if (arrayMarker.startsWith("[") && arrayMarker.endsWith("]")) {
            String indexText = arrayMarker.substring(1, arrayMarker.length() - 1);
            Integer index = parseInteger(indexText, -1);
            if (index != null && index >= 0 && index < current.size()) {
                return List.of(current.get(index));
            }
        }
        return Collections.emptyList();
    }

    private String extractFirstText(JsonNode root, String primaryPath, List<String> fallbackPaths) {
        if (StringUtils.isNotEmpty(primaryPath)) {
            List<JsonNode> nodes = extractNodes(root, primaryPath);
            if (!nodes.isEmpty()) {
                return toHttpParamValue(nodes.get(0));
            }
        }
        for (String fallbackPath : fallbackPaths) {
            List<JsonNode> nodes = extractNodes(root, fallbackPath);
            if (!nodes.isEmpty()) {
                return toHttpParamValue(nodes.get(0));
            }
        }
        return "";
    }

    private String toHttpParamValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    private String normalizeComparableValue(String value) {
        return StringUtils.isEmpty(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String toCompactJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return node == null ? "" : node.toString();
        }
    }

    private String textValue(JsonNode objectNode, String fieldName) {
        if (objectNode == null || objectNode.isNull() || StringUtils.isEmpty(fieldName)) {
            return "";
        }
        JsonNode node = objectNode.get(fieldName);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String determineFailureStage(RestClientException ex, Throwable rootCause) {
        String message = firstNonBlank(rootCause == null ? "" : rootCause.getMessage(), ex.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("connect timed out") || message.contains("connection timed out")) {
            return "CONNECT_TIMEOUT";
        }
        if (message.contains("read timed out") || message.contains("socket timeout")) {
            return "READ_TIMEOUT";
        }
        if (message.contains("connection refused")) {
            return "CONNECTION_REFUSED";
        }
        if (message.contains("no route to host")) {
            return "NO_ROUTE";
        }
        return "REMOTE_IO";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(StringUtils.trim(value))) {
                return StringUtils.trim(value);
            }
        }
        return "";
    }

    private Integer parseInteger(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
