package com.ruoyi.lite.plugin;

import java.util.Map;

/**
 * 将第三方原始报文转换为母体标准计费输入的扩展点。
 */
public interface InputAdapterPlugin {
    String getCode();

    Map<String, Object> adapt(Map<String, Object> rawInput, Map<String, Object> context);
}
