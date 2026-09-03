package com.ruoyi.system.service.cost.variable.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Resolves runtime remote variable values from prepared remote context and cache snapshots.
 */
@Service
public class RuntimeRemoteVariableValueService {
    private static final String REMOTE_CONTEXT_ROOT = "remoteContext";
    private static final String REMOTE_PAYLOAD_ROOT = "remotePayload";
    private static final String REMOTE_DATA_ROOT = "remoteData";
    private static final String FALLBACK_POLICY_FAIL_FAST = "FAIL_FAST";
    private static final String FALLBACK_POLICY_DEFAULT_VALUE = "DEFAULT_VALUE";
    private static final String FALLBACK_POLICY_LAST_SNAPSHOT = "LAST_SNAPSHOT";
    private static final String CACHE_POLICY_NONE = "NONE";
    private static final String CACHE_POLICY_TTL = "TTL";
    private static final String REMOTE_LAST_SNAPSHOT_CACHE_PREFIX = "cost:runtime:remote:last:";
    private static final int REMOTE_CACHE_TTL_MINUTES = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RedisCache redisCache;

    public Object resolve(CostRunServiceImpl.RuntimeVariable variable, Map<String, Object> baseContext) {
        Map<String, Object> mapping = parseOptionalJsonMap(variable.mappingConfigJson);
        mapping.putIfAbsent("sourceSystem", variable.sourceSystem);
        mapping.putIfAbsent("variableCode", variable.variableCode);
        Object value = resolveRemoteValueFromInput(baseContext, variable, mapping);
        if (value != null) {
            cacheRemoteVariableSnapshot(variable, baseContext, value);
            return value;
        }
        return resolveRemoteFallbackValue(variable, baseContext);
    }

    public String buildTemplatePath(CostRunServiceImpl.RuntimeVariable variable) {
        if (variable == null) {
            return "";
        }
        Map<String, Object> mapping = parseOptionalJsonMap(variable.mappingConfigJson);
        String valuePath = resolveRemoteValuePath(variable, mapping);
        String configuredContextPath = stringValue(mapping.get("contextPath"));
        if (StringUtils.isNotEmpty(configuredContextPath)) {
            return appendPath(configuredContextPath, valuePath);
        }
        if (isExplicitTemplatePath(variable.dataPath)) {
            return variable.dataPath;
        }
        String scopeCode = firstNonBlank(variable.sourceSystem, variable.variableCode);
        return REMOTE_CONTEXT_ROOT + "." + scopeCode + "." + variable.variableCode + "." + valuePath;
    }

    private Object resolveRemoteValueFromInput(Map<String, Object> input,
                                               CostRunServiceImpl.RuntimeVariable variable,
                                               Map<String, Object> mapping) {
        if (input == null || variable == null) {
            return null;
        }
        for (Object candidate : buildRemoteCandidates(input, variable, mapping)) {
            Object normalized = normalizeRemoteCandidate(candidate, mapping, input);
            Object value = extractRemoteValue(normalized, variable, mapping);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<Object> buildRemoteCandidates(Map<String, Object> input,
                                               CostRunServiceImpl.RuntimeVariable variable,
                                               Map<String, Object> mapping) {
        List<Object> candidates = new ArrayList<>();
        addRemoteCandidate(candidates, resolveByPath(input, stringValue(mapping.get("contextPath"))));
        addRemoteCandidate(candidates, resolveByPath(input, REMOTE_CONTEXT_ROOT));
        addRemoteCandidate(candidates, resolveByPath(input, REMOTE_PAYLOAD_ROOT));
        addRemoteCandidate(candidates, resolveByPath(input, REMOTE_DATA_ROOT));
        addRemoteCandidate(candidates, resolveByPath(input, variable.variableCode));
        addRemoteCandidate(candidates, resolveByPath(input, variable.dataPath));
        return candidates;
    }

    private void addRemoteCandidate(List<Object> candidates, Object candidate) {
        if (candidate != null) {
            candidates.add(candidate);
        }
    }

    private Object normalizeRemoteCandidate(Object candidate, Map<String, Object> mapping, Map<String, Object> input) {
        if (candidate instanceof Map) {
            Map<String, Object> candidateMap = castMap(candidate);
            Object nested = resolveNestedRemoteCandidate(candidateMap, mapping);
            if (nested != null && nested != candidate) {
                return normalizeRemoteCandidate(nested, mapping, input);
            }
            return candidateMap;
        }
        if (candidate instanceof List) {
            return selectRemoteMatchedRow((List<?>) candidate, mapping, input);
        }
        return candidate;
    }

    private Object resolveNestedRemoteCandidate(Map<String, Object> candidate, Map<String, Object> mapping) {
        String scopeKey = stringValue(mapping.get("scopeKey"));
        if (StringUtils.isNotEmpty(scopeKey) && candidate.containsKey(scopeKey)) {
            return candidate.get(scopeKey);
        }
        String sourceSystem = stringValue(mapping.get("sourceSystem"));
        String variableCode = stringValue(mapping.get("variableCode"));
        if (StringUtils.isNotEmpty(sourceSystem)) {
            Object systemScoped = candidate.get(sourceSystem);
            if (systemScoped instanceof Map && StringUtils.isNotEmpty(variableCode)
                    && ((Map<?, ?>) systemScoped).containsKey(variableCode)) {
                return ((Map<?, ?>) systemScoped).get(variableCode);
            }
            if (systemScoped != null) {
                return systemScoped;
            }
        }
        if (StringUtils.isNotEmpty(variableCode) && candidate.containsKey(variableCode)) {
            return candidate.get(variableCode);
        }
        return candidate;
    }

    private Object selectRemoteMatchedRow(List<?> rows, Map<String, Object> mapping, Map<String, Object> input) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, String> matchBy = normalizeMatchByConfig(mapping.get("matchBy"));
        for (Object row : rows) {
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> rowMap = castMap(row);
            if (matchBy.isEmpty()) {
                if (matchesRemoteRow(rowMap, input, "objectCode", "objectCode")
                        || matchesRemoteRow(rowMap, input, "bizNo", "bizNo")) {
                    return rowMap;
                }
                continue;
            }
            boolean matched = true;
            for (Map.Entry<String, String> entry : matchBy.entrySet()) {
                if (!matchesRemoteRow(rowMap, input, entry.getKey(), entry.getValue())) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return rowMap;
            }
        }
        Object first = rows.get(0);
        return first instanceof Map ? castMap(first) : first;
    }

    private Map<String, String> normalizeMatchByConfig(Object raw) {
        LinkedHashMap<String, String> matchBy = new LinkedHashMap<>();
        if (!(raw instanceof Map)) {
            return matchBy;
        }
        Map<?, ?> rawMap = (Map<?, ?>) raw;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String remoteField = stringValue(entry.getKey());
            String localField = stringValue(entry.getValue());
            if (StringUtils.isNotEmpty(remoteField) && StringUtils.isNotEmpty(localField)) {
                matchBy.put(remoteField, localField);
            }
        }
        return matchBy;
    }

    private boolean matchesRemoteRow(Map<String, Object> row, Map<String, Object> input, String remoteField, String localField) {
        if (row == null || input == null || StringUtils.isEmpty(remoteField) || StringUtils.isEmpty(localField)) {
            return false;
        }
        Object remoteValue = resolveByPath(row, remoteField);
        Object localValue = resolveByPath(input, localField);
        return remoteValue != null && localValue != null
                && StringUtils.equals(String.valueOf(remoteValue), String.valueOf(localValue));
    }

    private Object extractRemoteValue(Object candidate,
                                      CostRunServiceImpl.RuntimeVariable variable,
                                      Map<String, Object> mapping) {
        if (candidate == null) {
            return null;
        }
        if (!(candidate instanceof Map)) {
            return candidate;
        }
        Map<String, Object> candidateMap = castMap(candidate);
        for (String path : buildRemoteValuePaths(variable, mapping)) {
            Object value = resolveByPath(candidateMap, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<String> buildRemoteValuePaths(CostRunServiceImpl.RuntimeVariable variable, Map<String, Object> mapping) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        String configuredValuePath = stringValue(mapping.get("valuePath"));
        if (StringUtils.isNotEmpty(configuredValuePath)) {
            paths.add(configuredValuePath);
        }
        if (StringUtils.isNotEmpty(variable.dataPath)) {
            paths.add(variable.dataPath);
        }
        if (StringUtils.isNotEmpty(variable.variableCode)) {
            paths.add(variable.variableCode);
        }
        paths.add("mappedValue");
        paths.add("value");
        return new ArrayList<>(paths);
    }

    private String resolveRemoteValuePath(CostRunServiceImpl.RuntimeVariable variable, Map<String, Object> mapping) {
        String configuredValuePath = stringValue(mapping.get("valuePath"));
        if (StringUtils.isNotEmpty(configuredValuePath)) {
            return configuredValuePath;
        }
        if (StringUtils.isNotEmpty(variable.dataPath)) {
            return variable.dataPath;
        }
        return firstNonBlank(variable.variableCode, "value");
    }

    private Object resolveRemoteFallbackValue(CostRunServiceImpl.RuntimeVariable variable, Map<String, Object> baseContext) {
        String fallbackPolicy = firstNonBlank(StringUtils.trim(variable.fallbackPolicy), FALLBACK_POLICY_FAIL_FAST);
        if (FALLBACK_POLICY_DEFAULT_VALUE.equalsIgnoreCase(fallbackPolicy)) {
            return variable.defaultValue;
        }
        if (FALLBACK_POLICY_LAST_SNAPSHOT.equalsIgnoreCase(fallbackPolicy)) {
            Object cachedValue = readRemoteCachedValue(variable, baseContext);
            if (cachedValue != null) {
                return cachedValue;
            }
            if (variable.defaultValue != null) {
                return variable.defaultValue;
            }
        }
        throw new ServiceException("第三方变量[" + variable.variableCode + "]未获取到运行值，请检查 remoteContext/remotePayload 输入或调整兜底策略");
    }

    private Object readRemoteCachedValue(CostRunServiceImpl.RuntimeVariable variable, Map<String, Object> baseContext) {
        String cacheKey = buildRemoteLastSnapshotCacheKey(variable, baseContext);
        String cachedJson = redisCache.getCacheObject(cacheKey);
        return StringUtils.isEmpty(cachedJson) ? null : parseJsonToObject(cachedJson);
    }

    private void cacheRemoteVariableSnapshot(CostRunServiceImpl.RuntimeVariable variable,
                                             Map<String, Object> baseContext,
                                             Object value) {
        if (variable == null || value == null) {
            return;
        }
        String fallbackPolicy = firstNonBlank(StringUtils.trim(variable.fallbackPolicy), "");
        String cachePolicy = firstNonBlank(StringUtils.trim(variable.cachePolicy), CACHE_POLICY_NONE);
        if (CACHE_POLICY_NONE.equalsIgnoreCase(cachePolicy) && !FALLBACK_POLICY_LAST_SNAPSHOT.equalsIgnoreCase(fallbackPolicy)) {
            return;
        }
        String cacheKey = buildRemoteLastSnapshotCacheKey(variable, baseContext);
        String payload = writeJson(value);
        if (CACHE_POLICY_TTL.equalsIgnoreCase(cachePolicy)) {
            redisCache.setCacheObject(cacheKey, payload, REMOTE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return;
        }
        redisCache.setCacheObject(cacheKey, payload);
    }

    private String buildRemoteLastSnapshotCacheKey(CostRunServiceImpl.RuntimeVariable variable,
                                                   Map<String, Object> baseContext) {
        String sceneCode = resolveString(baseContext, "sceneCode");
        String objectCode = resolveString(baseContext, "objectCode", "object_code");
        String bizNo = resolveString(baseContext, "bizNo", "biz_no");
        String sourceSystem = firstNonBlank(variable.sourceSystem, "DEFAULT");
        String objectKey = firstNonBlank(objectCode, firstNonBlank(bizNo, "GLOBAL"));
        return REMOTE_LAST_SNAPSHOT_CACHE_PREFIX + firstNonBlank(sceneCode, "UNKNOWN")
                + ":" + sourceSystem + ":" + variable.variableCode + ":" + objectKey;
    }

    private Object parseJsonToObject(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private Map<String, Object> parseOptionalJsonMap(String json) {
        if (StringUtils.isEmpty(StringUtils.trim(json))) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private Object resolveByPath(Object input, String path) {
        if (!(input instanceof Map)) {
            return null;
        }
        return resolveByPath(castMap(input), path);
    }

    private Object resolveByPath(Map<String, Object> input, String path) {
        if (input == null || StringUtils.isEmpty(path)) {
            return null;
        }
        String[] pieces = path.split("\\.");
        Object current = input;
        for (String piece : pieces) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(piece);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String resolveString(Map<String, Object> input, String... keys) {
        if (input == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = resolveByPath(input, key);
            if (value != null && StringUtils.isNotEmpty(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(StringUtils.trim(value))) {
                return StringUtils.trim(value);
            }
        }
        return "";
    }

    private String appendPath(String prefix, String suffix) {
        String normalizedPrefix = StringUtils.trimToEmpty(prefix);
        String normalizedSuffix = StringUtils.trimToEmpty(suffix);
        if (StringUtils.isEmpty(normalizedPrefix)) {
            return normalizedSuffix;
        }
        if (StringUtils.isEmpty(normalizedSuffix) || normalizedPrefix.equals(normalizedSuffix)
                || normalizedPrefix.endsWith("." + normalizedSuffix)) {
            return normalizedPrefix;
        }
        return normalizedPrefix + "." + normalizedSuffix;
    }

    private boolean isExplicitTemplatePath(String path) {
        String normalized = StringUtils.trimToEmpty(path);
        return normalized.contains(".")
                || normalized.startsWith(REMOTE_CONTEXT_ROOT)
                || normalized.startsWith(REMOTE_PAYLOAD_ROOT)
                || normalized.startsWith(REMOTE_DATA_ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
