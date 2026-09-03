package com.ruoyi.system.service.cost.enums;

public enum CostInputSourceType {
    INLINE_JSON("INLINE_JSON"),
    INPUT_BATCH("INPUT_BATCH");

    private final String code;

    CostInputSourceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
