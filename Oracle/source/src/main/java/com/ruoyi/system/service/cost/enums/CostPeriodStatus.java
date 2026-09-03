package com.ruoyi.system.service.cost.enums;

public enum CostPeriodStatus {
    NOT_STARTED("NOT_STARTED"),
    IN_PROGRESS("IN_PROGRESS"),
    CLOSED("CLOSED"),
    SEALED("SEALED");

    private final String code;

    CostPeriodStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
