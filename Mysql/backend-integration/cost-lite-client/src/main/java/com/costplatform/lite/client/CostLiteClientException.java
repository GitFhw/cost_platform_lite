package com.costplatform.lite.client;

/**
 * 轻量计费客户端异常，保留 HTTP 状态和上游响应，便于宿主系统统一处理。
 */
public class CostLiteClientException extends RuntimeException {
    private final int httpStatus;
    private final Integer upstreamCode;
    private final String path;
    private final String responseBody;
    private final boolean retryable;

    public CostLiteClientException(String message, Throwable cause) {
        this(message, 0, null, null, null, false, cause);
    }

    public CostLiteClientException(String message,
                                   int httpStatus,
                                   Integer upstreamCode,
                                   String path,
                                   String responseBody,
                                   boolean retryable) {
        this(message, httpStatus, upstreamCode, path, responseBody, retryable, null);
    }

    public CostLiteClientException(String message,
                                   int httpStatus,
                                   Integer upstreamCode,
                                   String path,
                                   String responseBody,
                                   boolean retryable,
                                   Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.upstreamCode = upstreamCode;
        this.path = path;
        this.responseBody = responseBody;
        this.retryable = retryable;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Integer getUpstreamCode() {
        return upstreamCode;
    }

    public String getPath() {
        return path;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
