package com.costplatform.lite.extension;

import java.util.ArrayList;
import java.util.List;

/** Page result returned by a host option provider. */
public class CostLiteOptionPage {
    private List<CostLiteOption> rows = new ArrayList<>();
    private long total;
    private boolean hasMore;

    public CostLiteOptionPage() {
    }

    public CostLiteOptionPage(List<CostLiteOption> rows, long total, boolean hasMore) {
        this.rows = rows == null ? new ArrayList<CostLiteOption>() : rows;
        this.total = total;
        this.hasMore = hasMore;
    }

    public List<CostLiteOption> getRows() {
        return rows;
    }

    public void setRows(List<CostLiteOption> rows) {
        this.rows = rows == null ? new ArrayList<CostLiteOption>() : rows;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
