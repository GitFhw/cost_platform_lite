package com.ruoyi.system.service.cost.enums;

public enum CostDetailStatus {
    INIT("INIT"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String code;

    CostDetailStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
