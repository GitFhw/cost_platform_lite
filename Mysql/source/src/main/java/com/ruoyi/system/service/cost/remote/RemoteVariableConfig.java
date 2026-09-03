package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Third-party variable request configuration after normalization.
 */
public class RemoteVariableConfig {
    public final String variableCode;
    public final String remoteApi;
    public final String sourceSystem;
    public final String requestMethod;
    public final String contentType;
    public final ObjectNode queryConfig;
    public final ObjectNode requestHeaders;
    public final JsonNode bodyTemplate;
    public final String authType;
    public final JsonNode authConfig;
    public final String dataPath;
    public final ObjectNode responseConfig;
    public final ObjectNode mappingConfig;
    public final ObjectNode pageConfig;
    public final String adapterType;
    public final ObjectNode adapterConfig;
    public final String syncMode;
    public final String cachePolicy;
    public final String fallbackPolicy;

    public RemoteVariableConfig(String variableCode, String remoteApi, String sourceSystem, String requestMethod,
                                String contentType, ObjectNode queryConfig, ObjectNode requestHeaders,
                                JsonNode bodyTemplate, String authType, JsonNode authConfig, String dataPath,
                                ObjectNode responseConfig, ObjectNode mappingConfig, ObjectNode pageConfig,
                                String adapterType, ObjectNode adapterConfig, String syncMode, String cachePolicy,
                                String fallbackPolicy) {
        this.variableCode = variableCode;
        this.remoteApi = remoteApi;
        this.sourceSystem = sourceSystem;
        this.requestMethod = requestMethod;
        this.contentType = contentType;
        this.queryConfig = queryConfig;
        this.requestHeaders = requestHeaders;
        this.bodyTemplate = bodyTemplate;
        this.authType = authType;
        this.authConfig = authConfig;
        this.dataPath = dataPath;
        this.responseConfig = responseConfig;
        this.mappingConfig = mappingConfig;
        this.pageConfig = pageConfig;
        this.adapterType = adapterType;
        this.adapterConfig = adapterConfig;
        this.syncMode = syncMode;
        this.cachePolicy = cachePolicy;
        this.fallbackPolicy = fallbackPolicy;
    }
}
