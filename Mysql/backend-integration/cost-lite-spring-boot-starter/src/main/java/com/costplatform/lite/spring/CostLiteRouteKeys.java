package com.costplatform.lite.spring;

/**
 * 轻量计费语义路由键。
 *
 * <p>路由键是集成协议的一部分，具体 URL 由宿主适配器决定。默认适配器
 * 使用母体平台路径，但第三方系统可以通过配置或自定义 {@link CostLiteRouteResolver}
 * 替换这些路径。</p>
 */
public final class CostLiteRouteKeys {
    public static final String HEALTH = "health";
    public static final String BOOTSTRAP = "bootstrap";
    public static final String DICTIONARY_OPTIONS = "dictionary-options";

    public static final String SCENE_LIST = "scene-list";
    public static final String SCENE_STATS = "scene-stats";
    public static final String SCENE_OPTIONS = "scene-options";
    public static final String SCENE_DETAIL = "scene-detail";
    public static final String SCENE_GOVERNANCE = "scene-governance";
    public static final String SCENE_CREATE = "scene-create";
    public static final String SCENE_UPDATE = "scene-update";
    public static final String SCENE_COPY = "scene-copy";
    public static final String SCENE_DELETE = "scene-delete";

    public static final String FEE_LIST = "fee-list";
    public static final String FEE_STATS = "fee-stats";
    public static final String FEE_OPTIONS = "fee-options";
    public static final String FEE_DETAIL = "fee-detail";
    public static final String FEE_GOVERNANCE = "fee-governance";
    public static final String FEE_CREATE = "fee-create";
    public static final String FEE_UPDATE = "fee-update";
    public static final String FEE_DISABLE = "fee-disable";
    public static final String FEE_DELETE = "fee-delete";

    public static final String VARIABLE_LIST = "variable-list";
    public static final String VARIABLE_STATS = "variable-stats";
    public static final String VARIABLE_OPTIONS = "variable-options";
    public static final String VARIABLE_DETAIL = "variable-detail";
    public static final String VARIABLE_GOVERNANCE = "variable-governance";
    public static final String VARIABLE_CREATE = "variable-create";
    public static final String VARIABLE_UPDATE = "variable-update";
    public static final String VARIABLE_COPY = "variable-copy";
    public static final String VARIABLE_IMPORT_PREVIEW = "variable-import-preview";
    public static final String VARIABLE_IMPORT_DATA = "variable-import-data";
    public static final String VARIABLE_DELETE = "variable-delete";

    public static final String VARIABLE_GROUP_LIST = "variable-group-list";
    public static final String VARIABLE_GROUP_OPTIONS = "variable-group-options";
    public static final String VARIABLE_GROUP_DETAIL = "variable-group-detail";
    public static final String VARIABLE_GROUP_CREATE = "variable-group-create";
    public static final String VARIABLE_GROUP_UPDATE = "variable-group-update";
    public static final String VARIABLE_GROUP_DELETE = "variable-group-delete";

    public static final String RULE_LIST = "rule-list";
    public static final String RULE_STATS = "rule-stats";
    public static final String RULE_GOVERNANCE = "rule-governance";
    public static final String RULE_DETAIL = "rule-detail";
    public static final String RULE_CREATE = "rule-create";
    public static final String RULE_UPDATE = "rule-update";
    public static final String RULE_COPY = "rule-copy";
    public static final String RULE_TIER_PREVIEW = "rule-tier-preview";
    public static final String RULE_CONFLICT_PREVIEW = "rule-conflict-preview";
    public static final String RULE_DELETE = "rule-delete";

    public static final String FORMULA_LIST = "formula-list";
    public static final String FORMULA_STATS = "formula-stats";
    public static final String FORMULA_GOVERNANCE = "formula-governance";
    public static final String FORMULA_OPTIONS = "formula-options";
    public static final String FORMULA_TEMPLATE_OPTIONS = "formula-template-options";
    public static final String FORMULA_DETAIL = "formula-detail";
    public static final String FORMULA_CREATE = "formula-create";
    public static final String FORMULA_UPDATE = "formula-update";
    public static final String FORMULA_VERSIONS = "formula-versions";
    public static final String FORMULA_VERSION_DETAIL = "formula-version-detail";
    public static final String FORMULA_VERSION_ROLLBACK = "formula-version-rollback";
    public static final String FORMULA_TEST = "formula-test";
    public static final String FORMULA_DELETE = "formula-delete";

    public static final String PUBLISH_STATS = "publish-stats";
    public static final String PUBLISH_LIST = "publish-list";
    public static final String PUBLISH_PRECHECK = "publish-precheck";
    public static final String PUBLISH_DIFF = "publish-diff";
    public static final String PUBLISH_DETAIL = "publish-detail";
    public static final String PUBLISH_CREATE = "publish-create";
    public static final String PUBLISH_ACTIVATE = "publish-activate";
    public static final String PUBLISH_ROLLBACK = "publish-rollback";

    public static final String SIMULATION_STATS = "simulation-stats";
    public static final String SIMULATION_LIST = "simulation-list";
    public static final String SIMULATION_EXECUTE = "simulation-execute";
    public static final String SIMULATION_BATCH_EXECUTE = "simulation-batch-execute";
    public static final String SIMULATION_DETAIL = "simulation-detail";
    public static final String INPUT_BUILD_PREVIEW = "input-build-preview";
    public static final String TASK_STATS = "task-stats";
    public static final String TASK_OVERVIEW = "task-overview";
    public static final String TASK_LIST = "task-list";
    public static final String TASK_PRECHECK = "task-precheck";
    public static final String TASK_SUBMIT = "task-submit";
    public static final String TASK_INPUT_BATCH_CREATE = "task-input-batch-create";
    public static final String TASK_INPUT_BATCH_LIST = "task-input-batch-list";
    public static final String TASK_INPUT_BATCH_DETAIL = "task-input-batch-detail";
    public static final String TASK_DETAIL = "task-detail";
    public static final String TASK_DETAIL_RETRY = "task-detail-retry";
    public static final String TASK_PARTITION_RETRY = "task-partition-retry";
    public static final String TASK_CANCEL = "task-cancel";
    public static final String RESULT_STATS = "result-stats";
    public static final String RESULT_COMPARE = "result-compare";
    public static final String RESULT_LIST = "result-list";
    public static final String RESULT_DETAIL = "result-detail";
    public static final String TRACE_DETAIL = "trace-detail";
    public static final String VERSION_OPTIONS = "version-options";
    public static final String INPUT_TEMPLATE = "input-template";
    public static final String FEE_INPUT_TEMPLATE = "fee-input-template";
    public static final String FEE_CALCULATE = "fee-calculate";
    public static final String BILLING_LOG_STATS = "billing-log-stats";
    public static final String BILLING_LOG_LIST = "billing-log-list";
    public static final String BILLING_LOG_DETAIL = "billing-log-detail";

    public static final String OPEN_AUTH_TOKEN = "open-auth-token";
    public static final String OPEN_SCENES = "open-scenes";
    public static final String OPEN_SCENE_VERSIONS = "open-scene-versions";
    public static final String OPEN_SCENE_FEES = "open-scene-fees";
    public static final String OPEN_TEMPLATE = "open-template";
    public static final String OPEN_CALCULATE = "open-calculate";

    public static final String OPEN_APP_LIST = "open-app-list";
    public static final String OPEN_APP_SCENE_OPTIONS = "open-app-scene-options";
    public static final String OPEN_APP_DETAIL = "open-app-detail";
    public static final String OPEN_APP_CREATE = "open-app-create";
    public static final String OPEN_APP_UPDATE = "open-app-update";
    public static final String OPEN_APP_RESET_SECRET = "open-app-reset-secret";
    public static final String OPEN_APP_DELETE = "open-app-delete";

    private CostLiteRouteKeys() {
    }
}
