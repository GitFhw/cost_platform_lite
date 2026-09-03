package com.ruoyi.system.service.cost.enums;

public enum CostVariableSourceType {
    INPUT("INPUT"),
    DICT("DICT"),
    REMOTE("REMOTE"),
    FORMULA("FORMULA");

    private final String code;

    CostVariableSourceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
