package com.ruoyi.system.service.cost.enums;

public enum CostInputBatchStatus {
    LOADING("LOADING"),
    PARTIAL("PARTIAL"),
    READY("READY"),
    SUBMITTED("SUBMITTED"),
    CONSUMED("CONSUMED");

    private final String code;

    CostInputBatchStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
