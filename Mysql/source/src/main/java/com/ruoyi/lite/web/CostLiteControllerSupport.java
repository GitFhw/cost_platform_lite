package com.ruoyi.lite.web;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lite.config.CostLiteProperties;

/**
 * 轻量控制器公共能力。
 */
public abstract class CostLiteControllerSupport extends BaseController {
    protected final CostLiteProperties properties;

    protected CostLiteControllerSupport(CostLiteProperties properties) {
        this.properties = properties;
    }

    protected String operator() {
        try {
            return StringUtils.defaultIfEmpty(getUsername(), configuredOperator());
        } catch (Exception ignored) {
            return configuredOperator();
        }
    }

    private String configuredOperator() {
        return StringUtils.defaultIfEmpty(properties.getOperator(), "lite-admin");
    }
}
