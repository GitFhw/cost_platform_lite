package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.cost.CostFormula;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.cost.CostVariable;
import com.ruoyi.system.domain.cost.CostVariableGroup;
import com.ruoyi.system.domain.vo.*;
import com.ruoyi.system.mapper.cost.CostFormulaMapper;
import com.ruoyi.system.mapper.cost.CostSceneMapper;
import com.ruoyi.system.mapper.cost.CostVariableGroupMapper;
import com.ruoyi.system.mapper.cost.CostVariableMapper;
import com.ruoyi.system.service.cost.ICostExpressionService;
import com.ruoyi.system.service.cost.ICostVariableService;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import com.ruoyi.system.service.cost.remote.RemoteInvokeResult;
import com.ruoyi.system.service.cost.remote.RemoteVariableAccessPipeline;
import com.ruoyi.system.service.cost.remote.RemoteVariableConfig;
import com.ruoyi.system.service.cost.variable.VariableSourceHandlerChain;
import com.ruoyi.system.service.cost.variable.VariableSourceHandlerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 变量中心服务实现。
 *
 * <p>线程二重点承接变量中心、第三方接入变量和共享影响因素模板治理。</p>
 *
 * @author HwFan
 */
@Service
public class CostVariableServiceImpl implements ICostVariableService {
    private static final Logger log = LoggerFactory.getLogger(CostVariableServiceImpl.class);
    private static final String DICT_TYPE_VARIABLE_TYPE = "cost_variable_type";
    private static final String DICT_TYPE_SOURCE_TYPE = "cost_variable_source_type";
    private static final String DICT_TYPE_DATA_TYPE = "cost_variable_data_type";
    private static final String DICT_TYPE_VARIABLE_STATUS = "cost_variable_status";
    private static final String DICT_TYPE_AUTH_TYPE = "cost_variable_auth_type";
    private static final String DICT_TYPE_SYNC_MODE = "cost_variable_sync_mode";
    private static final String DICT_TYPE_CACHE_POLICY = "cost_variable_cache_policy";
    private static final String DICT_TYPE_FALLBACK_POLICY = "cost_variable_fallback_policy";
    private static final Set<String> SUPPORTED_REMOTE_METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    private static final Set<String> SUPPORTED_REMOTE_ADAPTERS = Set.of(
            RemoteVariableAccessPipeline.ADAPTER_STANDARD,
            RemoteVariableAccessPipeline.ADAPTER_ROOT_ARRAY,
            RemoteVariableAccessPipeline.ADAPTER_PAGE_ENVELOPE,
            RemoteVariableAccessPipeline.ADAPTER_SINGLE_OBJECT);
    private static final String REMOTE_TOKEN_PLACEHOLDER = "__PASTE_TOKEN_HERE__";
    private static final String REMOTE_SECRET_MASK = "******";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CostVariableMapper variableMapper;

    @Autowired
    private CostSceneMapper sceneMapper;

    @Autowired
    private CostVariableGroupMapper variableGroupMapper;

    @Autowired
    private CostFormulaMapper formulaMapper;

    @Autowired
    private CostDictionaryProvider dictionaryProvider;
    private final VariableSourceHandlerSupport variableSourceHandlerSupport = new VariableSourceHandlerSupport() {
        @Override
        public void validateDictTypeExists(String dictType) {
            CostVariableServiceImpl.this.validateDictTypeExists(dictType);
        }

        @Override
        public void validateRemoteVariableConfig(CostVariable variable) {
            CostVariableServiceImpl.this.validateRemoteVariableConfig(variable);
        }

        @Override
        public void validateFormulaVariableConfig(CostVariable variable) {
            CostVariableServiceImpl.this.validateFormulaVariableConfig(variable);
        }
    };
    @Autowired
    private ICostExpressionService expressionService;
    @Autowired
    private RemoteVariableAccessPipeline remoteVariableAccessPipeline;
    @Autowired
    private VariableSourceHandlerChain variableSourceHandlerChain;

    @Autowired
    private CostGovernanceImpactSupport governanceImpactSupport;

    @Override
    public List<CostVariable> selectVariableList(CostVariable variable) {
        List<CostVariable> variables = variableMapper.selectVariableList(variable);
        variables.forEach(this::maskRemoteAuthConfig);
        return variables;
    }

    @Override
    public CostVariable selectVariableById(Long variableId) {
        return maskRemoteAuthConfigCopy(selectVariableByIdRaw(variableId));
    }

    @Override
    public List<CostVariable> selectVariableOptions(CostVariable variable) {
        return variableMapper.selectVariableOptions(variable);
    }

    private CostVariable selectVariableByIdRaw(Long variableId) {
        return variableId == null ? null : variableMapper.selectById(variableId);
    }

    private CostVariable maskRemoteAuthConfigCopy(CostVariable variable) {
        if (variable == null) {
            return null;
        }
        CostVariable copy = new CostVariable();
        BeanUtils.copyProperties(variable, copy);
        maskRemoteAuthConfig(copy);
        return copy;
    }

    private void maskRemoteAuthConfig(CostVariable variable) {
        if (variable == null || StringUtils.isEmpty(variable.getAuthConfigJson())) {
            return;
        }
        ObjectNode masked = objectMapper.createObjectNode();
        masked.put("configured", true);
        masked.put("masked", true);
        masked.put("placeholder", REMOTE_SECRET_MASK);
        variable.setAuthConfigJson(toJson(masked));
    }

    private void preserveMaskedAuthConfig(CostVariable variable) {
        if (variable == null || variable.getVariableId() == null || !isMaskedAuthConfig(variable.getAuthConfigJson())) {
            return;
        }
        CostVariable current = selectVariableByIdRaw(variable.getVariableId());
        variable.setAuthConfigJson(current == null ? null : current.getAuthConfigJson());
    }

    private boolean isMaskedAuthConfig(String authConfigJson) {
        if (StringUtils.isEmpty(authConfigJson)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(authConfigJson);
            return node != null && node.path("masked").asBoolean(false);
        } catch (Exception ignored) {
            return REMOTE_SECRET_MASK.equals(StringUtils.trim(authConfigJson));
        }
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    @Override
    public Map<String, Object> selectVariableStats(CostVariable variable) {
        Map<String, Object> stats = variableMapper.selectVariableStats(variable);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("variableCount", 0);
        result.put("enabledVariableCount", 0);
        result.put("remoteVariableCount", 0);
        result.put("formulaVariableCount", 0);
        if (stats == null) {
            return result;
        }
        for (String key : result.keySet()) {
            Object value = stats.get(key);
            result.put(key, value == null ? 0 : value);
        }
        return result;
    }

    @Override
    public CostVariableGovernanceCheckVo selectVariableGovernanceCheck(Long variableId) {
        CostVariableGovernanceCheckVo check = variableMapper.selectVariableGovernanceCheck(variableId);
        if (check == null) {
            return null;
        }
        populateFormulaReferenceCount(check);
        normalizeGovernanceCount(check);

        boolean hasFeeRelRef = check.getFeeRelCount() > 0;
        boolean hasRuleConditionRef = check.getRuleConditionCount() > 0;
        boolean hasRuleQuantityRef = check.getRuleQuantityCount() > 0;
        boolean hasFormulaRef = check.getFormulaRefCount() > 0;
        boolean hasPublishedVersionRef = check.getPublishedVersionCount() > 0;

        check.setCanDelete(!hasRuleConditionRef && !hasRuleQuantityRef && !hasFormulaRef && !hasPublishedVersionRef);
        check.setCanDisable(!hasPublishedVersionRef);
        check.setRemoveBlockingReason(buildRemoveBlockingReason(check, hasFeeRelRef, hasRuleConditionRef, hasRuleQuantityRef, hasFormulaRef, hasPublishedVersionRef));
        check.setDisableBlockingReason(buildDisableBlockingReason(check, hasPublishedVersionRef));
        check.setRemoveAdvice(check.getCanDelete()
                ? (hasFeeRelRef ? "当前变量未被规则、公式和版本占用，费用变量关系会随删除自动清理。" : "当前变量未被规则、公式和版本占用，可直接删除。")
                : "请先解除规则、公式与发布版本引用后再删除变量；费用变量关系不单独阻断删除。");
        check.setDisableAdvice(check.getCanDisable() ? "变量停用后将不再出现在新增配置中，历史配置保留。"
                : "当前变量已进入发布版本，请先替换并发布新版本后再停用。");
        check.setImpactItems(governanceImpactSupport.buildVariableImpacts(check));
        populateFormulaDependencies(check);
        return check;
    }

    @Override
    public boolean checkVariableCodeUnique(CostVariable variable) {
        Long variableId = variable.getVariableId() == null ? -1L : variable.getVariableId();
        Long count = variableMapper.selectCount(Wrappers.<CostVariable>lambdaQuery()
                .eq(CostVariable::getSceneId, variable.getSceneId())
                .eq(CostVariable::getVariableCode, variable.getVariableCode())
                .ne(variableId != -1L, CostVariable::getVariableId, variableId));
        return count == null || count == 0 ? UserConstants.UNIQUE : UserConstants.NOT_UNIQUE;
    }

    @Override
    public int insertVariable(CostVariable variable) {
        validateVariableConfig(variable);
        normalizeVariableSourceFields(variable);
        fillDefaultFields(variable);
        return variableMapper.insert(variable);
    }

    @Override
    public int updateVariable(CostVariable variable) {
        preserveMaskedAuthConfig(variable);
        validateDisableBeforeUpdate(variable);
        validateVariableConfig(variable);
        normalizeVariableSourceFields(variable);
        fillDefaultFields(variable);
        return variableMapper.updateById(variable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteVariableByIds(Long[] variableIds) {
        if (variableIds == null || variableIds.length == 0) {
            return 0;
        }
        for (Long variableId : variableIds) {
            CostVariableGovernanceCheckVo check = selectVariableGovernanceCheck(variableId);
            if (check != null && !Boolean.TRUE.equals(check.getCanDelete())) {
                throw new ServiceException(String.format("%s不能删除：%s", check.getVariableName(), check.getRemoveBlockingReason()));
            }
        }
        variableMapper.deleteFeeVariableRelByVariableIds(variableIds);
        return variableMapper.deleteBatchIds(Arrays.asList(variableIds));
    }

    @Override
    public CostVariableImportPreviewVo previewImport(MultipartFile file) throws Exception {
        return parseImportFile(file, false, false, null);
    }

    @Override
    public CostVariableImportPreviewVo previewImport(MultipartFile file, boolean updateSupport) throws Exception {
        return parseImportFile(file, false, updateSupport, null);
    }

    @Override
    public CostVariableImportPreviewVo importVariables(MultipartFile file, boolean updateSupport, String operName) throws Exception {
        return parseImportFile(file, true, updateSupport, operName);
    }

    @Override
    public CostVariable copyVariable(CostVariableCopyRequest request) {
        if (request == null || request.getVariableId() == null) {
            throw new ServiceException("请选择需要复制的变量");
        }
        CostVariable source = selectVariableByIdRaw(request.getVariableId());
        if (source == null) {
            throw new ServiceException("源变量不存在");
        }

        Long targetSceneId = request.getTargetSceneId() == null ? source.getSceneId() : request.getTargetSceneId();
        validateSceneEnabled(targetSceneId, "变量");

        Long targetGroupId = request.getTargetGroupId();
        if (request.getTargetSceneId() == null && targetGroupId == null) {
            targetGroupId = source.getGroupId();
        }
        validateVariableGroup(targetSceneId, targetGroupId);

        CostVariable copied = new CostVariable();
        copied.setSceneId(targetSceneId);
        copied.setGroupId(targetGroupId);
        copied.setVariableCode(StringUtils.defaultIfEmpty(request.getVariableCode(), source.getVariableCode() + "_COPY"));
        copied.setVariableName(StringUtils.defaultIfEmpty(request.getVariableName(), source.getVariableName() + "-复制"));
        copied.setVariableType(source.getVariableType());
        copied.setSourceType(source.getSourceType());
        copied.setSourceSystem(source.getSourceSystem());
        copied.setDictType(source.getDictType());
        copied.setRemoteApi(source.getRemoteApi());
        copied.setAuthType(source.getAuthType());
        copied.setAuthConfigJson(source.getAuthConfigJson());
        copied.setDataPath(source.getDataPath());
        copied.setMappingConfigJson(source.getMappingConfigJson());
        copied.setSyncMode(source.getSyncMode());
        copied.setCachePolicy(source.getCachePolicy());
        copied.setFallbackPolicy(source.getFallbackPolicy());
        copied.setFormulaExpr(source.getFormulaExpr());
        copied.setFormulaCode(source.getFormulaCode());
        copied.setDataType(source.getDataType());
        copied.setDefaultValue(source.getDefaultValue());
        copied.setPrecisionScale(source.getPrecisionScale());
        copied.setStatus(source.getStatus());
        copied.setSortNo(source.getSortNo());
        copied.setRemark(StringUtils.defaultIfEmpty(source.getRemark(), "共享复制变量"));

        if (!checkVariableCodeUnique(copied)) {
            throw new ServiceException("目标场景下变量编码已存在，请更换新的变量编码");
        }
        insertVariable(copied);
        return copied;
    }

    @Override
    public List<CostVariableTemplateVo> selectSharedTemplates() {
        List<CostVariableTemplateVo> templates = new ArrayList<>();
        templates.add(buildTemplate("STORAGE_LEASE_BASE", "仓储保管共享因素模板",
                "适用于仓储保管、仓租计费等场景，沉淀账期、天数、面积、客户等级等共用因素。",
                "建议优先使用 contract.* / storage.* 命名空间。", buildStorageLeaseTemplateItems()));
        templates.add(buildTemplate("ZX_OPERATION_BASE", "装卸/清仓共享因素模板",
                "适用于装卸费、清仓费等共用因素模板，重点体现 zx.* 命名空间。",
                "建议使用 zx.tradeType、zx.qty、zx.weight 等路径型变量编码。", buildZxTemplateItems()));
        templates.add(buildTemplate("SG_OPERATION_BASE", "疏港共享因素模板",
                "适用于疏港及港口操作类费用，体现 sg.* 命名空间和港口操作公共输入。",
                "建议使用 sg.tradeType、sg.qty、sg.weight 等路径型变量编码。", buildSgTemplateItems()));
        populateTemplateGovernanceSummary(templates);
        return templates;
    }

    @Override
    public Map<String, Object> applySharedTemplate(CostVariableTemplateApplyRequest request, String operName) {
        if (request == null || request.getSceneId() == null) {
            throw new ServiceException("请选择模板应用的目标场景");
        }
        validateSceneEnabled(request.getSceneId(), "变量模板");
        validateVariableGroup(request.getSceneId(), request.getGroupId());

        List<Map<String, Object>> items = findTemplateItems(request.getTemplateCode());
        if (items.isEmpty()) {
            throw new ServiceException("共享模板不存在或尚未配置变量条目");
        }

        int inserted = 0;
        int updated = 0;
        List<String> skippedCodes = new ArrayList<>();
        boolean updateSupport = Boolean.TRUE.equals(request.getUpdateSupport());
        for (Map<String, Object> item : items) {
            CostVariable variable = buildVariableFromTemplateItem(request.getSceneId(), request.getGroupId(), item, operName);
            CostVariable existing = selectExistingVariable(request.getSceneId(), variable.getVariableCode());
            if (existing == null) {
                insertVariable(variable);
                inserted++;
                continue;
            }
            if (!updateSupport) {
                skippedCodes.add(variable.getVariableCode());
                continue;
            }
            variable.setVariableId(existing.getVariableId());
            variable.setUpdateBy(operName);
            updateVariable(variable);
            updated++;
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("templateCode", request.getTemplateCode());
        result.put("insertedCount", inserted);
        result.put("updatedCount", updated);
        result.put("skippedCount", skippedCodes.size());
        result.put("skippedCodes", skippedCodes);
        result.put("message", String.format("共享模板应用完成：新增%d条，更新%d条，跳过%d条。", inserted, updated, skippedCodes.size()));
        return result;
    }

    @Override
    public Map<String, Object> testRemoteConnection(Map<String, Object> request) {
        RemoteVariableConfig config = resolveRemoteVariableConfig(request, null);
        RemoteInvokeResult invokeResult = remoteVariableAccessPipeline.invoke(config);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("success", invokeResult.success);
        result.put("message", invokeResult.message);
        result.put("remoteApi", config.remoteApi);
        result.put("sourceSystem", config.sourceSystem);
        result.put("authType", config.authType);
        result.put("requestMethod", config.requestMethod);
        result.put("adapterType", config.adapterType);
        result.put("contentType", config.contentType);
        result.put("statusCode", invokeResult.statusCode);
        result.put("rowCount", invokeResult.rowCount());
        result.put("elapsedMs", invokeResult.elapsedMs);
        result.put("requestUrl", invokeResult.requestUrl);
        result.put("requestHeaderNames", invokeResult.requestHeaderNames);
        result.put("authHeaderApplied", invokeResult.authHeaderApplied);
        result.put("authHeaderName", invokeResult.authHeaderName);
        result.put("authTokenPresent", invokeResult.authTokenPresent);
        result.put("responsePreview", invokeResult.responsePreview());
        result.put("failureStage", invokeResult.failureStage);
        result.put("errorType", invokeResult.errorType);
        result.put("diagnosticMessage", invokeResult.diagnosticMessage);
        result.put("checkedAt", new Date());
        return result;
    }

    @Override
    public Map<String, Object> previewRemoteData(Map<String, Object> request) {
        Long variableId = parseLong(request.get("variableId"));
        CostVariable variable = variableId == null ? null : selectVariableByIdRaw(variableId);
        RemoteVariableConfig config = resolveRemoteVariableConfig(request, variable);
        RemoteInvokeResult invokeResult = remoteVariableAccessPipeline.invoke(config);
        List<Map<String, Object>> rawRows = remoteVariableAccessPipeline.buildPreviewRawRows(invokeResult.items);
        List<Map<String, Object>> mappedRows = remoteVariableAccessPipeline.buildPreviewMappedRows(config, invokeResult.items);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("success", invokeResult.success);
        result.put("message", invokeResult.message);
        result.put("rawRows", rawRows);
        result.put("mappedRows", mappedRows);
        result.put("sourceSystem", config.sourceSystem);
        result.put("syncMode", config.syncMode);
        result.put("cachePolicy", config.cachePolicy);
        result.put("fallbackPolicy", config.fallbackPolicy);
        result.put("requestMethod", config.requestMethod);
        result.put("adapterType", config.adapterType);
        result.put("statusCode", invokeResult.statusCode);
        result.put("elapsedMs", invokeResult.elapsedMs);
        result.put("rowCount", invokeResult.rowCount());
        result.put("responseMessage", invokeResult.responseMessage());
        result.put("requestUrl", invokeResult.requestUrl);
        result.put("requestHeaderNames", invokeResult.requestHeaderNames);
        result.put("authHeaderApplied", invokeResult.authHeaderApplied);
        result.put("authHeaderName", invokeResult.authHeaderName);
        result.put("authTokenPresent", invokeResult.authTokenPresent);
        result.put("failureStage", invokeResult.failureStage);
        result.put("errorType", invokeResult.errorType);
        result.put("diagnosticMessage", invokeResult.diagnosticMessage);
        result.put("previewAt", new Date());
        return result;
    }

    @Override
    public Map<String, Object> refreshRemoteCache(Long sceneId) {
        Long remoteVariableCount = variableMapper.countRemoteVariableByScene(sceneId);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("sceneId", sceneId);
        result.put("remoteVariableCount", remoteVariableCount == null ? 0 : remoteVariableCount);
        result.put("cacheRefreshSupported", false);
        result.put("message", "当前仅完成第三方变量统计，尚未接入实际缓存刷新链路。请通过接口测试或数据预览确认变量取数结果。");
        result.put("cachePolicy", "MANUAL_REFRESH");
        result.put("refreshAt", new Date());
        return result;
    }

    /**
     * 解析导入文件并输出预览/导入结果。
     *
     * <p>线程二要求变量中心支持模板导入、导入预览和校验报告，因此先统一把导入解析、校验和实际落库集中到这里。</p>
     */
    private CostVariableImportPreviewVo parseImportFile(MultipartFile file, boolean persist, boolean updateSupport, String operName) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请先上传变量导入文件");
        }
        ExcelUtil<CostVariableImportRow> util = new ExcelUtil<>(CostVariableImportRow.class);
        List<CostVariableImportRow> rows = util.importExcel(file.getInputStream());

        CostVariableImportPreviewVo preview = new CostVariableImportPreviewVo();
        preview.setTotalRows(rows.size());
        if (rows.isEmpty()) {
            preview.setImportable(Boolean.FALSE);
            return preview;
        }

        Map<String, CostScene> sceneCache = new LinkedHashMap<>();
        Map<String, CostVariableGroup> groupCache = new LinkedHashMap<>();
        Set<String> fileUniqueKeys = new LinkedHashSet<>();
        int passRows = 0;
        int failRows = 0;
        for (int index = 0; index < rows.size(); index++) {
            int rowNum = index + 2;
            CostVariableImportRow row = rows.get(index);
            List<CostVariableImportIssueVo> errors = validateImportRow(row, sceneCache, groupCache, fileUniqueKeys, updateSupport);
            if (!errors.isEmpty()) {
                failRows++;
                for (CostVariableImportIssueVo issue : errors) {
                    fillImportIssueContext(rowNum, row, issue);
                    preview.getIssues().add(issue);
                }
                continue;
            }

            CostVariable variable = buildVariableFromImportRow(row, sceneCache, groupCache);
            CostVariable existing = selectExistingVariable(variable.getSceneId(), variable.getVariableCode());
            String importAction = existing == null ? "INSERT" : "UPDATE";
            preview.getPreviewRows().add(buildImportPreviewRow(rowNum, row, importAction));
            passRows++;

            if (!persist) {
                continue;
            }

            try {
                if (existing == null) {
                    variable.setCreateBy(operName);
                    insertVariable(variable);
                } else {
                    variable.setVariableId(existing.getVariableId());
                    variable.setUpdateBy(operName);
                    updateVariable(variable);
                }
            } catch (ServiceException ex) {
                passRows--;
                failRows++;
                preview.getIssues().add(buildImportIssue(rowNum, row, ex.getMessage()));
            }
        }

        preview.setPassRows(passRows);
        preview.setFailRows(failRows);
        preview.setImportable(failRows == 0);
        return preview;
    }

    /**
     * 校验单行导入数据。
     */
    private List<CostVariableImportIssueVo> validateImportRow(CostVariableImportRow row, Map<String, CostScene> sceneCache,
                                           Map<String, CostVariableGroup> groupCache, Set<String> fileUniqueKeys, boolean updateSupport) {
        List<CostVariableImportIssueVo> errors = new ArrayList<>();
        if (row == null) {
            errors.add(buildImportIssue(null, null, null, "整行", null, "导入行为空"));
            return errors;
        }
        if (StringUtils.isEmpty(row.getSceneCode())) {
            errors.add(buildImportIssue(null, row, "sceneCode", "场景编码", row.getSceneCode(), "场景编码不能为空"));
            return errors;
        }
        if (StringUtils.isEmpty(row.getVariableCode())) {
            errors.add(buildImportIssue(null, row, "variableCode", "变量编码", row.getVariableCode(), "变量编码不能为空"));
        }
        if (StringUtils.isEmpty(row.getVariableName())) {
            errors.add(buildImportIssue(null, row, "variableName", "变量名称", row.getVariableName(), "变量名称不能为空"));
        }

        CostScene scene = resolveSceneByCode(row.getSceneCode(), sceneCache);
        if (scene == null) {
            errors.add(buildImportIssue(null, row, "sceneCode", "场景编码", row.getSceneCode(), "场景编码不存在：" + row.getSceneCode()));
            return errors;
        }
        if (!"0".equals(scene.getStatus())) {
            errors.add(buildImportIssue(null, row, "sceneCode", "场景编码", row.getSceneCode(), "场景不是正常状态：" + row.getSceneCode()));
        }

        CostVariableGroup group = resolveGroupByCode(scene.getSceneId(), row.getGroupCode(), groupCache);
        if (StringUtils.isNotEmpty(row.getGroupCode()) && group == null) {
            errors.add(buildImportIssue(null, row, "groupCode", "变量分组编码", row.getGroupCode(), "变量分组编码不存在：" + row.getGroupCode()));
        }

        String uniqueKey = scene.getSceneId() + "::" + row.getVariableCode();
        if (!fileUniqueKeys.add(uniqueKey)) {
            errors.add(buildImportIssue(null, row, "variableCode", "变量编码", row.getVariableCode(), "导入文件中存在重复变量编码：" + row.getVariableCode()));
        }

        if (errors.isEmpty()) {
            try {
                CostVariable variable = buildVariableFromImportRow(row, sceneCache, groupCache);
                CostVariable existing = selectExistingVariable(variable.getSceneId(), variable.getVariableCode());
                if (existing != null && !updateSupport) {
                    errors.add(buildImportIssue(null, row, "variableCode", "变量编码", row.getVariableCode(), "变量编码已存在，若需更新请勾选覆盖导入：" + row.getVariableCode()));
                } else if (existing != null) {
                    variable.setVariableId(existing.getVariableId());
                }
                validateVariableConfig(variable);
            } catch (ServiceException ex) {
                errors.add(buildImportConfigIssue(row, ex.getMessage()));
            }
        }
        return errors;
    }

    /**
     * 根据导入行构建变量对象。
     */
    private CostVariable buildVariableFromImportRow(CostVariableImportRow row, Map<String, CostScene> sceneCache,
                                                    Map<String, CostVariableGroup> groupCache) {
        CostScene scene = resolveSceneByCode(row.getSceneCode(), sceneCache);
        CostVariableGroup group = resolveGroupByCode(scene.getSceneId(), row.getGroupCode(), groupCache);

        CostVariable variable = new CostVariable();
        variable.setSceneId(scene.getSceneId());
        variable.setGroupId(group == null ? null : group.getGroupId());
        variable.setVariableCode(StringUtils.trim(row.getVariableCode()));
        variable.setVariableName(StringUtils.trim(row.getVariableName()));
        variable.setVariableType(StringUtils.defaultIfEmpty(StringUtils.trim(row.getVariableType()), "TEXT"));
        variable.setSourceType(StringUtils.defaultIfEmpty(StringUtils.trim(row.getSourceType()), "INPUT"));
        variable.setSourceSystem(StringUtils.trim(row.getSourceSystem()));
        variable.setDictType(StringUtils.trim(row.getDictType()));
        variable.setRemoteApi(StringUtils.trim(row.getRemoteApi()));
        variable.setAuthType(StringUtils.defaultIfEmpty(StringUtils.trim(row.getAuthType()), "NONE"));
        variable.setAuthConfigJson(StringUtils.trim(row.getAuthConfigJson()));
        variable.setDataPath(StringUtils.trim(row.getDataPath()));
        variable.setMappingConfigJson(StringUtils.trim(row.getMappingConfigJson()));
        variable.setSyncMode(StringUtils.defaultIfEmpty(StringUtils.trim(row.getSyncMode()), "REALTIME"));
        variable.setCachePolicy(StringUtils.defaultIfEmpty(StringUtils.trim(row.getCachePolicy()), "MANUAL_REFRESH"));
        variable.setFallbackPolicy(StringUtils.defaultIfEmpty(StringUtils.trim(row.getFallbackPolicy()), "FAIL_FAST"));
        variable.setFormulaExpr(StringUtils.trim(row.getFormulaExpr()));
        variable.setFormulaCode("");
        variable.setDataType(StringUtils.defaultIfEmpty(StringUtils.trim(row.getDataType()), "STRING"));
        variable.setDefaultValue(StringUtils.trim(row.getDefaultValue()));
        variable.setPrecisionScale(row.getPrecisionScale());
        variable.setStatus(StringUtils.defaultIfEmpty(StringUtils.trim(row.getStatus()), "0"));
        variable.setSortNo(row.getSortNo());
        variable.setRemark(StringUtils.trim(row.getRemark()));
        return variable;
    }

    /**
     * 构造导入预览行。
     */
    private Map<String, Object> buildImportPreviewRow(int rowNum, CostVariableImportRow row, String importAction) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("rowNum", rowNum);
        map.put("sceneCode", row.getSceneCode());
        map.put("groupCode", row.getGroupCode());
        map.put("variableCode", row.getVariableCode());
        map.put("variableName", row.getVariableName());
        map.put("sourceType", StringUtils.defaultIfEmpty(row.getSourceType(), "INPUT"));
        map.put("dataType", StringUtils.defaultIfEmpty(row.getDataType(), "STRING"));
        map.put("status", StringUtils.defaultIfEmpty(row.getStatus(), "0"));
        map.put("importAction", importAction);
        return map;
    }

    /**
     * 构造导入问题。
     */
    private CostVariableImportIssueVo buildImportIssue(int rowNum, CostVariableImportRow row, String message) {
        return buildImportIssue(rowNum, row, null, null, null, message);
    }

    private CostVariableImportIssueVo buildImportIssue(Integer rowNum, CostVariableImportRow row, String fieldName,
                                                       String fieldLabel, Object rawValue, String message) {
        CostVariableImportIssueVo issue = new CostVariableImportIssueVo();
        issue.setRowNum(rowNum);
        issue.setSceneCode(row == null ? null : row.getSceneCode());
        issue.setVariableCode(row == null ? null : row.getVariableCode());
        issue.setVariableName(row == null ? null : row.getVariableName());
        issue.setFieldName(fieldName);
        issue.setFieldLabel(fieldLabel);
        issue.setRawValue(rawValue == null ? null : String.valueOf(rawValue));
        issue.setMessage(message);
        return issue;
    }

    private void fillImportIssueContext(int rowNum, CostVariableImportRow row, CostVariableImportIssueVo issue) {
        issue.setRowNum(rowNum);
        if (row != null) {
            issue.setSceneCode(row.getSceneCode());
            issue.setVariableCode(row.getVariableCode());
            issue.setVariableName(row.getVariableName());
        }
    }

    private CostVariableImportIssueVo buildImportConfigIssue(CostVariableImportRow row, String message) {
        if (message == null) {
            return buildImportIssue(null, row, "config", "配置校验", null, "配置校验失败");
        }
        if (message.contains("变量类型")) {
            return buildImportIssue(null, row, "variableType", "变量类型", row.getVariableType(), message);
        }
        if (message.contains("来源类型")) {
            return buildImportIssue(null, row, "sourceType", "来源类型", row.getSourceType(), message);
        }
        if (message.contains("数据类型")) {
            return buildImportIssue(null, row, "dataType", "数据类型", row.getDataType(), message);
        }
        if (message.contains("状态")) {
            return buildImportIssue(null, row, "status", "状态", row.getStatus(), message);
        }
        if (message.contains("字典")) {
            return buildImportIssue(null, row, "dictType", "字典类型", row.getDictType(), message);
        }
        if (message.contains("接口地址") || message.contains("http://") || message.contains("https://")) {
            return buildImportIssue(null, row, "remoteApi", "第三方接口", row.getRemoteApi(), message);
        }
        if (message.contains("来源系统")) {
            return buildImportIssue(null, row, "sourceSystem", "来源系统", row.getSourceSystem(), message);
        }
        if (message.contains("鉴权")) {
            return buildImportIssue(null, row, "authConfigJson", "鉴权配置JSON", row.getAuthConfigJson(), message);
        }
        if (message.contains("映射")) {
            return buildImportIssue(null, row, "mappingConfigJson", "字段映射JSON", row.getMappingConfigJson(), message);
        }
        if (message.contains("公式")) {
            return buildImportIssue(null, row, "formulaExpr", "公式表达式", row.getFormulaExpr(), message);
        }
        return buildImportIssue(null, row, "config", "配置校验", null, message);
    }

    /**
     * 填充默认字段。
     */
    private void fillDefaultFields(CostVariable variable) {
        if (variable.getSortNo() == null) {
            variable.setSortNo(10);
        }
        if (variable.getPrecisionScale() == null) {
            variable.setPrecisionScale(2);
        }
    }

    /**
     * 校验变量中心核心配置必须符合线程二治理要求。
     */
    /**
     * 标准化治理计数。
     */
    private void normalizeGovernanceCount(CostVariableGovernanceCheckVo check) {
        check.setFeeRelCount(nullSafeLong(check.getFeeRelCount()));
        check.setRuleConditionCount(nullSafeLong(check.getRuleConditionCount()));
        check.setRuleQuantityCount(nullSafeLong(check.getRuleQuantityCount()));
        check.setFormulaRefCount(nullSafeLong(check.getFormulaRefCount()));
        check.setPublishedVersionCount(nullSafeLong(check.getPublishedVersionCount()));
    }

    private void populateFormulaReferenceCount(CostVariableGovernanceCheckVo check) {
        if (StringUtils.isEmpty(check.getVariableCode()) || check.getSceneId() == null) {
            check.setFormulaRefCount(0L);
            return;
        }
        long count = formulaMapper.selectList(Wrappers.<CostFormula>lambdaQuery()
                        .eq(CostFormula::getSceneId, check.getSceneId()))
                .stream()
                .filter(formula -> formulaReferencesVariable(formula, check.getVariableCode()))
                .count();
        check.setFormulaRefCount(count);
    }

    private boolean formulaReferencesVariable(CostFormula formula, String variableCode) {
        if (formula == null || StringUtils.isEmpty(formula.getFormulaExpr())) {
            return false;
        }
        try {
            CostExpressionAnalysisVo analysis = expressionService.analyzeExpression(formula.getFormulaExpr(), formula.getNamespaceScope());
            return analysis.getVariableReferences().contains(variableCode);
        } catch (Exception e) {
            log.warn("分析公式变量引用失败，formulaCode={}，variableCode={}", formula.getFormulaCode(), variableCode, e);
            return false;
        }
    }

    private void populateFormulaDependencies(CostVariableGovernanceCheckVo check) {
        check.setFormulaDependencies(Collections.emptyList());
        if (!"FORMULA".equalsIgnoreCase(check.getSourceType()) || check.getVariableId() == null) {
            return;
        }
        CostVariable variable = variableMapper.selectById(check.getVariableId());
        if (variable == null) {
            return;
        }
        CostVariableFormulaDependencyVo root = buildFormulaDependencyNode(variable, new LinkedHashSet<>());
        check.setFormulaDependencies(root.getChildren());
    }

    private CostVariableFormulaDependencyVo buildFormulaDependencyNode(CostVariable variable, LinkedHashSet<String> stack) {
        CostVariableFormulaDependencyVo node = buildVariableDependencyNode(variable);
        String variableCode = StringUtils.trim(variable.getVariableCode());
        if (StringUtils.isEmpty(variableCode)) {
            return node;
        }
        if (!stack.add(variableCode)) {
            node.setCircular(true);
            return node;
        }
        if ("FORMULA".equalsIgnoreCase(variable.getSourceType())) {
            CostFormula formula = findFormulaByCode(variable.getSceneId(), variable.getFormulaCode());
            if (formula != null) {
                node.setFormulaName(formula.getFormulaName());
                node.setChildren(resolveFormulaInputDependencies(variable.getSceneId(), formula, stack));
            }
        }
        stack.remove(variableCode);
        return node;
    }

    private List<CostVariableFormulaDependencyVo> resolveFormulaInputDependencies(Long sceneId, CostFormula formula, LinkedHashSet<String> stack) {
        if (formula == null || StringUtils.isEmpty(formula.getFormulaExpr())) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> references = extractFormulaVariableReferences(formula);
        if (references.isEmpty()) {
            return Collections.emptyList();
        }
        List<CostVariableFormulaDependencyVo> children = new ArrayList<>();
        for (String reference : references) {
            CostVariable dependency = findVariableByCode(sceneId, reference);
            if (dependency == null) {
                children.add(buildMissingDependencyNode(reference));
                continue;
            }
            if (stack.contains(reference)) {
                CostVariableFormulaDependencyVo circularNode = buildVariableDependencyNode(dependency);
                circularNode.setCircular(true);
                children.add(circularNode);
                continue;
            }
            children.add(buildFormulaDependencyNode(dependency, stack));
        }
        return children;
    }

    private LinkedHashSet<String> extractFormulaVariableReferences(CostFormula formula) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        try {
            CostExpressionAnalysisVo analysis = expressionService.analyzeExpression(formula.getFormulaExpr(), formula.getNamespaceScope());
            for (String reference : analysis.getVariableReferences()) {
                String normalized = normalizeVariableReference(reference);
                if (StringUtils.isNotEmpty(normalized)) {
                    references.add(normalized);
                }
            }
        } catch (Exception e) {
            log.warn("分析公式变量依赖失败，formulaCode={}", formula.getFormulaCode(), e);
        }
        return references;
    }

    private CostVariableFormulaDependencyVo buildVariableDependencyNode(CostVariable variable) {
        CostVariableFormulaDependencyVo node = new CostVariableFormulaDependencyVo();
        node.setVariableId(variable.getVariableId());
        node.setVariableCode(variable.getVariableCode());
        node.setVariableName(variable.getVariableName());
        node.setSourceType(variable.getSourceType());
        node.setStatus(variable.getStatus());
        node.setFormulaCode(variable.getFormulaCode());
        node.setFormulaName(variable.getFormulaName());
        return node;
    }

    private CostVariableFormulaDependencyVo buildMissingDependencyNode(String variableCode) {
        CostVariableFormulaDependencyVo node = new CostVariableFormulaDependencyVo();
        node.setVariableCode(variableCode);
        node.setVariableName("未维护变量");
        node.setMissing(true);
        return node;
    }

    private CostVariable findVariableByCode(Long sceneId, String variableCode) {
        if (sceneId == null || StringUtils.isEmpty(variableCode)) {
            return null;
        }
        return variableMapper.selectOne(Wrappers.<CostVariable>lambdaQuery()
                .eq(CostVariable::getSceneId, sceneId)
                .eq(CostVariable::getVariableCode, variableCode)
                .last("limit 1"));
    }

    private CostFormula findFormulaByCode(Long sceneId, String formulaCode) {
        if (sceneId == null || StringUtils.isEmpty(formulaCode)) {
            return null;
        }
        return formulaMapper.selectOne(Wrappers.<CostFormula>lambdaQuery()
                .eq(CostFormula::getSceneId, sceneId)
                .eq(CostFormula::getFormulaCode, formulaCode)
                .last("limit 1"));
    }

    private String normalizeVariableReference(String reference) {
        String raw = StringUtils.trim(reference);
        if (StringUtils.isEmpty(raw)) {
            return "";
        }
        int dotIndex = raw.indexOf('.');
        if (dotIndex <= 0 || dotIndex >= raw.length() - 1) {
            return raw;
        }
        String namespace = raw.substring(0, dotIndex);
        return "V".equalsIgnoreCase(namespace) ? raw.substring(dotIndex + 1) : "";
    }

    /**
     * 更新前停用校验。
     */
    private void validateDisableBeforeUpdate(CostVariable variable) {
        if (variable.getVariableId() == null || !"1".equals(variable.getStatus())) {
            return;
        }
        CostVariable current = selectVariableById(variable.getVariableId());
        if (current == null || "1".equals(current.getStatus())) {
            return;
        }
        CostVariableGovernanceCheckVo check = selectVariableGovernanceCheck(variable.getVariableId());
        if (check != null && !Boolean.TRUE.equals(check.getCanDisable())) {
            throw new ServiceException(String.format("%s不能停用：%s", check.getVariableName(), check.getDisableBlockingReason()));
        }
    }

    /**
     * 规范化变量来源字段。
     */
    /**
     * 校验公式变量配置。
     */
    private void validateFormulaVariableConfig(CostVariable variable) {
        if (StringUtils.isEmpty(variable.getFormulaCode())) {
            throw new ServiceException("公式变量必须引用公式编码；如为历史表达式，请先在公式实验室沉淀后再选择编码");
        }
        CostFormula formula = requireEnabledFormulaByCode(variable.getSceneId(), variable.getFormulaCode(), "公式变量");
        variable.setFormulaExpr(formula.getFormulaExpr());
    }

    /**
     * 按公式编码查询可用公式。
     */
    private CostFormula requireEnabledFormulaByCode(Long sceneId, String formulaCode, String fieldLabel) {
        CostFormula formula = formulaMapper.selectOne(Wrappers.<CostFormula>lambdaQuery()
                .eq(CostFormula::getSceneId, sceneId)
                .eq(CostFormula::getFormulaCode, formulaCode));
        if (formula == null) {
            throw new ServiceException(fieldLabel + "引用的公式编码不存在，请先到公式实验室维护");
        }
        if (!"0".equals(formula.getStatus())) {
            throw new ServiceException(fieldLabel + "引用的公式已停用，不能继续使用");
        }
        return formula;
    }

    /**
     * 构造删除阻断说明。
     */
    private String buildRemoveBlockingReason(CostVariableGovernanceCheckVo check, boolean hasFeeRelRef, boolean hasRuleConditionRef,
                                             boolean hasRuleQuantityRef, boolean hasFormulaRef, boolean hasPublishedVersionRef) {
        if (!hasRuleConditionRef && !hasRuleQuantityRef && !hasFormulaRef && !hasPublishedVersionRef) {
            if (hasFeeRelRef) {
                return String.format("当前变量未被规则、公式和版本占用；已有%d条费用变量关系将随删除自动清理", check.getFeeRelCount());
            }
            return "当前变量未被规则、公式和版本占用";
        }
        StringJoiner joiner = new StringJoiner("；");
        if (hasRuleConditionRef) {
            joiner.add(String.format("已有%d条规则条件引用", check.getRuleConditionCount()));
        }
        if (hasRuleQuantityRef) {
            joiner.add(String.format("已有%d条规则计量字段引用", check.getRuleQuantityCount()));
        }
        if (hasFormulaRef) {
            joiner.add(String.format("已有%d个公式引用", check.getFormulaRefCount()));
        }
        if (hasPublishedVersionRef) {
            joiner.add(String.format("已有%d个发布版本快照引用", check.getPublishedVersionCount()));
        }
        if (hasFeeRelRef) {
            joiner.add(String.format("另有%d条费用变量关系记录，不作为删除阻断", check.getFeeRelCount()));
        }
        return joiner.toString();
    }

    /**
     * 构造停用阻断说明。
     */
    private String buildDisableBlockingReason(CostVariableGovernanceCheckVo check, boolean hasPublishedVersionRef) {
        if (!hasPublishedVersionRef) {
            return "当前变量未进入发布快照，可安全停用";
        }
        return String.format("已有%d个发布版本快照引用当前变量", check.getPublishedVersionCount());
    }

    /**
     * 构造预览样例原始行。
     */
    private Map<String, Object> buildRawRow(String sourceCode, String sourceName, String businessDomain, Object value) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("sourceCode", sourceCode);
        row.put("sourceName", sourceName);
        row.put("businessDomain", businessDomain);
        row.put("value", value);
        return row;
    }

    private void validateVariableConfig(CostVariable variable) {
        variable.setSourceType(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getSourceType()), "INPUT"));
        validateSceneEnabled(variable.getSceneId(), "变量");
        validateVariableGroup(variable.getSceneId(), variable.getGroupId());
        validateDictValueExists(DICT_TYPE_VARIABLE_TYPE, variable.getVariableType(), "变量类型");
        validateDictValueExists(DICT_TYPE_SOURCE_TYPE, variable.getSourceType(), "来源类型");
        validateDictValueExists(DICT_TYPE_DATA_TYPE, variable.getDataType(), "数据类型");
        validateDictValueExists(DICT_TYPE_VARIABLE_STATUS, variable.getStatus(), "变量状态");
        variableSourceHandlerChain.validate(variable, variableSourceHandlerSupport);
    }

    private void normalizeVariableSourceFields(CostVariable variable) {
        variable.setSourceType(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getSourceType()), "INPUT"));
        variableSourceHandlerChain.normalize(variable);
    }

    private RemoteVariableConfig resolveRemoteVariableConfig(Map<String, Object> request, CostVariable variable) {
        CostVariable configSource = variable == null ? new CostVariable() : variable;
        if (variable == null) {
            configSource.setVariableCode(Objects.toString(request.get("variableCode"), "REMOTE_SAMPLE"));
            configSource.setSourceSystem(Objects.toString(request.get("sourceSystem"), ""));
            configSource.setRemoteApi(Objects.toString(request.get("remoteApi"), ""));
            configSource.setRequestMethod(Objects.toString(request.get("requestMethod"), "GET"));
            configSource.setContentType(Objects.toString(request.get("contentType"), MediaType.APPLICATION_JSON_VALUE));
            configSource.setQueryConfigJson(Objects.toString(request.get("queryConfigJson"), null));
            configSource.setRequestHeadersJson(Objects.toString(request.get("requestHeadersJson"), null));
            configSource.setBodyTemplateJson(Objects.toString(request.get("bodyTemplateJson"), null));
            configSource.setAuthType(Objects.toString(request.get("authType"), "NONE"));
            configSource.setAuthConfigJson(Objects.toString(request.get("authConfigJson"), null));
            configSource.setDataPath(Objects.toString(request.get("dataPath"), ""));
            configSource.setResponseConfigJson(Objects.toString(request.get("responseConfigJson"), null));
            configSource.setMappingConfigJson(Objects.toString(request.get("mappingConfigJson"), null));
            configSource.setPageConfigJson(Objects.toString(request.get("pageConfigJson"), null));
            configSource.setAdapterType(Objects.toString(request.get("adapterType"), RemoteVariableAccessPipeline.ADAPTER_STANDARD));
            configSource.setAdapterConfigJson(Objects.toString(request.get("adapterConfigJson"), null));
            configSource.setSyncMode(Objects.toString(request.get("syncMode"), "REALTIME"));
            configSource.setCachePolicy(Objects.toString(request.get("cachePolicy"), "MANUAL_REFRESH"));
            configSource.setFallbackPolicy(Objects.toString(request.get("fallbackPolicy"), "FAIL_FAST"));
            configSource.setSourceType("REMOTE");
        } else {
            applyRequestOverrides(configSource, request);
        }
        normalizeVariableSourceFields(configSource);
        validateRemoteVariableConfig(configSource);

        ObjectNode responseConfig = parseJsonObject(configSource.getResponseConfigJson(), "响应提取配置JSON");
        ObjectNode authConfig = parseJsonObject(configSource.getAuthConfigJson(), "鉴权配置JSON");
        normalizeAuthConfig(configSource.getAuthType(), authConfig);
        String listPath = firstNonBlank(textValue(responseConfig, "listPath"), configSource.getDataPath());
        if (responseConfig != null && StringUtils.isNotEmpty(listPath) && !responseConfig.has("listPath")) {
            responseConfig.put("listPath", listPath);
        }

        return new RemoteVariableConfig(
                firstNonBlank(configSource.getVariableCode(), "REMOTE_SAMPLE"),
                configSource.getRemoteApi(),
                configSource.getSourceSystem(),
                StringUtils.defaultIfEmpty(configSource.getRequestMethod(), "GET"),
                StringUtils.defaultIfEmpty(configSource.getContentType(), MediaType.APPLICATION_JSON_VALUE),
                parseJsonObject(configSource.getQueryConfigJson(), "查询参数配置JSON"),
                parseJsonObject(configSource.getRequestHeadersJson(), "请求头配置JSON"),
                parseJsonNode(configSource.getBodyTemplateJson(), "请求体模板"),
                StringUtils.defaultIfEmpty(configSource.getAuthType(), "NONE"),
                authConfig,
                configSource.getDataPath(),
                responseConfig,
                parseJsonObject(configSource.getMappingConfigJson(), "字段映射配置JSON"),
                parseJsonObject(configSource.getPageConfigJson(), "分页策略配置JSON"),
                StringUtils.defaultIfEmpty(configSource.getAdapterType(), RemoteVariableAccessPipeline.ADAPTER_STANDARD),
                parseJsonObject(configSource.getAdapterConfigJson(), "适配器配置JSON"),
                StringUtils.defaultIfEmpty(configSource.getSyncMode(), "REALTIME"),
                StringUtils.defaultIfEmpty(configSource.getCachePolicy(), "MANUAL_REFRESH"),
                StringUtils.defaultIfEmpty(configSource.getFallbackPolicy(), "FAIL_FAST")
        );
    }

    private void applyRequestOverrides(CostVariable variable, Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        if (request.containsKey("variableCode")) {
            variable.setVariableCode(Objects.toString(request.get("variableCode"), variable.getVariableCode()));
        }
        if (request.containsKey("sourceSystem")) {
            variable.setSourceSystem(Objects.toString(request.get("sourceSystem"), variable.getSourceSystem()));
        }
        if (request.containsKey("remoteApi")) {
            variable.setRemoteApi(Objects.toString(request.get("remoteApi"), variable.getRemoteApi()));
        }
        if (request.containsKey("requestMethod")) {
            variable.setRequestMethod(Objects.toString(request.get("requestMethod"), variable.getRequestMethod()));
        }
        if (request.containsKey("contentType")) {
            variable.setContentType(Objects.toString(request.get("contentType"), variable.getContentType()));
        }
        if (request.containsKey("queryConfigJson")) {
            variable.setQueryConfigJson(Objects.toString(request.get("queryConfigJson"), variable.getQueryConfigJson()));
        }
        if (request.containsKey("requestHeadersJson")) {
            variable.setRequestHeadersJson(Objects.toString(request.get("requestHeadersJson"), variable.getRequestHeadersJson()));
        }
        if (request.containsKey("bodyTemplateJson")) {
            variable.setBodyTemplateJson(Objects.toString(request.get("bodyTemplateJson"), variable.getBodyTemplateJson()));
        }
        if (request.containsKey("authType")) {
            variable.setAuthType(Objects.toString(request.get("authType"), variable.getAuthType()));
        }
        if (request.containsKey("authConfigJson")) {
            variable.setAuthConfigJson(Objects.toString(request.get("authConfigJson"), variable.getAuthConfigJson()));
        }
        if (request.containsKey("dataPath")) {
            variable.setDataPath(Objects.toString(request.get("dataPath"), variable.getDataPath()));
        }
        if (request.containsKey("responseConfigJson")) {
            variable.setResponseConfigJson(Objects.toString(request.get("responseConfigJson"), variable.getResponseConfigJson()));
        }
        if (request.containsKey("mappingConfigJson")) {
            variable.setMappingConfigJson(Objects.toString(request.get("mappingConfigJson"), variable.getMappingConfigJson()));
        }
        if (request.containsKey("pageConfigJson")) {
            variable.setPageConfigJson(Objects.toString(request.get("pageConfigJson"), variable.getPageConfigJson()));
        }
        if (request.containsKey("adapterType")) {
            variable.setAdapterType(Objects.toString(request.get("adapterType"), variable.getAdapterType()));
        }
        if (request.containsKey("adapterConfigJson")) {
            variable.setAdapterConfigJson(Objects.toString(request.get("adapterConfigJson"), variable.getAdapterConfigJson()));
        }
        if (request.containsKey("syncMode")) {
            variable.setSyncMode(Objects.toString(request.get("syncMode"), variable.getSyncMode()));
        }
        if (request.containsKey("cachePolicy")) {
            variable.setCachePolicy(Objects.toString(request.get("cachePolicy"), variable.getCachePolicy()));
        }
        if (request.containsKey("fallbackPolicy")) {
            variable.setFallbackPolicy(Objects.toString(request.get("fallbackPolicy"), variable.getFallbackPolicy()));
        }
    }

    private String textValue(JsonNode objectNode, String fieldName) {
        if (objectNode == null || objectNode.isNull() || StringUtils.isEmpty(fieldName)) {
            return "";
        }
        JsonNode node = objectNode.get(fieldName);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private ObjectNode parseJsonObject(String json, String fieldLabel) {
        JsonNode node = parseJsonNode(json, fieldLabel);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new ServiceException(fieldLabel + "必须是JSON对象");
        }
        return (ObjectNode) node;
    }

    private JsonNode parseJsonNode(String json, String fieldLabel) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ServiceException(fieldLabel + "格式不合法，请输入有效JSON")
                    .setDetailMessage(ex.getMessage());
        }
    }

    private void validateRemoteAuthConfig(String authType, String authConfigJson) {
        String normalizedAuthType = StringUtils.defaultIfEmpty(authType, "NONE");
        if ("NONE".equals(normalizedAuthType)) {
            return;
        }
        ObjectNode authConfig = parseJsonObject(authConfigJson, "鉴权配置JSON");
        if (authConfig == null || authConfig.isNull()) {
            throw new ServiceException("第三方接口变量已启用鉴权，必须配置鉴权配置JSON");
        }
        normalizeAuthConfig(normalizedAuthType, authConfig);
        switch (normalizedAuthType) {
            case "BASIC" -> {
                if (StringUtils.isEmpty(textValue(authConfig, "username"))) {
                    throw new ServiceException("Basic鉴权必须配置 username");
                }
            }
            case "BEARER" -> {
                String token = firstNonBlank(textValue(authConfig, "token"), textValue(authConfig, "accessToken"));
                if (StringUtils.isEmpty(token) || REMOTE_TOKEN_PLACEHOLDER.equals(token)) {
                    throw new ServiceException("Bearer鉴权必须配置 token");
                }
            }
            case "API_KEY" -> {
                if (StringUtils.isEmpty(textValue(authConfig, "keyValue"))) {
                    throw new ServiceException("API Key鉴权必须配置 keyValue");
                }
            }
            case "COOKIE" -> {
                boolean hasRawCookie = StringUtils.isNotEmpty(firstNonBlank(textValue(authConfig, "rawCookie"), textValue(authConfig, "cookie")));
                boolean hasCookiePair = StringUtils.isNotEmpty(textValue(authConfig, "cookieName"))
                        && StringUtils.isNotEmpty(textValue(authConfig, "cookieValue"));
                if (!hasRawCookie && !hasCookiePair) {
                    throw new ServiceException("Cookie鉴权必须配置 rawCookie 或 cookieName/cookieValue");
                }
            }
            default -> {
            }
        }
    }

    private void normalizeAuthConfig(String authType, ObjectNode authConfig) {
        if (authConfig == null || authConfig.isNull()) {
            return;
        }
        if ("BEARER".equalsIgnoreCase(StringUtils.defaultIfEmpty(authType, "NONE"))) {
            String token = firstNonBlank(textValue(authConfig, "token"), textValue(authConfig, "accessToken"));
            if (StringUtils.startsWithIgnoreCase(token, "Bearer ")) {
                String normalizedToken = StringUtils.trim(token.substring("Bearer ".length()));
                authConfig.put("token", normalizedToken);
                if (authConfig.has("accessToken")) {
                    authConfig.put("accessToken", normalizedToken);
                }
            }
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(StringUtils.trim(value))) {
                return StringUtils.trim(value);
            }
        }
        return "";
    }

    private String normalizeBearerPrefix(String prefix) {
        String normalized = StringUtils.trimToEmpty(prefix);
        if (StringUtils.isEmpty(normalized)) {
            return "";
        }
        return normalized.endsWith(" ") ? normalized : normalized + " ";
    }

    /**
     * 空值转0。
     */
    private long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 校验场景必须存在且处于可维护状态。
     */
    private void validateSceneEnabled(Long sceneId, String objectName) {
        if (sceneId == null) {
            throw new ServiceException(objectName + "所属场景不能为空");
        }
        CostScene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new ServiceException(objectName + "所属场景不存在");
        }
        if (!"0".equals(scene.getStatus())) {
            throw new ServiceException(objectName + "所属场景不是正常状态，当前不允许继续维护");
        }
    }

    /**
     * 校验变量分组合法性。
     */
    private void validateVariableGroup(Long sceneId, Long groupId) {
        if (groupId == null) {
            return;
        }
        CostVariableGroup group = variableGroupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException("变量分组不存在");
        }
        if (!Objects.equals(sceneId, group.getSceneId())) {
            throw new ServiceException("变量分组不属于当前场景");
        }
        if (!"0".equals(group.getStatus())) {
            throw new ServiceException("变量分组不是正常状态，当前不允许继续维护");
        }
    }

    /**
     * 校验字典值是否存在且启用。
     */
    private void validateDictValueExists(String dictType, String dictValue, String fieldLabel) {
        if (StringUtils.isEmpty(dictValue)) {
            return;
        }
        if (!dictionaryProvider.containsValue(dictType, dictValue)) {
            throw new ServiceException(String.format("%s取值无效，请从系统字典 %s 中选择合法值：%s", fieldLabel, dictType, dictValue));
        }
    }

    /**
     * 校验字典来源变量引用的字典类型必须存在。
     */
    private void validateDictTypeExists(String dictType) {
        if (StringUtils.isEmpty(dictType)) {
            throw new ServiceException("字典来源变量必须指定字典类型");
        }
        if (!dictionaryProvider.containsType(dictType)) {
            throw new ServiceException("字典来源变量引用的字典类型不存在：" + dictType);
        }
    }

    /**
     * 校验第三方来源变量的正式配置项。
     */
    private void validateRemoteVariableConfig(CostVariable variable) {
        if (StringUtils.isEmpty(variable.getRemoteApi())) {
            throw new ServiceException("第三方接口变量必须配置接口地址");
        }
        if (!variable.getRemoteApi().startsWith("http://") && !variable.getRemoteApi().startsWith("https://")) {
            throw new ServiceException("第三方接口变量的接口地址必须以 http:// 或 https:// 开头");
        }
        if (StringUtils.isEmpty(variable.getSourceSystem())) {
            throw new ServiceException("第三方接口变量必须配置来源系统标识");
        }
        if (!SUPPORTED_REMOTE_METHODS.contains(StringUtils.defaultIfEmpty(variable.getRequestMethod(), "GET").toUpperCase(Locale.ROOT))) {
            throw new ServiceException("第三方接口变量的请求方式仅支持 GET、POST、PUT、DELETE");
        }
        if (!SUPPORTED_REMOTE_ADAPTERS.contains(StringUtils.defaultIfEmpty(variable.getAdapterType(), RemoteVariableAccessPipeline.ADAPTER_STANDARD))) {
            throw new ServiceException("第三方接口变量的适配器类型无效");
        }
        validateDictValueExists(DICT_TYPE_AUTH_TYPE, StringUtils.defaultIfEmpty(variable.getAuthType(), "NONE"), "鉴权方式");
        validateDictValueExists(DICT_TYPE_SYNC_MODE, StringUtils.defaultIfEmpty(variable.getSyncMode(), "REALTIME"), "同步方式");
        validateDictValueExists(DICT_TYPE_CACHE_POLICY, StringUtils.defaultIfEmpty(variable.getCachePolicy(), "MANUAL_REFRESH"), "缓存策略");
        validateDictValueExists(DICT_TYPE_FALLBACK_POLICY, StringUtils.defaultIfEmpty(variable.getFallbackPolicy(), "FAIL_FAST"), "失败兜底策略");
        parseJsonObject(variable.getQueryConfigJson(), "查询参数配置JSON");
        parseJsonObject(variable.getRequestHeadersJson(), "请求头配置JSON");
        parseJsonNode(variable.getAuthConfigJson(), "鉴权配置JSON");
        parseJsonNode(variable.getBodyTemplateJson(), "请求体模板");
        parseJsonObject(variable.getResponseConfigJson(), "响应提取配置JSON");
        parseJsonObject(variable.getMappingConfigJson(), "字段映射配置JSON");
        parseJsonObject(variable.getPageConfigJson(), "分页策略配置JSON");
        parseJsonObject(variable.getAdapterConfigJson(), "适配器配置JSON");
        validateRemoteAuthConfig(variable.getAuthType(), variable.getAuthConfigJson());
    }

    /**
     * 按场景编码缓存查询场景。
     */
    private CostScene resolveSceneByCode(String sceneCode, Map<String, CostScene> sceneCache) {
        if (StringUtils.isEmpty(sceneCode)) {
            return null;
        }
        return sceneCache.computeIfAbsent(sceneCode, key -> sceneMapper.selectOne(Wrappers.<CostScene>lambdaQuery()
                .eq(CostScene::getSceneCode, key)
                .last("limit 1")));
    }

    /**
     * 按场景和分组编码查询变量分组。
     */
    private CostVariableGroup resolveGroupByCode(Long sceneId, String groupCode, Map<String, CostVariableGroup> groupCache) {
        if (sceneId == null || StringUtils.isEmpty(groupCode)) {
            return null;
        }
        String cacheKey = sceneId + "::" + groupCode;
        return groupCache.computeIfAbsent(cacheKey, key -> variableGroupMapper.selectOne(Wrappers.<CostVariableGroup>lambdaQuery()
                .eq(CostVariableGroup::getSceneId, sceneId)
                .eq(CostVariableGroup::getGroupCode, groupCode)
                .last("limit 1")));
    }

    /**
     * 查询同场景下是否已存在同编码变量。
     */
    private CostVariable selectExistingVariable(Long sceneId, String variableCode) {
        if (sceneId == null || StringUtils.isEmpty(variableCode)) {
            return null;
        }
        return variableMapper.selectOne(Wrappers.<CostVariable>lambdaQuery()
                .eq(CostVariable::getSceneId, sceneId)
                .eq(CostVariable::getVariableCode, variableCode)
                .last("limit 1"));
    }

    /**
     * 构造共享模板对象。
     */
    private CostVariableTemplateVo buildTemplate(String templateCode, String templateName, String description, String namespaceHint,
                                                 List<Map<String, Object>> items) {
        CostVariableTemplateVo template = new CostVariableTemplateVo();
        template.setTemplateCode(templateCode);
        template.setTemplateName(templateName);
        template.setDescription(description);
        template.setNamespaceHint(namespaceHint);
        template.setVariableCount(items.size());
        template.setItems(items);
        return template;
    }

    /**
     * 汇总共享模板的复用治理摘要。
     */
    private void populateTemplateGovernanceSummary(List<CostVariableTemplateVo> templates) {
        if (templates == null || templates.isEmpty()) {
            return;
        }

        LinkedHashMap<String, Set<String>> templateVariableCodeMap = new LinkedHashMap<>();
        LinkedHashSet<String> allVariableCodes = new LinkedHashSet<>();
        for (CostVariableTemplateVo template : templates) {
            LinkedHashSet<String> variableCodes = new LinkedHashSet<>();
            for (Map<String, Object> item : template.getItems()) {
                String variableCode = Objects.toString(item.get("variableCode"), "");
                if (StringUtils.isNotEmpty(variableCode)) {
                    variableCodes.add(variableCode);
                    allVariableCodes.add(variableCode);
                }
            }
            templateVariableCodeMap.put(template.getTemplateCode(), variableCodes);
        }
        if (allVariableCodes.isEmpty()) {
            return;
        }

        List<CostVariable> matchedVariables = variableMapper.selectList(Wrappers.<CostVariable>lambdaQuery()
                .in(CostVariable::getVariableCode, allVariableCodes));
        if (matchedVariables == null || matchedVariables.isEmpty()) {
            return;
        }

        LinkedHashSet<Long> sceneIds = new LinkedHashSet<>();
        for (CostVariable variable : matchedVariables) {
            if (variable.getSceneId() != null) {
                sceneIds.add(variable.getSceneId());
            }
        }

        Map<Long, CostScene> sceneMap = new HashMap<>();
        if (!sceneIds.isEmpty()) {
            for (CostScene scene : sceneMapper.selectBatchIds(sceneIds)) {
                sceneMap.put(scene.getSceneId(), scene);
            }
        }

        for (CostVariableTemplateVo template : templates) {
            Set<String> templateCodes = templateVariableCodeMap.getOrDefault(template.getTemplateCode(), Collections.emptySet());
            if (templateCodes.isEmpty()) {
                continue;
            }

            LinkedHashMap<Long, Set<String>> sceneCoverage = new LinkedHashMap<>();
            LinkedHashMap<Long, Integer> sceneHitCount = new LinkedHashMap<>();
            LinkedHashMap<Long, Date> sceneLatestAppliedTime = new LinkedHashMap<>();
            int matchedVariableCount = 0;
            Date latestAppliedTime = null;
            Long latestSceneId = null;

            for (CostVariable variable : matchedVariables) {
                if (!templateCodes.contains(variable.getVariableCode())) {
                    continue;
                }
                matchedVariableCount++;
                if (variable.getSceneId() != null) {
                    sceneCoverage.computeIfAbsent(variable.getSceneId(), key -> new LinkedHashSet<>()).add(variable.getVariableCode());
                    sceneHitCount.merge(variable.getSceneId(), 1, Integer::sum);
                }
                Date appliedTime = variable.getUpdateTime() != null ? variable.getUpdateTime() : variable.getCreateTime();
                if (variable.getSceneId() != null && appliedTime != null) {
                    Date latestSceneAppliedTime = sceneLatestAppliedTime.get(variable.getSceneId());
                    if (latestSceneAppliedTime == null || appliedTime.after(latestSceneAppliedTime)) {
                        sceneLatestAppliedTime.put(variable.getSceneId(), appliedTime);
                    }
                }
                if (appliedTime != null && (latestAppliedTime == null || appliedTime.after(latestAppliedTime))) {
                    latestAppliedTime = appliedTime;
                    latestSceneId = variable.getSceneId();
                }
            }

            int fullyAppliedSceneCount = 0;
            for (Set<String> coveredCodes : sceneCoverage.values()) {
                if (coveredCodes.containsAll(templateCodes)) {
                    fullyAppliedSceneCount++;
                }
            }

            List<Map.Entry<Long, Integer>> rankedScenes = new ArrayList<>(sceneHitCount.entrySet());
            rankedScenes.sort((left, right) -> {
                int compare = Integer.compare(right.getValue(), left.getValue());
                if (compare != 0) {
                    return compare;
                }
                return Long.compare(left.getKey(), right.getKey());
            });

            List<String> recentSceneNames = new ArrayList<>();
            List<Map<String, Object>> sceneSummaries = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : rankedScenes) {
                String sceneLabel = buildSceneLabel(sceneMap.get(entry.getKey()), entry.getKey());
                if (StringUtils.isNotEmpty(sceneLabel) && recentSceneNames.size() < 3) {
                    recentSceneNames.add(sceneLabel);
                }
                LinkedHashMap<String, Object> sceneSummary = new LinkedHashMap<>();
                sceneSummary.put("sceneId", entry.getKey());
                sceneSummary.put("sceneLabel", sceneLabel);
                sceneSummary.put("matchedCount", entry.getValue());
                sceneSummary.put("coverageCount", sceneCoverage.getOrDefault(entry.getKey(), Collections.emptySet()).size());
                sceneSummary.put("templateVariableCount", templateCodes.size());
                sceneSummary.put("coverageRate", templateCodes.isEmpty()
                        ? 0
                        : Math.round(sceneCoverage.getOrDefault(entry.getKey(), Collections.emptySet()).size() * 1000.0 / templateCodes.size()) / 10.0);
                sceneSummary.put("latestAppliedTime", sceneLatestAppliedTime.get(entry.getKey()));
                sceneSummaries.add(sceneSummary);
            }

            template.setMatchedVariableCount(matchedVariableCount);
            template.setAppliedSceneCount(sceneCoverage.size());
            template.setFullyAppliedSceneCount(fullyAppliedSceneCount);
            template.setRecentSceneNames(recentSceneNames);
            template.setSceneSummaries(sceneSummaries);
            template.setLatestAppliedTime(latestAppliedTime);
            if (latestSceneId != null) {
                template.setLatestSceneName(buildSceneLabel(sceneMap.get(latestSceneId), latestSceneId));
            }
        }
    }

    private String buildSceneLabel(CostScene scene, Long sceneId) {
        if (scene == null) {
            return sceneId == null ? "" : "场景#" + sceneId;
        }
        String sceneCode = StringUtils.defaultString(scene.getSceneCode());
        String sceneName = StringUtils.defaultString(scene.getSceneName());
        if (StringUtils.isNotEmpty(sceneCode) && StringUtils.isNotEmpty(sceneName)) {
            return sceneCode + " / " + sceneName;
        }
        return StringUtils.defaultIfEmpty(sceneName, sceneCode);
    }

    /**
     * 根据模板条目构造变量。
     */
    private CostVariable buildVariableFromTemplateItem(Long sceneId, Long groupId, Map<String, Object> item, String operName) {
        CostVariable variable = new CostVariable();
        variable.setSceneId(sceneId);
        variable.setGroupId(groupId);
        variable.setVariableCode(Objects.toString(item.get("variableCode"), ""));
        variable.setVariableName(Objects.toString(item.get("variableName"), ""));
        variable.setVariableType(Objects.toString(item.get("variableType"), "TEXT"));
        variable.setSourceType(Objects.toString(item.get("sourceType"), "INPUT"));
        variable.setDataPath(Objects.toString(item.get("dataPath"), variable.getVariableCode()));
        variable.setDataType(Objects.toString(item.get("dataType"), "STRING"));
        variable.setDefaultValue(Objects.toString(item.get("defaultValue"), ""));
        variable.setPrecisionScale(parseInteger(item.get("precisionScale"), 2));
        variable.setStatus("0");
        variable.setSortNo(parseInteger(item.get("sortNo"), 10));
        variable.setRemark("来自共享模板：" + Objects.toString(item.get("templateName"), ""));
        variable.setCreateBy(operName);
        return variable;
    }

    /**
     * 根据模板编码找到变量条目。
     */
    private List<Map<String, Object>> findTemplateItems(String templateCode) {
        if ("STORAGE_LEASE_BASE".equals(templateCode)) {
            return buildStorageLeaseTemplateItems();
        }
        if ("ZX_OPERATION_BASE".equals(templateCode)) {
            return buildZxTemplateItems();
        }
        if ("SG_OPERATION_BASE".equals(templateCode)) {
            return buildSgTemplateItems();
        }
        return Collections.emptyList();
    }

    /**
     * 仓储保管共享模板。
     */
    private List<Map<String, Object>> buildStorageLeaseTemplateItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(templateItem("STORAGE_LEASE_BASE", "contract.customerLevel", "客户等级", "TEXT", "INPUT", "STRING", null, 10));
        items.add(templateItem("STORAGE_LEASE_BASE", "storage.days", "计费天数", "NUMBER", "INPUT", "NUMBER", "0", 20));
        items.add(templateItem("STORAGE_LEASE_BASE", "storage.area", "计费面积", "NUMBER", "INPUT", "NUMBER", "0", 30));
        items.add(templateItem("STORAGE_LEASE_BASE", "storage.locationType", "库区类型", "TEXT", "INPUT", "STRING", null, 40));
        return items;
    }

    /**
     * 装卸/清仓共享模板。
     */
    private List<Map<String, Object>> buildZxTemplateItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(templateItem("ZX_OPERATION_BASE", "zx.tradeType", "装卸业务类型", "TEXT", "INPUT", "STRING", null, 10));
        items.add(templateItem("ZX_OPERATION_BASE", "zx.qty", "装卸件数", "NUMBER", "INPUT", "NUMBER", "0", 20));
        items.add(templateItem("ZX_OPERATION_BASE", "zx.weight", "装卸重量", "NUMBER", "INPUT", "NUMBER", "0", 30));
        items.add(templateItem("ZX_OPERATION_BASE", "zx.amountBase", "装卸计费基数", "NUMBER", "INPUT", "NUMBER", "0", 40));
        return items;
    }

    /**
     * 疏港共享模板。
     */
    private List<Map<String, Object>> buildSgTemplateItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(templateItem("SG_OPERATION_BASE", "sg.tradeType", "疏港业务类型", "TEXT", "INPUT", "STRING", null, 10));
        items.add(templateItem("SG_OPERATION_BASE", "sg.qty", "疏港件数", "NUMBER", "INPUT", "NUMBER", "0", 20));
        items.add(templateItem("SG_OPERATION_BASE", "sg.weight", "疏港重量", "NUMBER", "INPUT", "NUMBER", "0", 30));
        items.add(templateItem("SG_OPERATION_BASE", "sg.distance", "疏港里程", "NUMBER", "INPUT", "NUMBER", "0", 40));
        return items;
    }

    /**
     * 模板条目工厂。
     */
    private Map<String, Object> templateItem(String templateName, String variableCode, String variableName, String variableType,
                                             String sourceType, String dataType, String defaultValue, int sortNo) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("templateName", templateName);
        item.put("variableCode", variableCode);
        item.put("variableName", variableName);
        item.put("variableType", variableType);
        item.put("sourceType", sourceType);
        item.put("dataPath", variableCode);
        item.put("dataType", dataType);
        item.put("defaultValue", defaultValue);
        item.put("precisionScale", 2);
        item.put("sortNo", sortNo);
        return item;
    }

    /**
     * 安全解析 Long。
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 安全解析 Integer。
     */
    private Integer parseInteger(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
