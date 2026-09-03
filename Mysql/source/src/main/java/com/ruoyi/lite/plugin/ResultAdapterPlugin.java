package com.ruoyi.lite.plugin;

import java.util.Map;

/**
 * 将母体计费结果转换为第三方系统所需格式的扩展点。
 */
public interface ResultAdapterPlugin {
    String getCode();

    Object adapt(Map<String, Object> billingResult, Map<String, Object> context);
}
