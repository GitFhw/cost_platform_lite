package com.ruoyi.lite.plugin;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件注册表。首版采用“启动时加载，重启生效”，保持实现简单可控。
 */
@Component
public class CostLitePluginRegistry {
    private final Map<String, InputAdapterPlugin> inputAdapters = new ConcurrentHashMap<>();
    private final Map<String, ResultAdapterPlugin> resultAdapters = new ConcurrentHashMap<>();
    private final Map<String, FormulaFunctionPlugin> formulaFunctions = new ConcurrentHashMap<>();
    private final Map<String, CostLitePlugin> plugins = new ConcurrentHashMap<>();

    public void register(CostLitePlugin plugin) {
        if (plugin != null && plugin.getCode() != null && !plugin.getCode().trim().isEmpty()) {
            plugins.put(plugin.getCode(), plugin);
        }
    }

    public void register(InputAdapterPlugin plugin) {
        if (plugin != null && plugin.getCode() != null && !plugin.getCode().trim().isEmpty()) {
            inputAdapters.put(plugin.getCode(), plugin);
        }
    }

    public void register(ResultAdapterPlugin plugin) {
        if (plugin != null && plugin.getCode() != null && !plugin.getCode().trim().isEmpty()) {
            resultAdapters.put(plugin.getCode(), plugin);
        }
    }

    public void register(FormulaFunctionPlugin plugin) {
        if (plugin != null && plugin.getName() != null && !plugin.getName().trim().isEmpty()) {
            formulaFunctions.put(plugin.getName(), plugin);
        }
    }

    public InputAdapterPlugin getInputAdapter(String code) {
        return inputAdapters.get(code);
    }

    public ResultAdapterPlugin getResultAdapter(String code) {
        return resultAdapters.get(code);
    }

    public FormulaFunctionPlugin getFormulaFunction(String name) {
        return formulaFunctions.get(name);
    }

    public Collection<CostLitePlugin> getPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    public Collection<InputAdapterPlugin> getInputAdapters() {
        return Collections.unmodifiableCollection(inputAdapters.values());
    }

    public Collection<ResultAdapterPlugin> getResultAdapters() {
        return Collections.unmodifiableCollection(resultAdapters.values());
    }

    public Collection<FormulaFunctionPlugin> getFormulaFunctions() {
        return Collections.unmodifiableCollection(formulaFunctions.values());
    }
}
