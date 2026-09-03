package com.costplatform.lite.spring;

import com.costplatform.lite.client.CostLiteClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot 集成配置。默认关闭，避免仅引入依赖就改变宿主应用启动行为。
 */
@ConfigurationProperties(prefix = "cost.lite.integration")
public class CostLiteSpringProperties {
    private boolean enabled;
    private String baseUrl;
    private String adminToken;
    private String openToken;
    private String webPath = "/cost";
    private boolean proxyEnabled = true;
    private Map<String, String> upstreamPaths = new LinkedHashMap<>();
    private int connectTimeout = 5000;
    private int readTimeout = 30000;
    private int maxRetries;
    private long retryBackoff = 200L;
    private int maxResponseBytes = 20 * 1024 * 1024;
    private String userAgent = "cost-lite-spring-boot-starter/1.0";
    private String responseMode = "status";

    public CostLiteClientProperties toClientProperties() {
        CostLiteClientProperties properties = new CostLiteClientProperties();
        properties.setBaseUrl(baseUrl);
        properties.setAdminToken(adminToken);
        properties.setOpenToken(openToken);
        properties.setConnectTimeoutMillis(connectTimeout);
        properties.setReadTimeoutMillis(readTimeout);
        properties.setMaxRetries(maxRetries);
        properties.setRetryBackoffMillis(retryBackoff);
        properties.setMaxResponseBytes(maxResponseBytes);
        properties.setUserAgent(userAgent);
        return properties;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getWebPath() {
        return webPath;
    }

    public void setWebPath(String webPath) {
        this.webPath = webPath;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public Map<String, String> getUpstreamPaths() {
        return upstreamPaths;
    }

    public void setUpstreamPaths(Map<String, String> upstreamPaths) {
        this.upstreamPaths = upstreamPaths == null
                ? new LinkedHashMap<String, String>()
                : new LinkedHashMap<>(upstreamPaths);
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(long retryBackoff) {
        this.retryBackoff = retryBackoff;
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

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }
}
