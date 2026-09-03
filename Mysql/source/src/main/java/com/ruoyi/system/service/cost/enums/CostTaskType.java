package com.ruoyi.system.service.cost.enums;

public enum CostTaskType {
    SIMULATION_BATCH("SIMULATION_BATCH"),
    FORMAL_SINGLE("FORMAL_SINGLE"),
    FORMAL_BATCH("FORMAL_BATCH");

    private final String code;

    CostTaskType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
