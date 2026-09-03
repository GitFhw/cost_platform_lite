package com.ruoyi.system.service.cost.enums;

public enum CostAccessSourceType {
    HTTP_API("HTTP_API");

    private final String code;

    CostAccessSourceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
