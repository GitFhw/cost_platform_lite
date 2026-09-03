package com.ruoyi.system.service.cost.enums;

public enum CostVariableDataType {
    NUMBER("NUMBER"),
    BOOLEAN("BOOLEAN"),
    JSON("JSON");

    private final String code;

    CostVariableDataType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
