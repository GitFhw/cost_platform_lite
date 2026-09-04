package com.costplatform.lite.spring;

import com.costplatform.lite.client.CostLiteAuth;
import com.costplatform.lite.client.CostLiteClient;
import com.costplatform.lite.client.CostLiteRequest;
import com.costplatform.lite.client.CostLiteResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可选的宿主侧代理控制器。它只负责稳定的集成路径和响应归一，不承载计费业务逻辑。
 *
 * <p>控制器中的路径是稳定的集成协议路径；真正的上游 URL 通过
 * {@link CostLiteRouteResolver} 解析，因此不会把某个母体框架的 URL 写死在协议层。</p>
 */
@RestController
@RequestMapping("${cost.lite.integration.web-path:/cost}")
public class CostLiteProxyController {
    private final CostLiteClient client;
    private final CostLiteProxyResponseFactory responseFactory;
    private final CostLiteRouteResolver routeResolver;

    public CostLiteProxyController(CostLiteClient client,
                                   CostLiteProxyResponseFactory responseFactory,
                                   CostLiteRouteResolver routeResolver) {
        this.client = client;
        this.responseFactory = responseFactory;
        this.routeResolver = routeResolver;
    }

    @GetMapping("/health")
    public Object health(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.HEALTH, query, CostLiteAuth.NONE);
    }

    @GetMapping("/bootstrap")
    public Object bootstrap(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.BOOTSTRAP, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/dictionary/options")
    public Object dictionaryOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.DICTIONARY_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/scenes")
    public Object scenes(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.SCENE_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/scenes/stats")
    public Object sceneStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.SCENE_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/scenes/options")
    public Object sceneOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.SCENE_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/scenes/{sceneId}")
    public Object scene(@PathVariable Long sceneId,
                        @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.SCENE_DETAIL, query, CostLiteAuth.MANAGEMENT,
                "sceneId", sceneId);
    }

    @GetMapping("/scenes/{sceneId}/governance")
    public Object sceneGovernance(@PathVariable Long sceneId) {
        return get(CostLiteRouteKeys.SCENE_GOVERNANCE, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "sceneId", sceneId);
    }

    @GetMapping("/scenes/{sceneId}/fees")
    public Object sceneFees(@PathVariable Long sceneId,
                            @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FEE_LIST, with(query, "sceneId", sceneId), CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/scenes/{sceneId}/variables")
    public Object sceneVariables(@PathVariable Long sceneId,
                                 @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VARIABLE_LIST, with(query, "sceneId", sceneId), CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/scenes/{sceneId}/versions")
    public Object sceneVersions(@PathVariable Long sceneId,
                                @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VERSION_OPTIONS, query, CostLiteAuth.MANAGEMENT,
                "sceneId", sceneId);
    }

    @PostMapping("/scenes")
    public Object createScene(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.SCENE_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/scenes/copy")
    public Object copyScene(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.SCENE_COPY, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/scenes")
    public Object updateScene(@RequestBody(required = false) Object body) {
        return put(CostLiteRouteKeys.SCENE_UPDATE, body, CostLiteAuth.MANAGEMENT);
    }

    @DeleteMapping("/scenes/{sceneIds}")
    public Object deleteScenes(@PathVariable String sceneIds) {
        return delete(CostLiteRouteKeys.SCENE_DELETE, CostLiteAuth.MANAGEMENT,
                "sceneIds", sceneIds);
    }

    @GetMapping("/fees")
    public Object fees(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FEE_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/fees/stats")
    public Object feeStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FEE_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/fees/options")
    public Object feeOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FEE_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/fees/{feeId}")
    public Object fee(@PathVariable Long feeId,
                      @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FEE_DETAIL, query, CostLiteAuth.MANAGEMENT,
                "feeId", feeId);
    }

    @GetMapping("/fees/{feeId}/governance")
    public Object feeGovernance(@PathVariable Long feeId) {
        return get(CostLiteRouteKeys.FEE_GOVERNANCE, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "feeId", feeId);
    }

    @PostMapping("/fees")
    public Object createFee(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.FEE_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/fees")
    public Object updateFee(@RequestBody(required = false) Object body) {
        return put(CostLiteRouteKeys.FEE_UPDATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/fees/{feeIds}/disable")
    public Object disableFees(@PathVariable String feeIds) {
        return put(CostLiteRouteKeys.FEE_DISABLE, null, CostLiteAuth.MANAGEMENT,
                "feeIds", feeIds);
    }

    @DeleteMapping("/fees/{feeIds}")
    public Object deleteFees(@PathVariable String feeIds) {
        return delete(CostLiteRouteKeys.FEE_DELETE, CostLiteAuth.MANAGEMENT,
                "feeIds", feeIds);
    }

    @GetMapping("/variables")
    public Object variables(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VARIABLE_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/variables/stats")
    public Object variableStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VARIABLE_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/variables/options")
    public Object variableOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VARIABLE_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/variables/{variableId}")
    public Object variable(@PathVariable Long variableId,
                           @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VARIABLE_DETAIL, query, CostLiteAuth.MANAGEMENT,
                "variableId", variableId);
    }

    @GetMapping("/variables/{variableId}/governance")
    public Object variableGovernance(@PathVariable Long variableId) {
        return get(CostLiteRouteKeys.VARIABLE_GOVERNANCE, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "variableId", variableId);
    }

    @PostMapping("/variables")
    public Object createVariable(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.VARIABLE_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/variables")
    public Object updateVariable(@RequestBody(required = false) Object body) {
        return put(CostLiteRouteKeys.VARIABLE_UPDATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/variables/copy")
    public Object copyVariable(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.VARIABLE_COPY, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/variables/import-preview")
    public Object previewVariableImport(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.VARIABLE_IMPORT_PREVIEW, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/variables/import-data")
    public Object importVariables(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.VARIABLE_IMPORT_DATA, body, CostLiteAuth.MANAGEMENT);
    }

    @DeleteMapping("/variables/{variableIds}")
    public Object deleteVariables(@PathVariable String variableIds) {
        return delete(CostLiteRouteKeys.VARIABLE_DELETE, CostLiteAuth.MANAGEMENT,
                "variableIds", variableIds);
    }

    @GetMapping("/variable-groups")
    public Object variableGroups(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VARIABLE_GROUP_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/variable-groups/options")
    public Object variableGroupOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.VARIABLE_GROUP_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/variable-groups/{groupId}")
    public Object variableGroup(@PathVariable Long groupId) {
        return get(CostLiteRouteKeys.VARIABLE_GROUP_DETAIL,
                Collections.<String, Object>emptyMap(), CostLiteAuth.MANAGEMENT,
                "groupId", groupId);
    }

    @PostMapping("/variable-groups")
    public Object createVariableGroup(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.VARIABLE_GROUP_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/variable-groups")
    public Object updateVariableGroup(@RequestBody(required = false) Object body) {
        return put(CostLiteRouteKeys.VARIABLE_GROUP_UPDATE, body, CostLiteAuth.MANAGEMENT);
    }

    @DeleteMapping("/variable-groups/{groupIds}")
    public Object deleteVariableGroups(@PathVariable String groupIds) {
        return delete(CostLiteRouteKeys.VARIABLE_GROUP_DELETE, CostLiteAuth.MANAGEMENT,
                "groupIds", groupIds);
    }

    @GetMapping("/rules")
    public Object rules(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.RULE_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/rules/stats")
    public Object ruleStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.RULE_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/rules/{ruleId}")
    public Object rule(@PathVariable Long ruleId) {
        return get(CostLiteRouteKeys.RULE_DETAIL, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "ruleId", ruleId);
    }

    @GetMapping("/rules/{ruleId}/governance")
    public Object ruleGovernance(@PathVariable Long ruleId) {
        return get(CostLiteRouteKeys.RULE_GOVERNANCE, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "ruleId", ruleId);
    }

    @PostMapping("/rules")
    public Object createRule(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.RULE_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/rules")
    public Object updateRule(@RequestBody(required = false) Object body) {
        return put(CostLiteRouteKeys.RULE_UPDATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/rules/copy")
    public Object copyRule(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.RULE_COPY, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/rules/tier-preview")
    public Object previewRuleTier(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.RULE_TIER_PREVIEW, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/rules/conflict-preview")
    public Object previewRuleConflict(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.RULE_CONFLICT_PREVIEW, body, CostLiteAuth.MANAGEMENT);
    }

    @DeleteMapping("/rules/{ruleIds}")
    public Object deleteRules(@PathVariable String ruleIds) {
        return delete(CostLiteRouteKeys.RULE_DELETE, CostLiteAuth.MANAGEMENT,
                "ruleIds", ruleIds);
    }

    @GetMapping("/formulas")
    public Object formulas(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FORMULA_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/formulas/stats")
    public Object formulaStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FORMULA_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/formulas/options")
    public Object formulaOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FORMULA_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/formulas/template-options")
    public Object formulaTemplateOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FORMULA_TEMPLATE_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/formulas/{formulaId}")
    public Object formula(@PathVariable Long formulaId) {
        return get(CostLiteRouteKeys.FORMULA_DETAIL, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "formulaId", formulaId);
    }

    @GetMapping("/formulas/{formulaId}/governance")
    public Object formulaGovernance(@PathVariable Long formulaId) {
        return get(CostLiteRouteKeys.FORMULA_GOVERNANCE, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "formulaId", formulaId);
    }

    @GetMapping("/formulas/{formulaId}/versions")
    public Object formulaVersions(@PathVariable Long formulaId) {
        return get(CostLiteRouteKeys.FORMULA_VERSIONS, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "formulaId", formulaId);
    }

    @GetMapping("/formula-versions/{versionId}")
    public Object formulaVersion(@PathVariable Long versionId) {
        return get(CostLiteRouteKeys.FORMULA_VERSION_DETAIL, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "versionId", versionId);
    }

    @PostMapping("/formulas")
    public Object createFormula(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.FORMULA_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/formulas")
    public Object updateFormula(@RequestBody(required = false) Object body) {
        return put(CostLiteRouteKeys.FORMULA_UPDATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/formula-versions/{versionId}/rollback")
    public Object rollbackFormulaVersion(@PathVariable Long versionId) {
        return put(CostLiteRouteKeys.FORMULA_VERSION_ROLLBACK, null, CostLiteAuth.MANAGEMENT,
                "versionId", versionId);
    }

    @PostMapping("/formulas/test")
    public Object testFormula(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.FORMULA_TEST, body, CostLiteAuth.MANAGEMENT);
    }

    @DeleteMapping("/formulas/{formulaIds}")
    public Object deleteFormulas(@PathVariable String formulaIds) {
        return delete(CostLiteRouteKeys.FORMULA_DELETE, CostLiteAuth.MANAGEMENT,
                "formulaIds", formulaIds);
    }

    @GetMapping("/versions")
    public Object versions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.PUBLISH_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/versions/stats")
    public Object versionStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.PUBLISH_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/versions/precheck/{sceneId}")
    public Object versionPrecheck(@PathVariable Long sceneId) {
        return get(CostLiteRouteKeys.PUBLISH_PRECHECK, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "sceneId", sceneId);
    }

    @GetMapping("/versions/diff")
    public Object versionDiff(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.PUBLISH_DIFF, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/versions/{versionId}")
    public Object version(@PathVariable Long versionId,
                          @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.PUBLISH_DETAIL, query, CostLiteAuth.MANAGEMENT,
                "versionId", versionId);
    }

    @PostMapping("/versions")
    public Object createVersion(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.PUBLISH_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/versions/{versionId}/activate")
    public Object activateVersion(@PathVariable Long versionId) {
        return put(CostLiteRouteKeys.PUBLISH_ACTIVATE, null, CostLiteAuth.MANAGEMENT,
                "versionId", versionId);
    }

    @PutMapping("/versions/{versionId}/rollback")
    public Object rollbackVersion(@PathVariable Long versionId) {
        return put(CostLiteRouteKeys.PUBLISH_ROLLBACK, null, CostLiteAuth.MANAGEMENT,
                "versionId", versionId);
    }

    @GetMapping("/simulations/stats")
    public Object simulationStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.SIMULATION_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/simulations")
    public Object simulations(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.SIMULATION_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/simulations")
    public Object executeSimulation(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.SIMULATION_EXECUTE, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/simulations/batch")
    public Object executeSimulationBatch(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.SIMULATION_BATCH_EXECUTE, body, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/simulations/{simulationId}")
    public Object simulation(@PathVariable Long simulationId) {
        return get(CostLiteRouteKeys.SIMULATION_DETAIL, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "simulationId", simulationId);
    }

    @PostMapping("/input-build/preview")
    public Object previewInputBuild(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.INPUT_BUILD_PREVIEW, body, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/tasks/stats")
    public Object taskStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.TASK_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/tasks/overview")
    public Object taskOverview(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.TASK_OVERVIEW, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/tasks")
    public Object tasks(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.TASK_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/tasks/precheck")
    public Object precheckTask(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.TASK_PRECHECK, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/tasks")
    public Object submitTask(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.TASK_SUBMIT, body, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/task-input-batches")
    public Object createInputBatch(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.TASK_INPUT_BATCH_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/task-input-batches")
    public Object inputBatches(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.TASK_INPUT_BATCH_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/task-input-batches/{batchId}")
    public Object inputBatch(@PathVariable Long batchId,
                             @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.TASK_INPUT_BATCH_DETAIL, query, CostLiteAuth.MANAGEMENT,
                "batchId", batchId);
    }

    @GetMapping("/tasks/{taskId}")
    public Object task(@PathVariable Long taskId,
                       @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.TASK_DETAIL, query, CostLiteAuth.MANAGEMENT,
                "taskId", taskId);
    }

    @PutMapping("/task-details/{detailId}/retry")
    public Object retryTaskDetail(@PathVariable Long detailId) {
        return put(CostLiteRouteKeys.TASK_DETAIL_RETRY, null, CostLiteAuth.MANAGEMENT,
                "detailId", detailId);
    }

    @PutMapping("/task-partitions/{partitionId}/retry")
    public Object retryTaskPartition(@PathVariable Long partitionId) {
        return put(CostLiteRouteKeys.TASK_PARTITION_RETRY, null, CostLiteAuth.MANAGEMENT,
                "partitionId", partitionId);
    }

    @PutMapping("/tasks/{taskId}/cancel")
    public Object cancelTask(@PathVariable Long taskId) {
        return put(CostLiteRouteKeys.TASK_CANCEL, null, CostLiteAuth.MANAGEMENT,
                "taskId", taskId);
    }

    @GetMapping("/results/stats")
    public Object resultStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.RESULT_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/results/compare")
    public Object resultCompare(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.RESULT_COMPARE, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/results")
    public Object results(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.RESULT_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/results/{resultId}")
    public Object result(@PathVariable Long resultId) {
        return get(CostLiteRouteKeys.RESULT_DETAIL, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "resultId", resultId);
    }

    @GetMapping("/traces/{traceId}")
    public Object trace(@PathVariable Long traceId) {
        return get(CostLiteRouteKeys.TRACE_DETAIL, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "traceId", traceId);
    }

    @GetMapping("/input-template")
    public Object inputTemplate(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.INPUT_TEMPLATE, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/template")
    public Object feeInputTemplate(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.FEE_INPUT_TEMPLATE, query, CostLiteAuth.MANAGEMENT);
    }

    @PostMapping("/calculate")
    public Object calculate(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.FEE_CALCULATE, body, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/billing-logs/stats")
    public Object billingLogStats(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.BILLING_LOG_STATS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/logs")
    public Object logs(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.BILLING_LOG_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/logs/{simulationId}")
    public Object log(@PathVariable Long simulationId,
                      @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.BILLING_LOG_DETAIL, query, CostLiteAuth.MANAGEMENT,
                "simulationId", simulationId);
    }

    @GetMapping("/open/scenes")
    public Object openScenes(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.OPEN_SCENES, query, CostLiteAuth.OPEN);
    }

    @GetMapping("/open/scenes/{sceneId}/fees")
    public Object openSceneFees(@PathVariable Long sceneId,
                                @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.OPEN_SCENE_FEES, query, CostLiteAuth.OPEN,
                "sceneId", sceneId);
    }

    @GetMapping("/open/scenes/{sceneId}/versions")
    public Object openSceneVersions(@PathVariable Long sceneId,
                                    @RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.OPEN_SCENE_VERSIONS, query, CostLiteAuth.OPEN,
                "sceneId", sceneId);
    }

    @GetMapping("/open/template")
    public Object openTemplate(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.OPEN_TEMPLATE, query, CostLiteAuth.OPEN);
    }

    @PostMapping("/open/calculate")
    public Object openCalculate(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.OPEN_CALCULATE, body, CostLiteAuth.OPEN);
    }

    @PostMapping("/open/token")
    public Object openToken(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.OPEN_AUTH_TOKEN, body, CostLiteAuth.NONE);
    }

    @GetMapping("/open-apps")
    public Object openApps(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.OPEN_APP_LIST, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/open-apps/scene-options")
    public Object openAppSceneOptions(@RequestParam MultiValueMap<String, String> query) {
        return get(CostLiteRouteKeys.OPEN_APP_SCENE_OPTIONS, query, CostLiteAuth.MANAGEMENT);
    }

    @GetMapping("/open-apps/{appId}")
    public Object openApp(@PathVariable Long appId) {
        return get(CostLiteRouteKeys.OPEN_APP_DETAIL, Collections.<String, Object>emptyMap(),
                CostLiteAuth.MANAGEMENT, "appId", appId);
    }

    @PostMapping("/open-apps")
    public Object createOpenApp(@RequestBody(required = false) Object body) {
        return post(CostLiteRouteKeys.OPEN_APP_CREATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/open-apps")
    public Object updateOpenApp(@RequestBody(required = false) Object body) {
        return put(CostLiteRouteKeys.OPEN_APP_UPDATE, body, CostLiteAuth.MANAGEMENT);
    }

    @PutMapping("/open-apps/{appId}/reset-secret")
    public Object resetOpenAppSecret(@PathVariable Long appId) {
        return put(CostLiteRouteKeys.OPEN_APP_RESET_SECRET, null, CostLiteAuth.MANAGEMENT,
                "appId", appId);
    }

    @DeleteMapping("/open-apps/{appIds}")
    public Object deleteOpenApps(@PathVariable String appIds) {
        return delete(CostLiteRouteKeys.OPEN_APP_DELETE, CostLiteAuth.MANAGEMENT,
                "appIds", appIds);
    }

    private Object get(String routeKey,
                       Map<String, ?> query,
                       CostLiteAuth auth,
                       Object... variables) {
        return invoke(CostLiteRequest.get(route(routeKey, variables))
                .query(query)
                .auth(auth)
                .build());
    }

    private Object post(String routeKey,
                        Object body,
                        CostLiteAuth auth,
                        Object... variables) {
        return invoke(CostLiteRequest.post(route(routeKey, variables))
                .body(body)
                .auth(auth)
                .build());
    }

    private Object put(String routeKey,
                       Object body,
                       CostLiteAuth auth,
                       Object... variables) {
        return invoke(CostLiteRequest.put(route(routeKey, variables))
                .body(body)
                .auth(auth)
                .build());
    }

    private Object delete(String routeKey,
                          CostLiteAuth auth,
                          Object... variables) {
        return invoke(CostLiteRequest.delete(route(routeKey, variables))
                .auth(auth)
                .build());
    }

    private Object invoke(CostLiteRequest request) {
        try {
            CostLiteResponse response = client.execute(request);
            return responseFactory.success(response);
        } catch (RuntimeException exception) {
            return responseFactory.failure(exception);
        }
    }

    private String route(String routeKey, Object... variables) {
        if (variables == null || variables.length == 0) {
            return routeResolver.resolve(routeKey);
        }
        if (variables.length % 2 != 0) {
            throw new IllegalArgumentException("轻量计费路由变量必须按名称和值成对传入");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < variables.length; index += 2) {
            values.put(String.valueOf(variables[index]), variables[index + 1]);
        }
        return routeResolver.resolve(routeKey, values);
    }

    private Map<String, Object> toQuery(MultiValueMap<String, String> query) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (query != null) {
            for (Map.Entry<String, java.util.List<String>> entry : query.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private Map<String, Object> with(MultiValueMap<String, String> query, String name, Object value) {
        Map<String, Object> result = toQuery(query);
        result.put(name, value);
        return result;
    }
}
