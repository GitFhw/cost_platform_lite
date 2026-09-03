package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;

import java.util.Map;

public final class PublishValidationSupport {
    private PublishValidationSupport() {
    }

    public static CostPublishCheckItemVo checkItem(String level, String code, String title, String message) {
        CostPublishCheckItemVo item = new CostPublishCheckItemVo();
        item.setLevel(level);
        item.setCode(code);
        item.setTitle(title);
        item.setMessage(message);
        return item;
    }

    public static String assetLabel(Map<String, Object> row, String nameKey, String codeKey) {
        return String.format("%1$s(%2$s)", stringValue(row.get(nameKey)), stringValue(row.get(codeKey)));
    }

    public static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    public static String resolveCheckResult(long blockingCount, long warningCount) {
        if (blockingCount > 0) {
            return "BLOCK";
        }
        if (warningCount > 0) {
            return "WARN";
        }
        return "PASS";
    }

    public static String resolveCheckLabel(long blockingCount, long warningCount) {
        if (blockingCount > 0) {
            return "存在阻断";
        }
        if (warningCount > 0) {
            return "存在告警";
        }
        return "校验通过";
    }
}
