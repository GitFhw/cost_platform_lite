package com.ruoyi.system.service.cost.remote;

/**
 * Parsed pagination strategy for access profile fetching.
 */
public class AccessProfileFetchConfig {
    public static final String PAGING_MODE_NONE = "NONE";
    public static final String PAGING_MODE_PAGE_NO = "PAGE_NO";
    public static final String PAGING_MODE_CURSOR = "CURSOR";

    public boolean paged;
    public String pagingMode = PAGING_MODE_NONE;
    public String recordsPath;
    public String pageField = "pageNo";
    public String pageSizeField = "pageSize";
    public String cursorField = "cursor";
    public String hasMorePath;
    public String nextCursorPath;
    public String totalPath;
    public int pageSize = 500;
    public int maxPages = 200;
    public int startPage = 1;

    public boolean isCursorMode() {
        return PAGING_MODE_CURSOR.equals(pagingMode);
    }
}
