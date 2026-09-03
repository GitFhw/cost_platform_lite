package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Third-party variable invocation result with diagnostics for operators.
 */
public class RemoteInvokeResult {
    public final boolean success;
    public final String message;
    public final int statusCode;
    public final long elapsedMs;
    public final String contentType;
    public final int responseSize;
    public final JsonNode responseBody;
    public final List<JsonNode> items;
    public final String requestUrl;
    public final List<String> requestHeaderNames;
    public final boolean authHeaderApplied;
    public final String authHeaderName;
    public final boolean authTokenPresent;
    public final String failureStage;
    public final String errorType;
    public final String diagnosticMessage;

    public RemoteInvokeResult(boolean success, String message, int statusCode, long elapsedMs, String contentType,
                              int responseSize, JsonNode responseBody,
                              List<JsonNode> items, String requestUrl, List<String> requestHeaderNames,
                              boolean authHeaderApplied, String authHeaderName, boolean authTokenPresent,
                              String failureStage, String errorType, String diagnosticMessage) {
        this.success = success;
        this.message = message;
        this.statusCode = statusCode;
        this.elapsedMs = elapsedMs;
        this.contentType = contentType;
        this.responseSize = responseSize;
        this.responseBody = responseBody;
        this.items = items;
        this.requestUrl = requestUrl;
        this.requestHeaderNames = requestHeaderNames;
        this.authHeaderApplied = authHeaderApplied;
        this.authHeaderName = authHeaderName;
        this.authTokenPresent = authTokenPresent;
        this.failureStage = failureStage;
        this.errorType = errorType;
        this.diagnosticMessage = diagnosticMessage;
    }

    public int rowCount() {
        return items == null ? 0 : items.size();
    }

    public String responseMessage() {
        return responseBody == null ? "" : responseBody.toString();
    }

    public String responsePreview() {
        String body = responseMessage();
        return body.length() <= 400 ? body : body.substring(0, 400) + "...";
    }
}
