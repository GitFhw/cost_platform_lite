package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches a third-party access-profile payload and exposes normalized fetch metadata.
 */
@Service
public class AccessProfilePayloadFetchService {
    private final AccessProfileFetchSupport fetchSupport;
    private final AccessProfileHttpClient accessProfileHttpClient;
    private final ObjectMapper objectMapper;

    public AccessProfilePayloadFetchService(AccessProfileFetchSupport fetchSupport,
                                            AccessProfileHttpClient accessProfileHttpClient) {
        this.fetchSupport = fetchSupport;
        this.accessProfileHttpClient = accessProfileHttpClient;
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> fetch(CostAccessProfile profile, String requestPayloadJson) {
        return fetch(profile, requestPayloadJson, fetchSupport.build(profile), null, "");
    }

    public Map<String, Object> fetch(CostAccessProfile profile, String requestPayloadJson,
                                     AccessProfileFetchConfig fetchConfig, Integer pageNo, String cursor) {
        String requestMethod = StringUtils.defaultIfEmpty(StringUtils.upperCase(profile.getRequestMethod()), "GET");
        Object requestPayload = fetchSupport.applyRequestControls(
                parseOptionalJsonObject(firstNonBlank(requestPayloadJson, profile.getSamplePayloadJson())),
                fetchConfig, pageNo, cursor);
        AccessProfileHttpResult accessResult = accessProfileHttpClient.fetch(profile, requestMethod, requestPayload);

        LinkedHashMap<String, Object> fetchMeta = new LinkedHashMap<>();
        fetchMeta.put("requestMethod", accessResult.requestMethod);
        fetchMeta.put("endpointUrl", accessResult.endpointUrl);
        fetchMeta.put("resolvedUrl", accessResult.resolvedUrl);
        fetchMeta.put("statusCode", accessResult.statusCode);
        fetchMeta.put("contentType", accessResult.contentType);
        fetchMeta.put("sourceType", accessResult.sourceType);
        fetchMeta.put("requestPayloadProvided", accessResult.requestPayloadProvided);
        fetchMeta.put("responseSize", accessResult.responseSize);
        fetchMeta.put("pageNo", pageNo);
        fetchMeta.put("cursor", cursor);
        fetchMeta.put("recordsPath", fetchConfig.recordsPath);
        fetchMeta.put("pagingMode", fetchConfig.pagingMode);
        fetchMeta.put("paged", fetchConfig.paged);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("requestPayload", accessResult.requestPayload);
        result.put("responsePayload", accessResult.responsePayload);
        result.put("fetchMeta", fetchMeta);
        return result;
    }

    private Object parseOptionalJsonObject(String json) {
        String normalized = StringUtils.trim(json);
        if (StringUtils.isEmpty(normalized)) {
            return null;
        }
        try {
            return objectMapper.readValue(normalized, Object.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 解析失败：" + e.getOriginalMessage());
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }
}
