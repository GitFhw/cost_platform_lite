package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Builds normalized HTTP request objects for remote access configurations.
 */
@Service
public class RemoteRequestBuilder {
    private final RemoteAuthHandler authHandler;

    public RemoteRequestBuilder(RemoteAuthHandler authHandler) {
        this.authHandler = authHandler;
    }

    public URI buildUri(RemoteVariableConfig config) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(config.remoteApi);
        appendConfiguredQueryParams(builder, config.queryConfig);
        appendConfiguredPagination(builder, config.pageConfig);
        authHandler.appendAuthQueryParams(builder, config);
        return builder.build(true).toUri();
    }

    public HttpHeaders buildHeaders(RemoteVariableConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        if (StringUtils.isNotEmpty(config.contentType)) {
            headers.setContentType(MediaType.parseMediaType(config.contentType));
        }
        if (config.requestHeaders != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = config.requestHeaders.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value == null || value.isNull()) {
                    continue;
                }
                if (value.isArray()) {
                    value.forEach(item -> headers.add(field.getKey(), toHttpParamValue(item)));
                    continue;
                }
                headers.set(field.getKey(), toHttpParamValue(value));
            }
        }
        authHandler.applyAuthHeaders(headers, config);
        return headers;
    }

    public HttpEntity<?> buildHttpEntity(RemoteVariableConfig config, HttpHeaders headers) {
        if ("GET".equals(config.requestMethod) || "DELETE".equals(config.requestMethod)) {
            return new HttpEntity<>(headers);
        }
        if (config.bodyTemplate == null || config.bodyTemplate.isNull()) {
            return new HttpEntity<>(headers);
        }
        if (MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(config.contentType)) {
            return new HttpEntity<>(config.bodyTemplate.toString(), headers);
        }
        return new HttpEntity<>(config.bodyTemplate.isTextual() ? config.bodyTemplate.asText() : config.bodyTemplate.toString(), headers);
    }

    private void appendConfiguredQueryParams(UriComponentsBuilder builder, JsonNode queryConfig) {
        if (queryConfig == null || !queryConfig.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = queryConfig.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isArray()) {
                value.forEach(item -> builder.queryParam(field.getKey(), toHttpParamValue(item)));
                continue;
            }
            builder.queryParam(field.getKey(), toHttpParamValue(value));
        }
    }

    private void appendConfiguredPagination(UriComponentsBuilder builder, JsonNode pageConfig) {
        if (pageConfig == null || !pageConfig.isObject()) {
            return;
        }
        String pageNumKey = firstNonBlank(textValue(pageConfig, "pageNumKey"), textValue(pageConfig, "currentKey"));
        String pageSizeKey = firstNonBlank(textValue(pageConfig, "pageSizeKey"), textValue(pageConfig, "sizeKey"));
        JsonNode previewPageNum = pageConfig.get("previewPageNum");
        JsonNode previewPageSize = pageConfig.get("previewPageSize");
        if (StringUtils.isNotEmpty(pageNumKey) && previewPageNum != null && !previewPageNum.isNull()) {
            builder.replaceQueryParam(pageNumKey, toHttpParamValue(previewPageNum));
        }
        if (StringUtils.isNotEmpty(pageSizeKey) && previewPageSize != null && !previewPageSize.isNull()) {
            builder.replaceQueryParam(pageSizeKey, toHttpParamValue(previewPageSize));
        }
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

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }
}
