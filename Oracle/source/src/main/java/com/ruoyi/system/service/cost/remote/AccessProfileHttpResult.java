package com.ruoyi.system.service.cost.remote;

/**
 * Result of a direct access-profile HTTP call.
 */
public class AccessProfileHttpResult {
    public final String requestMethod;
    public final String endpointUrl;
    public final String resolvedUrl;
    public final int statusCode;
    public final String contentType;
    public final String sourceType;
    public final boolean requestPayloadProvided;
    public final int responseSize;
    public final Object requestPayload;
    public final Object responsePayload;

    public AccessProfileHttpResult(String requestMethod, String endpointUrl, String resolvedUrl, int statusCode,
                                   String contentType, String sourceType, boolean requestPayloadProvided,
                                   int responseSize, Object requestPayload, Object responsePayload) {
        this.requestMethod = requestMethod;
        this.endpointUrl = endpointUrl;
        this.resolvedUrl = resolvedUrl;
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.sourceType = sourceType;
        this.requestPayloadProvided = requestPayloadProvided;
        this.responseSize = responseSize;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
    }
}
