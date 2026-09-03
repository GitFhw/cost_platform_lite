package com.ruoyi.system.service.cost.remote;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.service.cost.remote.AccessProfileInputMappingService.InputBuildContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Processes one access-profile page from remote payload to persisted input items.
 */
@Service
public class AccessProfileFetchPageProcessor {
    private final AccessProfilePayloadFetchService payloadFetchService;
    private final AccessProfileFetchSupport fetchSupport;
    private final AccessProfileInputMappingService inputMappingService;
    private final AccessProfileInputBatchItemService inputBatchItemService;

    public AccessProfileFetchPageProcessor(AccessProfilePayloadFetchService payloadFetchService,
                                           AccessProfileFetchSupport fetchSupport,
                                           AccessProfileInputMappingService inputMappingService,
                                           AccessProfileInputBatchItemService inputBatchItemService) {
        this.payloadFetchService = payloadFetchService;
        this.fetchSupport = fetchSupport;
        this.inputMappingService = inputMappingService;
        this.inputBatchItemService = inputBatchItemService;
    }

    public Map<String, Object> process(CostAccessProfile profile,
                                       InputBuildContext context,
                                       CostCalcInputBatch batch,
                                       AccessProfileFetchConfig fetchConfig,
                                       AccessProfileFetchSession session,
                                       int previewLimit) {
        Map<String, Object> fetchResult = payloadFetchService.fetch(
                profile, session.requestPayloadJson, fetchConfig, session.pageNo, session.nextCursor);
        session.captureFirstPayload(fetchResult);

        Object responsePayload = fetchResult.get("responsePayload");
        List<Map<String, Object>> rawRecords = fetchSupport.extractResponseRecords(profile, responsePayload);
        session.totalRawCount += rawRecords.size();

        Map<String, Object> mappedPage = inputMappingService.buildMappedInputResult(
                context, rawRecords, session.totalMappedCount + 1);
        List<Map<String, Object>> mappedRecords = castFieldList(mappedPage.get("mappedRecords"));
        session.appendPreviewRecords(mappedRecords, previewLimit);
        session.missingPaths.addAll(castStringList(mappedPage.get("missingPaths")));
        session.totalMappedCount += inputBatchItemService.appendItems(
                batch, mappedRecords, session.totalMappedCount, session.seenBizNos);

        Map<String, Object> pageMeta = castMap(fetchResult.get("fetchMeta"));
        session.pageSummaries.add(fetchSupport.buildPageSummary(pageMeta, rawRecords.size()));
        session.totalFetchedPageCount++;
        session.responseTotal = fetchSupport.resolveTotal(fetchConfig, responsePayload, session.responseTotal);
        session.hasMore = fetchSupport.hasMore(fetchConfig, rawRecords.size(), responsePayload,
                session.responseTotal, session.totalRawCount, session.pageSummaries.size());
        session.nextCursor = fetchSupport.resolveNextCursor(fetchConfig, responsePayload);
        if (fetchConfig.isCursorMode() && StringUtils.isEmpty(session.nextCursor)) {
            session.hasMore = false;
        }
        return pageMeta;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private List<Map<String, Object>> castFieldList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add(castMap(item));
            }
        }
        return result;
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}
