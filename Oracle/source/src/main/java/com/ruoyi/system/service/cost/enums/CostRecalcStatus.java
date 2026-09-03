package com.ruoyi.system.service.cost.enums;

public enum CostRecalcStatus {
    PENDING_APPROVAL("PENDING_APPROVAL"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String code;

    CostRecalcStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
