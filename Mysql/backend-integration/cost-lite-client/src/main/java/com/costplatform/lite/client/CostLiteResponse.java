package com.costplatform.lite.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * 轻量计费服务的原始 JSON 响应，同时兼容 RuoYi code/msg/rows 和通用 status 响应。
 */
public final class CostLiteResponse {
    private final int httpStatus;
    private final String rawBody;
    private final JsonNode body;

    public CostLiteResponse(int httpStatus, String rawBody, JsonNode body) {
        this.httpStatus = httpStatus;
        this.rawBody = rawBody == null ? "" : rawBody;
        this.body = body == null ? NullNode.getInstance() : body;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getRawBody() {
        return rawBody;
    }

    public JsonNode getBodyNode() {
        return body;
    }

    public Integer getCode() {
        return integerValue(body.get("code"));
    }

    public Integer getStatus() {
        return integerValue(body.get("status"));
    }

    public String getMessage() {
        String message = textValue(body.get("msg"));
        if (isBlank(message)) {
            message = textValue(body.get("statusText"));
        }
        if (isBlank(message)) {
            message = textValue(body.get("error"));
        }
        if (isBlank(message)) {
            message = textValue(body.get("message"));
        }
        return isBlank(message) ? "" : message;
    }

    public JsonNode getDataNode() {
        JsonNode data = body.get("data");
        return data == null || data.isNull() ? null : data;
    }

    public JsonNode getRowsNode() {
        JsonNode rows = body.get("rows");
        return rows == null || rows.isNull() ? null : rows;
    }

    public Long getTotal() {
        JsonNode total = body.get("total");
        if (total == null || total.isNull()) {
            return null;
        }
        try {
            return total.isNumber() ? total.longValue() : Long.valueOf(total.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public JsonNode getPayloadNode() {
        JsonNode data = getDataNode();
        if (data != null) {
            return data;
        }
        JsonNode rows = getRowsNode();
        return rows == null ? body : rows;
    }

    public boolean isSuccess() {
        if (httpStatus < 200 || httpStatus >= 300) {
            return false;
        }
        Integer status = getStatus();
        if (status != null) {
            return status == 1;
        }
        Integer code = getCode();
        return code == null || code == 0 || code == 200;
    }

    public <T> T dataAs(Class<T> type, ObjectMapper objectMapper) {
        JsonNode payload = getPayloadNode();
        return payload == null || payload.isNull() ? null : objectMapper.convertValue(payload, type);
    }

    public <T> T dataAs(TypeReference<T> type, ObjectMapper objectMapper) {
        JsonNode payload = getPayloadNode();
        return payload == null || payload.isNull() ? null : objectMapper.convertValue(payload, type);
    }

    private Integer integerValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.isNumber() ? node.intValue() : Integer.valueOf(node.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
