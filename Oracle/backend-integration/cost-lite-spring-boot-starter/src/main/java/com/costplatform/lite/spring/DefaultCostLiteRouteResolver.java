package com.costplatform.lite.spring;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认参考运行时路由适配器。
 *
 * <p>这里是唯一集中保存参考运行时 URL 的位置。参考运行时沿用母体计费接口契约，
 * Starter 的稳定协议只依赖路由键。第三方项目可以通过 {@code upstream-paths}
 * 覆盖单个路径，或直接提供自己的 {@link CostLiteRouteResolver} Bean。</p>
 */
public class DefaultCostLiteRouteResolver implements CostLiteRouteResolver {
    private final Map<String, String> paths;

    public DefaultCostLiteRouteResolver(CostLiteSpringProperties properties) {
        LinkedHashMap<String, String> configured = new LinkedHashMap<>(defaultPaths());
        if (properties != null && properties.getUpstreamPaths() != null) {
            configured.putAll(properties.getUpstreamPaths());
        }
        this.paths = Collections.unmodifiableMap(configured);
    }

    @Override
    public String resolve(String routeKey, Map<String, ?> variables) {
        if (routeKey == null || routeKey.trim().isEmpty()) {
            throw new IllegalArgumentException("轻量计费路由键不能为空");
        }
        String template = paths.get(routeKey);
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("未配置轻量计费路由：" + routeKey);
        }

        String path = template.trim();
        if (variables != null) {
            for (Map.Entry<String, ?> entry : variables.entrySet()) {
                String name = entry.getKey();
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                String placeholder = "{" + name + "}";
                path = path.replace(placeholder, encodePathSegment(entry.getValue()));
            }
        }
        if (path.indexOf('{') >= 0 || path.indexOf('}') >= 0) {
            throw new IllegalArgumentException("轻量计费路由缺少路径变量：" + routeKey);
        }
        validateRelativePath(path, routeKey);
        return path;
    }

    private Map<String, String> defaultPaths() {
        LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
        put(defaults, CostLiteRouteKeys.HEALTH, "/cost/lite/health");
        put(defaults, CostLiteRouteKeys.BOOTSTRAP, "/cost/lite/bootstrap");
        put(defaults, CostLiteRouteKeys.DICTIONARY_OPTIONS, "/cost/dictionary/options");

        put(defaults, CostLiteRouteKeys.SCENE_LIST, "/cost/scene/list");
        put(defaults, CostLiteRouteKeys.SCENE_STATS, "/cost/scene/stats");
        put(defaults, CostLiteRouteKeys.SCENE_OPTIONS, "/cost/scene/optionselect");
        put(defaults, CostLiteRouteKeys.SCENE_DETAIL, "/cost/scene/{sceneId}");
        put(defaults, CostLiteRouteKeys.SCENE_GOVERNANCE, "/cost/scene/governance/{sceneId}");
        put(defaults, CostLiteRouteKeys.SCENE_CREATE, "/cost/scene");
        put(defaults, CostLiteRouteKeys.SCENE_UPDATE, "/cost/scene");
        put(defaults, CostLiteRouteKeys.SCENE_COPY, "/cost/scene/copy");
        put(defaults, CostLiteRouteKeys.SCENE_DELETE, "/cost/scene/{sceneIds}");

        put(defaults, CostLiteRouteKeys.FEE_LIST, "/cost/fee/list");
        put(defaults, CostLiteRouteKeys.FEE_STATS, "/cost/fee/stats");
        put(defaults, CostLiteRouteKeys.FEE_OPTIONS, "/cost/fee/optionselect");
        put(defaults, CostLiteRouteKeys.FEE_DETAIL, "/cost/fee/{feeId}");
        put(defaults, CostLiteRouteKeys.FEE_GOVERNANCE, "/cost/fee/governance/{feeId}");
        put(defaults, CostLiteRouteKeys.FEE_CREATE, "/cost/fee");
        put(defaults, CostLiteRouteKeys.FEE_UPDATE, "/cost/fee");
        put(defaults, CostLiteRouteKeys.FEE_DISABLE, "/cost/fee/disable/{feeIds}");
        put(defaults, CostLiteRouteKeys.FEE_DELETE, "/cost/fee/{feeIds}");

        put(defaults, CostLiteRouteKeys.VARIABLE_LIST, "/cost/variable/list");
        put(defaults, CostLiteRouteKeys.VARIABLE_STATS, "/cost/variable/stats");
        put(defaults, CostLiteRouteKeys.VARIABLE_OPTIONS, "/cost/variable/optionselect");
        put(defaults, CostLiteRouteKeys.VARIABLE_DETAIL, "/cost/variable/{variableId}");
        put(defaults, CostLiteRouteKeys.VARIABLE_GOVERNANCE, "/cost/variable/governance/{variableId}");
        put(defaults, CostLiteRouteKeys.VARIABLE_CREATE, "/cost/variable");
        put(defaults, CostLiteRouteKeys.VARIABLE_UPDATE, "/cost/variable");
        put(defaults, CostLiteRouteKeys.VARIABLE_COPY, "/cost/variable/copy");
        put(defaults, CostLiteRouteKeys.VARIABLE_IMPORT_PREVIEW, "/cost/variable/importPreview");
        put(defaults, CostLiteRouteKeys.VARIABLE_IMPORT_DATA, "/cost/variable/importData");
        put(defaults, CostLiteRouteKeys.VARIABLE_DELETE, "/cost/variable/{variableIds}");

        put(defaults, CostLiteRouteKeys.VARIABLE_GROUP_LIST, "/cost/variable/group/list");
        put(defaults, CostLiteRouteKeys.VARIABLE_GROUP_OPTIONS, "/cost/variable/group/optionselect");
        put(defaults, CostLiteRouteKeys.VARIABLE_GROUP_DETAIL, "/cost/variable/group/{groupId}");
        put(defaults, CostLiteRouteKeys.VARIABLE_GROUP_CREATE, "/cost/variable/group");
        put(defaults, CostLiteRouteKeys.VARIABLE_GROUP_UPDATE, "/cost/variable/group");
        put(defaults, CostLiteRouteKeys.VARIABLE_GROUP_DELETE, "/cost/variable/group/{groupIds}");

        put(defaults, CostLiteRouteKeys.RULE_LIST, "/cost/rule/list");
        put(defaults, CostLiteRouteKeys.RULE_STATS, "/cost/rule/stats");
        put(defaults, CostLiteRouteKeys.RULE_GOVERNANCE, "/cost/rule/governance/{ruleId}");
        put(defaults, CostLiteRouteKeys.RULE_DETAIL, "/cost/rule/{ruleId}");
        put(defaults, CostLiteRouteKeys.RULE_CREATE, "/cost/rule");
        put(defaults, CostLiteRouteKeys.RULE_UPDATE, "/cost/rule");
        put(defaults, CostLiteRouteKeys.RULE_COPY, "/cost/rule/copy");
        put(defaults, CostLiteRouteKeys.RULE_TIER_PREVIEW, "/cost/rule/tierPreview");
        put(defaults, CostLiteRouteKeys.RULE_CONFLICT_PREVIEW, "/cost/rule/conflictPreview");
        put(defaults, CostLiteRouteKeys.RULE_DELETE, "/cost/rule/{ruleIds}");

        put(defaults, CostLiteRouteKeys.FORMULA_LIST, "/cost/formula/list");
        put(defaults, CostLiteRouteKeys.FORMULA_STATS, "/cost/formula/stats");
        put(defaults, CostLiteRouteKeys.FORMULA_GOVERNANCE, "/cost/formula/governance/{formulaId}");
        put(defaults, CostLiteRouteKeys.FORMULA_OPTIONS, "/cost/formula/optionselect");
        put(defaults, CostLiteRouteKeys.FORMULA_TEMPLATE_OPTIONS, "/cost/formula/templateOptions");
        put(defaults, CostLiteRouteKeys.FORMULA_DETAIL, "/cost/formula/{formulaId}");
        put(defaults, CostLiteRouteKeys.FORMULA_CREATE, "/cost/formula");
        put(defaults, CostLiteRouteKeys.FORMULA_UPDATE, "/cost/formula");
        put(defaults, CostLiteRouteKeys.FORMULA_VERSIONS, "/cost/formula/versions/{formulaId}");
        put(defaults, CostLiteRouteKeys.FORMULA_VERSION_DETAIL, "/cost/formula/version/{versionId}");
        put(defaults, CostLiteRouteKeys.FORMULA_VERSION_ROLLBACK, "/cost/formula/version/rollback/{versionId}");
        put(defaults, CostLiteRouteKeys.FORMULA_TEST, "/cost/formula/test");
        put(defaults, CostLiteRouteKeys.FORMULA_DELETE, "/cost/formula/{formulaIds}");

        put(defaults, CostLiteRouteKeys.PUBLISH_STATS, "/cost/publish/stats");
        put(defaults, CostLiteRouteKeys.PUBLISH_LIST, "/cost/publish/list");
        put(defaults, CostLiteRouteKeys.PUBLISH_PRECHECK, "/cost/publish/precheck/{sceneId}");
        put(defaults, CostLiteRouteKeys.PUBLISH_DIFF, "/cost/publish/diff");
        put(defaults, CostLiteRouteKeys.PUBLISH_DETAIL, "/cost/publish/{versionId}");
        put(defaults, CostLiteRouteKeys.PUBLISH_CREATE, "/cost/publish");
        put(defaults, CostLiteRouteKeys.PUBLISH_ACTIVATE, "/cost/publish/activate/{versionId}");
        put(defaults, CostLiteRouteKeys.PUBLISH_ROLLBACK, "/cost/publish/rollback/{versionId}");

        put(defaults, CostLiteRouteKeys.SIMULATION_STATS, "/cost/run/simulation/stats");
        put(defaults, CostLiteRouteKeys.SIMULATION_LIST, "/cost/run/simulation/list");
        put(defaults, CostLiteRouteKeys.SIMULATION_EXECUTE, "/cost/run/simulation/execute");
        put(defaults, CostLiteRouteKeys.SIMULATION_BATCH_EXECUTE, "/cost/run/simulation/batch-execute");
        put(defaults, CostLiteRouteKeys.SIMULATION_DETAIL, "/cost/run/simulation/{simulationId}");
        put(defaults, CostLiteRouteKeys.INPUT_BUILD_PREVIEW, "/cost/run/input-build/preview");
        put(defaults, CostLiteRouteKeys.TASK_STATS, "/cost/run/task/stats");
        put(defaults, CostLiteRouteKeys.TASK_OVERVIEW, "/cost/run/task/overview");
        put(defaults, CostLiteRouteKeys.TASK_LIST, "/cost/run/task/list");
        put(defaults, CostLiteRouteKeys.TASK_PRECHECK, "/cost/run/task/precheck");
        put(defaults, CostLiteRouteKeys.TASK_SUBMIT, "/cost/run/task/submit");
        put(defaults, CostLiteRouteKeys.TASK_INPUT_BATCH_CREATE, "/cost/run/task/input-batch");
        put(defaults, CostLiteRouteKeys.TASK_INPUT_BATCH_LIST, "/cost/run/task/input-batch/list");
        put(defaults, CostLiteRouteKeys.TASK_INPUT_BATCH_DETAIL, "/cost/run/task/input-batch/{batchId}");
        put(defaults, CostLiteRouteKeys.TASK_DETAIL, "/cost/run/task/{taskId}");
        put(defaults, CostLiteRouteKeys.TASK_DETAIL_RETRY, "/cost/run/task/retry/{detailId}");
        put(defaults, CostLiteRouteKeys.TASK_PARTITION_RETRY, "/cost/run/task/partition/retry/{partitionId}");
        put(defaults, CostLiteRouteKeys.TASK_CANCEL, "/cost/run/task/cancel/{taskId}");
        put(defaults, CostLiteRouteKeys.RESULT_STATS, "/cost/run/result/stats");
        put(defaults, CostLiteRouteKeys.RESULT_COMPARE, "/cost/run/result/compare");
        put(defaults, CostLiteRouteKeys.RESULT_LIST, "/cost/run/result/list");
        put(defaults, CostLiteRouteKeys.RESULT_DETAIL, "/cost/run/result/{resultId}");
        put(defaults, CostLiteRouteKeys.TRACE_DETAIL, "/cost/run/trace/{traceId}");
        put(defaults, CostLiteRouteKeys.VERSION_OPTIONS, "/cost/run/version-options/{sceneId}");
        put(defaults, CostLiteRouteKeys.INPUT_TEMPLATE, "/cost/run/input-template");
        put(defaults, CostLiteRouteKeys.FEE_INPUT_TEMPLATE, "/cost/run/input-template/fee");
        put(defaults, CostLiteRouteKeys.FEE_CALCULATE, "/cost/run/fee/calculate");
        put(defaults, CostLiteRouteKeys.BILLING_LOG_STATS, "/cost/lite/billing-log/stats");
        put(defaults, CostLiteRouteKeys.BILLING_LOG_LIST, "/cost/lite/billing-log/list");
        put(defaults, CostLiteRouteKeys.BILLING_LOG_DETAIL, "/cost/lite/billing-log/{simulationId}");

        put(defaults, CostLiteRouteKeys.OPEN_AUTH_TOKEN, "/cost/open/auth/token");
        put(defaults, CostLiteRouteKeys.OPEN_SCENES, "/cost/open/scenes");
        put(defaults, CostLiteRouteKeys.OPEN_SCENE_VERSIONS, "/cost/open/scenes/{sceneId}/versions");
        put(defaults, CostLiteRouteKeys.OPEN_SCENE_FEES, "/cost/open/scenes/{sceneId}/fees");
        put(defaults, CostLiteRouteKeys.OPEN_TEMPLATE, "/cost/open/fee-template");
        put(defaults, CostLiteRouteKeys.OPEN_CALCULATE, "/cost/open/fee/calculate");

        put(defaults, CostLiteRouteKeys.OPEN_APP_LIST, "/cost/openApp/list");
        put(defaults, CostLiteRouteKeys.OPEN_APP_SCENE_OPTIONS, "/cost/openApp/sceneOptions");
        put(defaults, CostLiteRouteKeys.OPEN_APP_DETAIL, "/cost/openApp/{appId}");
        put(defaults, CostLiteRouteKeys.OPEN_APP_CREATE, "/cost/openApp");
        put(defaults, CostLiteRouteKeys.OPEN_APP_UPDATE, "/cost/openApp");
        put(defaults, CostLiteRouteKeys.OPEN_APP_RESET_SECRET, "/cost/openApp/resetSecret/{appId}");
        put(defaults, CostLiteRouteKeys.OPEN_APP_DELETE, "/cost/openApp/{appIds}");
        return defaults;
    }

    private void put(Map<String, String> defaults, String key, String path) {
        defaults.put(key, path);
    }

    private String encodePathSegment(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("轻量计费路由变量不能为空");
        }
        try {
            return URLEncoder.encode(String.valueOf(value), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("轻量计费路径变量编码失败", exception);
        }
    }

    private void validateRelativePath(String path, String routeKey) {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")
                || path.indexOf('?') >= 0 || path.indexOf('#') >= 0) {
            throw new IllegalArgumentException("轻量计费路由必须是相对路径：" + routeKey);
        }
    }
}
