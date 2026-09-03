package com.ruoyi.lite.plugin;

import java.util.List;

/**
 * 公式函数扩展点。核心公式语言保持母体实现，业务特殊函数通过插件增加。
 */
public interface FormulaFunctionPlugin {
    String getName();

    Object apply(List<Object> arguments);
}
