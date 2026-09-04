package com.ruoyi.lite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量宿主配置。
 */
@ConfigurationProperties(prefix = "cost.lite")
public class CostLiteProperties {
    /** 是否启用管理接口令牌校验。默认关闭，便于首次部署当天跑通。 */
    private boolean authEnabled;

    /** 管理接口令牌，启用校验后必须配置。 */
    private String adminToken;

    /** 写入审计和调用日志的操作人标识。 */
    private String operator = "lite-admin";

    /** 单次列表返回最大记录数。 */
    private int maxPageSize = 200;

    /** 是否保存同步计费调用的请求和结果。 */
    private boolean persistBillingLog = true;

    /** 外部插件目录，默认位于 JAR 同级的 plugins 目录。 */
    private String pluginDir = "plugins";

    /** 是否加载外部插件。 */
    private boolean pluginEnabled = true;

    /** 嵌入式运行开关；Starter 默认在宿主进程内启用，独立服务显式关闭。 */
    private Embedded embedded = new Embedded();

    /** 计费字典适配配置。 */
    private Dictionary dictionary = new Dictionary();

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public boolean isPersistBillingLog() {
        return persistBillingLog;
    }

    public void setPersistBillingLog(boolean persistBillingLog) {
        this.persistBillingLog = persistBillingLog;
    }

    public String getPluginDir() {
        return pluginDir;
    }

    public void setPluginDir(String pluginDir) {
        this.pluginDir = pluginDir;
    }

    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    public void setPluginEnabled(boolean pluginEnabled) {
        this.pluginEnabled = pluginEnabled;
    }

    public Embedded getEmbedded() {
        return embedded;
    }

    public void setEmbedded(Embedded embedded) {
        this.embedded = embedded == null ? new Embedded() : embedded;
    }

    public Dictionary getDictionary() {
        return dictionary;
    }

    public void setDictionary(Dictionary dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * 控制是否将计费能力注册到当前宿主 Spring 容器。
     */
    public static class Embedded {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 独立运行时字典配置。
     */
    public static class Dictionary {
        /** CONFIG 使用下方 values；SYSTEM 复用宿主系统字典；CUSTOM 由宿主提供 Bean。 */
        private String provider = "SYSTEM";

        /** 是否校验已配置字典类型的取值。 */
        private boolean validationEnabled = true;

        /** 未配置的字典类型是否放行，便于业务系统逐步接入自有字典。 */
        private boolean allowUnconfiguredTypes = true;

        /** 计费统一字典类型与允许值。 */
        private Map<String, List<String>> values = defaultValues();

        /** 计费统一字典类型到宿主字典类型的映射，仅在名称不一致时配置。 */
        private Map<String, String> typeMappings = new LinkedHashMap<>();

        /** 计费统一字典值到宿主字典值的映射，第一层键始终使用计费统一字典类型。 */
        private Map<String, Map<String, String>> valueMappings = new LinkedHashMap<>();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public boolean isValidationEnabled() {
            return validationEnabled;
        }

        public void setValidationEnabled(boolean validationEnabled) {
            this.validationEnabled = validationEnabled;
        }

        public boolean isAllowUnconfiguredTypes() {
            return allowUnconfiguredTypes;
        }

        public void setAllowUnconfiguredTypes(boolean allowUnconfiguredTypes) {
            this.allowUnconfiguredTypes = allowUnconfiguredTypes;
        }

        public Map<String, List<String>> getValues() {
            return values;
        }

        public void setValues(Map<String, List<String>> values) {
            this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        }

        public Map<String, String> getTypeMappings() {
            return typeMappings;
        }

        public void setTypeMappings(Map<String, String> typeMappings) {
            this.typeMappings = typeMappings == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(typeMappings);
        }

        public Map<String, Map<String, String>> getValueMappings() {
            return valueMappings;
        }

        public void setValueMappings(Map<String, Map<String, String>> valueMappings) {
            this.valueMappings = new LinkedHashMap<>();
            if (valueMappings == null) {
                return;
            }
            valueMappings.forEach((dictType, mappings) -> this.valueMappings.put(
                    dictType,
                    mappings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(mappings)));
        }

        /**
         * 把计费统一字典类型解析为当前宿主使用的类型编码。
         *
         * @param dictType 计费统一字典类型
         * @return 宿主字典类型；未配置映射时原样返回
         */
        public String resolveType(String dictType) {
            String mapped = typeMappings.get(dictType);
            return hasText(mapped) ? mapped.trim() : dictType;
        }

        /**
         * 把计费统一字典值解析为当前宿主使用的值。
         *
         * @param dictType  计费统一字典类型
         * @param dictValue 计费统一字典值
         * @return 宿主字典值；未配置映射时原样返回
         */
        public String resolveValue(String dictType, String dictValue) {
            Map<String, String> mappings = valueMappings.get(dictType);
            if (mappings == null) {
                return dictValue;
            }
            String mapped = mappings.get(dictValue);
            return hasText(mapped) ? mapped.trim() : dictValue;
        }

        /**
         * 查找配置型字典允许值。配置既可继续使用统一类型，也可直接使用映射后的宿主类型。
         */
        public List<String> findConfiguredValues(String dictType) {
            String resolvedType = resolveType(dictType);
            List<String> configured = values.get(resolvedType);
            if (configured == null && !resolvedType.equals(dictType)) {
                configured = values.get(dictType);
            }
            return configured;
        }

        private static boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }

        private static Map<String, List<String>> defaultValues() {
            Map<String, List<String>> defaults = new LinkedHashMap<>();
            defaults.put("cost_business_domain", java.util.Arrays.asList("SALARY", "PORT", "STORAGE", "TRANSPORT", "MATERIAL", "MANUFACTURE"));
            defaults.put("cost_scene_status", java.util.Arrays.asList("0", "1", "2"));
            defaults.put("cost_scene_type", java.util.Arrays.asList("CONTRACT", "THEME", "PLAN", "COMPANY"));
            defaults.put("cost_fee_status", java.util.Arrays.asList("0", "1"));
            defaults.put("cost_unit_code", java.util.Arrays.asList("吨", "天", "次", "航次", "人", "箱", "元", "平方米*天"));
            defaults.put("cost_variable_type", java.util.Arrays.asList("TEXT", "NUMBER", "DICT", "REMOTE", "FORMULA", "BOOLEAN", "DATE"));
            defaults.put("cost_variable_source_type", java.util.Arrays.asList("INPUT", "DICT", "REMOTE", "FORMULA"));
            defaults.put("cost_variable_data_type", java.util.Arrays.asList("STRING", "NUMBER", "BOOLEAN", "DATE", "JSON"));
            defaults.put("cost_variable_status", java.util.Arrays.asList("0", "1"));
            defaults.put("cost_variable_auth_type", java.util.Arrays.asList("NONE", "BASIC", "BEARER", "API_KEY"));
            defaults.put("cost_variable_sync_mode", java.util.Arrays.asList("REALTIME", "NEAR_REALTIME", "SCHEDULED"));
            defaults.put("cost_variable_cache_policy", java.util.Arrays.asList("NONE", "TTL", "MANUAL_REFRESH"));
            defaults.put("cost_variable_fallback_policy", java.util.Arrays.asList("FAIL_FAST", "DEFAULT_VALUE", "LAST_SNAPSHOT"));
            defaults.put("cost_formula_status", java.util.Arrays.asList("0", "1"));
            defaults.put("cost_formula_return_type", java.util.Arrays.asList("NUMBER", "BOOLEAN", "STRING", "JSON"));
            return defaults;
        }
    }
}
