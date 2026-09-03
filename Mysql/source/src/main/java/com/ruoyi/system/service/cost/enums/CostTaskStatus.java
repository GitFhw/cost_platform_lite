package com.ruoyi.system.service.cost.enums;

public enum CostTaskStatus {
    INIT("INIT"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    PART_SUCCESS("PART_SUCCESS"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String code;

    CostTaskStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
