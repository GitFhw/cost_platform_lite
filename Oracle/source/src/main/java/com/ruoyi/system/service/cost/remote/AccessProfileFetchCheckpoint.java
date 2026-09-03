package com.ruoyi.system.service.cost.remote;

/**
 * Checkpoint for resumable access-profile page loading.
 */
public class AccessProfileFetchCheckpoint {
    public String requestPayloadJson;
    public String pagingMode;
    public String recordsPath;
    public Integer nextPageNo;
    public String nextCursor;
    public Boolean hasMore;
    public Integer responseTotal;
    public Integer fetchedPageCount;
    public Integer fetchedRecordCount;
    public Integer mappedRecordCount;
    public Integer lastPageNo;
    public String lastCursor;
    public String lastResolvedUrl;
    public Integer pageSize;
    public Integer maxPages;
}
