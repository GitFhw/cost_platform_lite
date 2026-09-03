package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts access-profile calls to the shared remote access pipeline.
 */
@Service
public class AccessProfileHttpClient {
    private final RemoteVariableAccessPipeline remoteAccessPipeline;
    private final ObjectMapper objectMapper;

    public AccessProfileHttpClient(RemoteVariableAccessPipeline remoteAccessPipeline) {
        this.remoteAccessPipeline = remoteAccessPipeline;
        this.objectMapper = new ObjectMapper();
    }

    public AccessProfileHttpResult fetch(CostAccessProfile profile, String requestMethod, Object requestPayload) {
        RemoteVariableConfig config = buildRemoteConfig(profile, requestMethod, requestPayload);
        RemoteInvokeResult invokeResult = remoteAccessPipeline.invoke(config);
        if (!invokeResult.success) {
            throw new ServiceException("直连业务接口失败：" + limitLength(
                    firstNonBlank(invokeResult.diagnosticMessage, invokeResult.message), 300));
        }
        if (invokeResult.responseSize <= 0) {
            throw new ServiceException("业务接口已返回，但响应体为空，无法预演标准计费对象");
        }
        Object responsePayload = objectMapper.convertValue(invokeResult.responseBody, Object.class);
        return new AccessProfileHttpResult(
                config.requestMethod,
                profile.getEndpointUrl(),
                invokeResult.requestUrl,
                invokeResult.statusCode,
                invokeResult.contentType,
                profile.getSourceType(),
                requestPayload != null,
                invokeResult.responseSize,
                requestPayload,
                responsePayload);
    }

    private RemoteVariableConfig buildRemoteConfig(CostAccessProfile profile, String requestMethod, Object requestPayload) {
        String normalizedMethod = StringUtils.defaultIfEmpty(StringUtils.upperCase(requestMethod), "GET");
        Map<String, Object> authConfig = parseOptionalJsonMap(profile.getAuthConfigJson());
        ObjectNode requestHeaders = toObjectNode(authConfig.get("headers"));
        ObjectNode queryConfig = "GET".equals(normalizedMethod) ? toObjectNode(requestPayload) : JsonNodeFactory.instance.objectNode();
        JsonNode bodyTemplate = "GET".equals(normalizedMethod) ? JsonNodeFactory.instance.nullNode()
                : toJsonNode(requestPayload == null ? new LinkedHashMap<>() : requestPayload);
        return new RemoteVariableConfig(
                profile.getProfileCode(),
                profile.getEndpointUrl(),
                profile.getSourceType(),
                normalizedMethod,
                MediaType.APPLICATION_JSON_VALUE,
                queryConfig,
                requestHeaders,
                bodyTemplate,
                StringUtils.defaultIfEmpty(StringUtils.upperCase(profile.getAuthType()), "NONE"),
                toJsonNode(authConfig),
                "",
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                RemoteVariableAccessPipeline.ADAPTER_STANDARD,
                JsonNodeFactory.instance.objectNode(),
                "",
                "",
                "");
    }

    private ObjectNode toObjectNode(Object value) {
        if (value == null) {
            return JsonNodeFactory.instance.objectNode();
        }
        JsonNode node = toJsonNode(value);
        return node != null && node.isObject() ? (ObjectNode) node : JsonNodeFactory.instance.objectNode();
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        return objectMapper.valueToTree(value);
    }

    private Map<String, Object> parseOptionalJsonMap(String json) {
        if (StringUtils.isEmpty(StringUtils.trim(json))) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
