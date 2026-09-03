package com.ruoyi.system.service.cost.remote;

import java.util.*;

/**
 * Holds state while an access-profile paged fetch is being orchestrated.
 */
public class AccessProfileFetchSession {
    public final Set<String> seenBizNos = new LinkedHashSet<>();
    public final List<Map<String, Object>> previewMappedRecords = new ArrayList<>();
    public final LinkedHashSet<String> missingPaths = new LinkedHashSet<>();
    public final List<Map<String, Object>> pageSummaries = new ArrayList<>();
    public Object firstResponsePayload;
    public Object firstRequestPayload;
    public int totalMappedCount;
    public int totalRawCount;
    public int totalFetchedPageCount;
    public Integer responseTotal;
    public String nextCursor = "";
    public boolean hasMore = true;
    public int pageNo;
    public String requestPayloadJson = "";

    public static AccessProfileFetchSession resume(AccessProfileFetchCheckpoint checkpoint,
                                                   AccessProfileFetchConfig fetchConfig,
                                                   String requestPayloadJson,
                                                   int existingMappedCount) {
        AccessProfileFetchSession session = new AccessProfileFetchSession();
        session.totalMappedCount = existingMappedCount;
        session.totalRawCount = checkpoint.fetchedRecordCount == null ? 0 : checkpoint.fetchedRecordCount;
        session.totalFetchedPageCount = checkpoint.fetchedPageCount == null ? 0 : checkpoint.fetchedPageCount;
        session.responseTotal = checkpoint.responseTotal;
        session.nextCursor = checkpoint.nextCursor == null ? "" : checkpoint.nextCursor;
        session.hasMore = checkpoint.hasMore == null || checkpoint.hasMore;
        session.pageNo = checkpoint.nextPageNo == null || checkpoint.nextPageNo < 1 ? fetchConfig.startPage : checkpoint.nextPageNo;
        session.requestPayloadJson = requestPayloadJson == null ? "" : requestPayloadJson;
        return session;
    }

    public void captureFirstPayload(Map<String, Object> fetchResult) {
        if (firstResponsePayload != null || fetchResult == null) {
            return;
        }
        firstResponsePayload = fetchResult.get("responsePayload");
        firstRequestPayload = fetchResult.get("requestPayload");
    }

    public void appendPreviewRecords(List<Map<String, Object>> pageRecords, int limit) {
        if (previewMappedRecords.size() >= limit || pageRecords == null) {
            return;
        }
        for (Map<String, Object> record : pageRecords) {
            previewMappedRecords.add(record);
            if (previewMappedRecords.size() >= limit) {
                return;
            }
        }
    }
}
