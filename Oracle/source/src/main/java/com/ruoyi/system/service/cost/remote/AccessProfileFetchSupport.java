package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Parses and advances access-profile fetch pagination.
 */
@Service
public class AccessProfileFetchSupport {
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int DEFAULT_MAX_PAGES = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccessProfileFetchConfig build(CostAccessProfile profile) {
        Map<String, Object> config = parseOptionalJsonMap(profile.getFetchConfigJson());
        Map<String, Object> paging = config.get("paging") instanceof Map ? castMap(config.get("paging")) : Collections.emptyMap();
        AccessProfileFetchConfig fetchConfig = new AccessProfileFetchConfig();
        fetchConfig.recordsPath = firstNonBlank(stringValue(config.get("recordsPath")), stringValue(config.get("recordPath")));
        fetchConfig.pagingMode = StringUtils.defaultIfEmpty(
                StringUtils.upperCase(firstNonBlank(stringValue(paging.get("mode")), stringValue(config.get("pagingMode")))),
                AccessProfileFetchConfig.PAGING_MODE_NONE);
        fetchConfig.paged = AccessProfileFetchConfig.PAGING_MODE_PAGE_NO.equals(fetchConfig.pagingMode)
                || AccessProfileFetchConfig.PAGING_MODE_CURSOR.equals(fetchConfig.pagingMode);
        fetchConfig.pageField = firstNonBlank(stringValue(paging.get("pageField")), "pageNo");
        fetchConfig.pageSizeField = firstNonBlank(stringValue(paging.get("pageSizeField")), "pageSize");
        fetchConfig.cursorField = firstNonBlank(stringValue(paging.get("cursorField")), "cursor");
        fetchConfig.pageSize = Math.max(1,
                NumberUtils.toInt(firstNonBlank(stringValue(paging.get("pageSize")), stringValue(config.get("pageSize"))),
                        DEFAULT_PAGE_SIZE));
        fetchConfig.maxPages = Math.max(1,
                NumberUtils.toInt(firstNonBlank(stringValue(paging.get("maxPages")), stringValue(config.get("maxPages"))),
                        DEFAULT_MAX_PAGES));
        fetchConfig.startPage = Math.max(1,
                NumberUtils.toInt(firstNonBlank(stringValue(paging.get("startPage")), stringValue(paging.get("startPageNo"))),
                        1));
        fetchConfig.hasMorePath = firstNonBlank(stringValue(paging.get("hasMorePath")), stringValue(config.get("hasMorePath")));
        fetchConfig.nextCursorPath = firstNonBlank(stringValue(paging.get("nextCursorPath")), stringValue(config.get("nextCursorPath")));
        fetchConfig.totalPath = firstNonBlank(stringValue(paging.get("totalPath")), stringValue(config.get("totalPath")));
        return fetchConfig;
    }

    public AccessProfileFetchCheckpoint initialCheckpoint(String requestPayloadJson, String samplePayloadJson,
                                                          AccessProfileFetchConfig fetchConfig) {
        AccessProfileFetchCheckpoint checkpoint = new AccessProfileFetchCheckpoint();
        checkpoint.requestPayloadJson = firstNonBlank(StringUtils.trim(requestPayloadJson), StringUtils.trim(samplePayloadJson));
        checkpoint.pagingMode = fetchConfig.pagingMode;
        checkpoint.recordsPath = fetchConfig.recordsPath;
        checkpoint.pageSize = fetchConfig.pageSize;
        checkpoint.maxPages = fetchConfig.maxPages;
        checkpoint.nextPageNo = fetchConfig.startPage;
        checkpoint.nextCursor = "";
        checkpoint.hasMore = true;
        return checkpoint;
    }

    public AccessProfileFetchCheckpoint terminalCheckpoint(String requestPayloadJson,
                                                           AccessProfileFetchConfig fetchConfig, Integer mappedRecordCount) {
        AccessProfileFetchCheckpoint checkpoint = initialCheckpoint(requestPayloadJson, "", fetchConfig);
        checkpoint.hasMore = false;
        checkpoint.mappedRecordCount = mappedRecordCount;
        return checkpoint;
    }

    public AccessProfileFetchCheckpoint parseCheckpoint(String checkpointJson) {
        Map<String, Object> checkpointMap = parseOptionalJsonMap(checkpointJson);
        AccessProfileFetchCheckpoint checkpoint = new AccessProfileFetchCheckpoint();
        checkpoint.requestPayloadJson = stringValue(checkpointMap.get("requestPayloadJson"));
        checkpoint.pagingMode = stringValue(checkpointMap.get("pagingMode"));
        checkpoint.recordsPath = stringValue(checkpointMap.get("recordsPath"));
        checkpoint.nextPageNo = parseNullableInteger(checkpointMap.get("nextPageNo"));
        checkpoint.nextCursor = stringValue(checkpointMap.get("nextCursor"));
        checkpoint.hasMore = checkpointMap.get("hasMore") == null ? null : Boolean.parseBoolean(String.valueOf(checkpointMap.get("hasMore")));
        checkpoint.responseTotal = parseNullableInteger(checkpointMap.get("responseTotal"));
        checkpoint.fetchedPageCount = parseNullableInteger(checkpointMap.get("fetchedPageCount"));
        checkpoint.fetchedRecordCount = parseNullableInteger(checkpointMap.get("fetchedRecordCount"));
        checkpoint.mappedRecordCount = parseNullableInteger(checkpointMap.get("mappedRecordCount"));
        checkpoint.lastPageNo = parseNullableInteger(checkpointMap.get("lastPageNo"));
        checkpoint.lastCursor = stringValue(checkpointMap.get("lastCursor"));
        checkpoint.lastResolvedUrl = stringValue(checkpointMap.get("lastResolvedUrl"));
        checkpoint.pageSize = parseNullableInteger(checkpointMap.get("pageSize"));
        checkpoint.maxPages = parseNullableInteger(checkpointMap.get("maxPages"));
        return checkpoint;
    }

    public Object applyRequestControls(Object requestPayload, AccessProfileFetchConfig fetchConfig, Integer pageNo,
                                       String cursor) {
        if (!fetchConfig.paged) {
            return requestPayload;
        }
        LinkedHashMap<String, Object> payload = requestPayload instanceof Map
                ? new LinkedHashMap<>(castMap(requestPayload))
                : new LinkedHashMap<>();
        if (AccessProfileFetchConfig.PAGING_MODE_PAGE_NO.equals(fetchConfig.pagingMode)) {
            payload.put(fetchConfig.pageField, pageNo == null ? fetchConfig.startPage : pageNo);
            payload.put(fetchConfig.pageSizeField, fetchConfig.pageSize);
        } else if (AccessProfileFetchConfig.PAGING_MODE_CURSOR.equals(fetchConfig.pagingMode)) {
            payload.put(fetchConfig.pageSizeField, fetchConfig.pageSize);
            if (StringUtils.isNotEmpty(cursor)) {
                payload.put(fetchConfig.cursorField, cursor);
            }
        }
        return payload;
    }

    public List<Map<String, Object>> extractResponseRecords(CostAccessProfile profile, Object responsePayload) {
        return extractResponseRecords(build(profile), responsePayload);
    }

    public List<Map<String, Object>> extractResponseRecords(AccessProfileFetchConfig fetchConfig, Object responsePayload) {
        Object recordPayload = responsePayload;
        if (StringUtils.isNotEmpty(fetchConfig.recordsPath)) {
            recordPayload = resolveByPath(responsePayload, fetchConfig.recordsPath);
        }
        if (recordPayload instanceof Map) {
            return Collections.singletonList(castMap(recordPayload));
        }
        if (recordPayload instanceof List) {
            List<Map<String, Object>> records = new ArrayList<>();
            for (Object item : (List<?>) recordPayload) {
                if (!(item instanceof Map)) {
                    throw new ServiceException("预演输入数组中的每一项都必须是 JSON 对象");
                }
                records.add(castMap(item));
            }
            return records;
        }
        throw new ServiceException("直连业务接口返回结果无法解析为标准计费对象记录集合");
    }

    public Integer resolveTotal(AccessProfileFetchConfig fetchConfig, Object responsePayload, Integer fallback) {
        if (StringUtils.isEmpty(fetchConfig.totalPath)) {
            return fallback;
        }
        Object totalValue = resolveByPath(responsePayload, fetchConfig.totalPath);
        if (totalValue == null) {
            return fallback;
        }
        return NumberUtils.toInt(String.valueOf(totalValue), fallback == null ? 0 : fallback);
    }

    public boolean hasMore(AccessProfileFetchConfig fetchConfig, int pageRecordCount, Object responsePayload,
                           Integer responseTotal, int totalFetchedCount, int fetchedPageCount) {
        if (!fetchConfig.paged) {
            return false;
        }
        if (StringUtils.isNotEmpty(fetchConfig.hasMorePath)) {
            Object value = resolveByPath(responsePayload, fetchConfig.hasMorePath);
            if (value != null) {
                return Boolean.parseBoolean(String.valueOf(value));
            }
        }
        if (responseTotal != null && responseTotal > 0) {
            return totalFetchedCount < responseTotal;
        }
        if (pageRecordCount < fetchConfig.pageSize) {
            return false;
        }
        return fetchedPageCount < fetchConfig.maxPages;
    }

    public String resolveNextCursor(AccessProfileFetchConfig fetchConfig, Object responsePayload) {
        if (StringUtils.isEmpty(fetchConfig.nextCursorPath)) {
            return "";
        }
        Object value = resolveByPath(responsePayload, fetchConfig.nextCursorPath);
        return value == null ? "" : String.valueOf(value);
    }

    public Map<String, Object> buildPageSummary(Map<String, Object> pageMeta, int recordCount) {
        LinkedHashMap<String, Object> pageSummary = new LinkedHashMap<>();
        pageSummary.put("pageNo", pageMeta == null ? null : pageMeta.get("pageNo"));
        pageSummary.put("cursor", pageMeta == null ? null : pageMeta.get("cursor"));
        pageSummary.put("recordCount", recordCount);
        pageSummary.put("resolvedUrl", pageMeta == null ? null : pageMeta.get("resolvedUrl"));
        return pageSummary;
    }

    public Map<String, Object> buildFetchMeta(AccessProfileFetchConfig fetchConfig,
                                              List<Map<String, Object>> pageSummaries,
                                              int totalRawCount,
                                              Integer responseTotal,
                                              boolean maxPageReached,
                                              Boolean resumed) {
        LinkedHashMap<String, Object> fetchMeta = new LinkedHashMap<>();
        fetchMeta.put("paged", true);
        fetchMeta.put("pagingMode", fetchConfig.pagingMode);
        fetchMeta.put("pageSize", fetchConfig.pageSize);
        fetchMeta.put("pageCount", pageSummaries == null ? 0 : pageSummaries.size());
        fetchMeta.put("recordCount", totalRawCount);
        fetchMeta.put("responseTotal", responseTotal);
        fetchMeta.put("recordsPath", fetchConfig.recordsPath);
        fetchMeta.put("pageSummaries", pageSummaries);
        fetchMeta.put("maxPages", fetchConfig.maxPages);
        fetchMeta.put("maxPageReached", maxPageReached);
        if (resumed != null) {
            fetchMeta.put("resumed", resumed);
        }
        return fetchMeta;
    }

    public void advanceCheckpoint(AccessProfileFetchCheckpoint checkpoint,
                                  AccessProfileFetchConfig fetchConfig,
                                  String requestPayloadJson,
                                  Integer responseTotal,
                                  int totalFetchedPageCount,
                                  int totalRawCount,
                                  int totalMappedCount,
                                  int pageNo,
                                  String nextCursor,
                                  boolean hasMore,
                                  Map<String, Object> pageMeta) {
        checkpoint.requestPayloadJson = requestPayloadJson;
        checkpoint.pagingMode = fetchConfig.pagingMode;
        checkpoint.recordsPath = fetchConfig.recordsPath;
        checkpoint.pageSize = fetchConfig.pageSize;
        checkpoint.maxPages = fetchConfig.maxPages;
        checkpoint.responseTotal = responseTotal;
        checkpoint.fetchedPageCount = totalFetchedPageCount;
        checkpoint.fetchedRecordCount = totalRawCount;
        checkpoint.mappedRecordCount = totalMappedCount;
        checkpoint.lastPageNo = pageNo;
        checkpoint.lastCursor = stringValue(pageMeta == null ? null : pageMeta.get("cursor"));
        checkpoint.lastResolvedUrl = stringValue(pageMeta == null ? null : pageMeta.get("resolvedUrl"));
        checkpoint.nextPageNo = pageNo + 1;
        checkpoint.nextCursor = nextCursor;
        checkpoint.hasMore = hasMore;
    }

    public void markFailed(AccessProfileFetchCheckpoint checkpoint, int pageNo, String nextCursor) {
        checkpoint.hasMore = true;
        checkpoint.nextPageNo = pageNo;
        checkpoint.nextCursor = nextCursor;
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

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer parseNullableInteger(Object value) {
        return value == null ? null : NumberUtils.toInt(String.valueOf(value));
    }
}
