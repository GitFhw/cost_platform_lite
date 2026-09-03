package com.ruoyi.lite.service;

/**
 * 带留存日志主键的计费异常。
 */
public class CostLiteBillingException extends RuntimeException {
    private final Long billingLogId;
    private final Integer code;

    public CostLiteBillingException(String message, Integer code, Long billingLogId, Throwable cause) {
        super(message, cause);
        this.billingLogId = billingLogId;
        this.code = code;
    }

    public Long getBillingLogId() {
        return billingLogId;
    }

    public Integer getCode() {
        return code;
    }
}
