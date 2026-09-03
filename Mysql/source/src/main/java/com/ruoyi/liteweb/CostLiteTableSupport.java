package com.ruoyi.lite.web;

import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;

import java.util.Collections;
import java.util.List;

/**
 * 轻量接口分页响应工具。
 */
public final class CostLiteTableSupport {
    private CostLiteTableSupport() {
    }

    public static TableDataInfo table(List<?> source, Integer pageNum, Integer pageSize, CostLiteProperties properties) {
        List<?> rows = source == null ? Collections.emptyList() : source;
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int maxPageSize = properties == null ? 200 : Math.max(1, properties.getMaxPageSize());
        int safeSize = pageSize == null || pageSize < 1 ? maxPageSize : Math.min(pageSize, maxPageSize);
        int from = Math.min((safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setTotal(rows.size());
        result.setRows(rows.subList(from, to));
        return result;
    }

    public static AjaxResult data(Object data) {
        return AjaxResult.success(data);
    }
}
