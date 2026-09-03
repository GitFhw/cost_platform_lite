package com.ruoyi.system.service.cost.enums;

public enum CostSnapshotSource {
    PUBLISHED("PUBLISHED"),
    DRAFT("DRAFT");

    private final String code;

    CostSnapshotSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
