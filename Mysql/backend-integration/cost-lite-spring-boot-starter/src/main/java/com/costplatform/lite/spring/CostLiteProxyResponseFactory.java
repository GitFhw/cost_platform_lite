package com.costplatform.lite.spring;

import com.costplatform.lite.client.CostLiteClientException;
import com.costplatform.lite.client.CostLiteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把母体服务的 RuoYi 风格响应归一为宿主前端常用的 status/error/statusText/data 结构。
 */
public class CostLiteProxyResponseFactory {
    private final ObjectMapper objectMapper;
    private final CostLiteSpringProperties properties;

    public CostLiteProxyResponseFactory(ObjectMapper objectMapper, CostLiteSpringProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Object success(CostLiteResponse response) {
        if (isRawMode()) {
            return toObject(response.getBodyNode());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 1);
        result.put("error", "");
        result.put("statusText", defaultMessage(response.getMessage(), "成功"));

        JsonNode dataNode = response.getDataNode();
        JsonNode rowsNode = response.getRowsNode();
        if (dataNode != null) {
            result.put("data", toObject(dataNode));
        } else if (rowsNode != null) {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("rows", toObject(rowsNode));
            if (response.getTotal() != null) {
                page.put("total", response.getTotal());
            }
            result.put("data", page);
        } else {
            result.put("data", toObject(response.getPayloadNode()));
        }
        if (rowsNode != null) {
            result.put("rows", toObject(rowsNode));
        }
        if (response.getTotal() != null) {
            result.put("total", response.getTotal());
        }
        return result;
    }

    public Object failure(Throwable throwable) {
        if (isRawMode()) {
            Map<String, Object> rawFailure = new LinkedHashMap<>();
            rawFailure.put("error", messageOf(throwable));
            return rawFailure;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 2);
        result.put("error", messageOf(throwable));
        result.put("statusText", messageOf(throwable));
        result.put("data", null);
        if (throwable instanceof CostLiteClientException) {
            CostLiteClientException exception = (CostLiteClientException) throwable;
            result.put("httpStatus", exception.getHttpStatus());
            if (exception.getUpstreamCode() != null) {
                result.put("upstreamCode", exception.getUpstreamCode());
            }
        }
        return result;
    }

    private boolean isRawMode() {
        return "raw".equalsIgnoreCase(properties.getResponseMode());
    }

    private Object toObject(JsonNode node) {
        return node == null || node.isNull() ? null : objectMapper.convertValue(node, Object.class);
    }

    private String messageOf(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return "轻量计费服务调用失败";
        }
        return throwable.getMessage();
    }

    private String defaultMessage(String message, String fallback) {
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}
