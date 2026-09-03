package com.costplatform.lite.client;

/**
 * 与轻量计费服务通信所需的客户端配置。
 */
public class CostLiteClientProperties {
    private String baseUrl;
    private String adminToken;
    private String openToken;
    private int connectTimeoutMillis = 5000;
    private int readTimeoutMillis = 30000;
    private int maxRetries;
    private long retryBackoffMillis = 200L;
    private int maxResponseBytes = 20 * 1024 * 1024;
    private String userAgent = "cost-lite-client/1.0";

    public void validate() {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("cost-lite baseUrl 未配置");
        }
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalStateException("cost-lite 超时时间必须大于0");
        }
        if (maxRetries < 0) {
            throw new IllegalStateException("cost-lite maxRetries 不能小于0");
        }
        if (retryBackoffMillis < 0) {
            throw new IllegalStateException("cost-lite retryBackoffMillis 不能小于0");
        }
        if (maxResponseBytes <= 0) {
            throw new IllegalStateException("cost-lite maxResponseBytes 必须大于0");
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public String getOpenToken() {
        return openToken;
    }

    public void setOpenToken(String openToken) {
        this.openToken = openToken;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
