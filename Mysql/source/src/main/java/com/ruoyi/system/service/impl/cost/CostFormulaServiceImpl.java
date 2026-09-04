package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostFormula;
import com.ruoyi.system.domain.cost.CostFormulaVersion;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.cost.bo.CostFormulaTestBo;
import com.ruoyi.system.domain.vo.CostExpressionAnalysisVo;
import com.ruoyi.system.domain.vo.CostFormulaGovernanceCheckVo;
import com.ruoyi.system.mapper.cost.CostFormulaMapper;
import com.ruoyi.system.mapper.cost.CostFormulaVersionMapper;
import com.ruoyi.system.mapper.cost.CostSceneMapper;
import com.ruoyi.system.service.cost.ICostExpressionService;
import com.ruoyi.system.service.cost.ICostFormulaService;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.*;

/**
 * 公式实验室服务实现。
 *
 * <p>线程七在这里建立“公式主数据 -> 测试验证 -> 被变量/规则引用”的治理主线，
 * 先把公式作为独立资产做对，再在后续线程逐步扩大引用范围。</p>
 *
 * @author HwFan
 */
@Service
public class CostFormulaServiceImpl implements ICostFormulaService {
    private static final String ASSET_TYPE_FORMULA = "FORMULA";
    private static final String ASSET_TYPE_TEMPLATE = "TEMPLATE";
    private static final String DICT_TYPE_FORMULA_STATUS = "cost_formula_status";
    private static final String DICT_TYPE_RETURN_TYPE = "cost_formula_return_type";
    private static final String DEFAULT_NAMESPACE_SCOPE = "V,C,I,F,T";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CostFormulaMapper formulaMapper;

    @Autowired
    private CostSceneMapper sceneMapper;

    @Autowired
    private CostFormulaVersionMapper formulaVersionMapper;

    @Autowired
    private CostDictionaryProvider dictionaryProvider;

    @Autowired
    private ICostExpressionService expressionService;

    @Autowired
    private CostGovernanceImpactSupport governanceImpactSupport;

    @Override
    public List<CostFormula> selectFormulaList(CostFormula formula) {
        return formulaMapper.selectFormulaList(formula);
    }

    @Override
    public CostFormula selectFormulaById(Long formulaId) {
        return formulaMapper.selectById(formulaId);
    }

    @Override
    public List<CostFormula> selectFormulaOptions(CostFormula formula) {
        formula.setAssetType(ASSET_TYPE_FORMULA);
        return formulaMapper.selectFormulaOptions(formula);
    }

    @Override
    public List<CostFormula> selectTemplateOptions(CostFormula formula) {
        return formulaMapper.selectTemplateOptions(formula);
    }

    @Override
    public Map<String, Object> selectFormulaStats(CostFormula formula) {
        Map<String, Object> stats = formulaMapper.selectFormulaStats(formula);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("formulaCount", 0);
        result.put("enabledFormulaCount", 0);
        result.put("variableRefCount", 0);
        result.put("ruleRefCount", 0);
        if (stats == null) {
            return result;
        }
        stats.forEach((key, value) -> result.put(key, value == null ? 0 : value));
        return result;
    }

    @Override
    public CostFormulaGovernanceCheckVo selectFormulaGovernanceCheck(Long formulaId) {
        CostFormulaGovernanceCheckVo check = formulaMapper.selectFormulaGovernanceCheck(formulaId);
        if (check == null) {
            return null;
        }
        long variableRefCount = check.getVariableRefCount() == null ? 0 : check.getVariableRefCount();
        long ruleRefCount = check.getRuleRefCount() == null ? 0 : check.getRuleRefCount();
        long publishedVersionCount = check.getPublishedVersionCount() == null ? 0 : check.getPublishedVersionCount();
        check.setVariableRefCount(variableRefCount);
        check.setRuleRefCount(ruleRefCount);
        check.setPublishedVersionCount(publishedVersionCount);
        check.setCanDelete(variableRefCount == 0 && ruleRefCount == 0 && publishedVersionCount == 0);
        check.setCanDisable(publishedVersionCount == 0);
        check.setRemoveBlockingReason(check.getCanDelete() ? "" : buildRemoveBlockingReason(variableRefCount, ruleRefCount, publishedVersionCount));
        check.setDisableBlockingReason(check.getCanDisable() ? "" : "当前公式已进入发布版本快照，请先替换并发布新版本后再停用。");
        check.setImpactItems(governanceImpactSupport.buildFormulaImpacts(check));
        return check;
    }

    @Override
    public boolean checkFormulaCodeUnique(CostFormula formula) {
        Long formulaId = formula.getFormulaId() == null ? -1L : formula.getFormulaId();
        Long count = formulaMapper.selectCount(Wrappers.<CostFormula>lambdaQuery()
                .eq(CostFormula::getSceneId, formula.getSceneId())
                .eq(CostFormula::getFormulaCode, formula.getFormulaCode())
                .ne(formulaId != -1L, CostFormula::getFormulaId, formulaId));
        return count == null || count == 0 ? UserConstants.UNIQUE : UserConstants.NOT_UNIQUE;
    }

    @Override
    public int insertFormula(CostFormula formula) {
        validateFormula(formula);
        fillDefaultFields(formula);
        int rows = formulaMapper.insert(formula);
        saveFormulaVersion(formula, "CREATE");
        return rows;
    }

    @Override
    public int updateFormula(CostFormula formula) {
        return updateFormulaInternal(formula, "UPDATE");
    }

    @Override
    public int deleteFormulaByIds(Long[] formulaIds) {
        for (Long formulaId : formulaIds) {
            CostFormulaGovernanceCheckVo check = selectFormulaGovernanceCheck(formulaId);
            if (check != null && !Boolean.TRUE.equals(check.getCanDelete())) {
                throw new ServiceException(String.format("%s 不能删除：%s", check.getFormulaName(), check.getRemoveBlockingReason()));
            }
        }
        return formulaMapper.deleteBatchIds(Arrays.asList(formulaIds));
    }

    @Override
    public List<CostFormulaVersion> selectFormulaVersionList(Long formulaId) {
        return formulaVersionMapper.selectFormulaVersionList(formulaId);
    }

    @Override
    public CostFormula selectFormulaVersionDetail(Long versionId) {
        CostFormulaVersion version = formulaVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException("当前版本不存在，请刷新后重试");
        }
        CostFormula formula = parseFormulaSnapshot(version.getSnapshotJson());
        formula.setFormulaId(version.getFormulaId());
        formula.setSceneId(version.getSceneId());
        formula.setFormulaCode(version.getFormulaCode());
        formula.setFormulaName(version.getFormulaName());
        formula.setAssetType(version.getAssetType());
        formula.setBusinessFormula(version.getBusinessFormula());
        formula.setFormulaExpr(version.getFormulaExpr());
        formula.setWorkbenchMode(version.getWorkbenchMode());
        formula.setWorkbenchPattern(version.getWorkbenchPattern());
        formula.setTemplateCode(version.getTemplateCode());
        formula.setWorkbenchConfigJson(version.getWorkbenchConfigJson());
        formula.setCurrentVersionNo(version.getVersionNo());
        return formula;
    }

    @Override
    public int rollbackFormulaVersion(Long versionId, String operator) {
        CostFormulaVersion version = formulaVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException("当前版本不存在，请刷新后重试");
        }
        CostFormula current = formulaMapper.selectById(version.getFormulaId());
        if (current == null) {
            throw new ServiceException("当前公式不存在，无法执行版本回滚");
        }
        CostFormula rollbackFormula = parseFormulaSnapshot(version.getSnapshotJson());
        rollbackFormula.setFormulaId(current.getFormulaId());
        rollbackFormula.setSceneId(current.getSceneId());
        rollbackFormula.setCreateBy(current.getCreateBy());
        rollbackFormula.setCreateTime(current.getCreateTime());
        rollbackFormula.setUpdateBy(operator);
        rollbackFormula.setUpdateTime(DateUtils.getNowDate());
        return updateFormulaInternal(rollbackFormula, "ROLLBACK");
    }

    @Override
    public Map<String, Object> testFormula(CostFormulaTestBo bo, String operator) {
        if (bo == null) {
            throw new ServiceException("测试请求不能为空");
        }
        CostFormula formula = resolveFormulaForTest(bo);
        Map<String, Object> input = parseInputContext(bo.getInputJson());
        CostExpressionAnalysisVo analysis = expressionService.analyzeExpression(formula.getFormulaExpr(), formula.getNamespaceScope());
        Object result = expressionService.evaluate(formula.getFormulaExpr(), input);

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("formulaCode", formula.getFormulaCode());
        response.put("formulaName", formula.getFormulaName());
        response.put("businessFormula", formula.getBusinessFormula());
        response.put("formulaExpr", formula.getFormulaExpr());
        response.put("input", input);
        response.put("analysis", analysis);
        response.put("result", result);

        if (formula.getFormulaId() != null) {
            formula.setSampleResultJson(writeJson(result));
            formula.setLastTestTime(DateUtils.getNowDate());
            formula.setUpdateBy(operator);
            formulaMapper.updateById(formula);
        }
        return response;
    }

    private Map<String, Object> buildWorkbenchConfigPayload(CostFormula formula, Map<String, Object> config) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("mode", formula.getWorkbenchMode());
        normalized.put("pattern", formula.getWorkbenchPattern());
        normalized.put("templateCode", formula.getTemplateCode());
        normalized.put("conditionLogic", StringUtils.defaultIfEmpty(stringValue(valueOf(config, "conditionLogic")), "AND"));
        normalized.put("conditions", listValue(valueOf(config, "conditions")));
        normalized.put("trueResultValue", stringValue(valueOf(config, "trueResultValue")));
        normalized.put("falseResultValue", stringValue(valueOf(config, "falseResultValue")));
        normalized.put("rangeVariableCode", stringValue(valueOf(config, "rangeVariableCode")));
        normalized.put("ranges", listValue(valueOf(config, "ranges")));
        normalized.put("defaultResultValue", stringValue(valueOf(config, "defaultResultValue")));
        normalized.put("businessFormula", StringUtils.defaultIfEmpty(stringValue(valueOf(config, "businessFormula")), formula.getBusinessFormula()));
        normalized.put("formulaExpr", StringUtils.defaultIfEmpty(stringValue(valueOf(config, "formulaExpr")), formula.getFormulaExpr()));
        return normalized;
    }

    /**
     * 校验公式配置。
     */
    private void validateFormula(CostFormula formula) {
        validateSceneEnabled(formula.getSceneId());
        validateDictValue(DICT_TYPE_FORMULA_STATUS, formula.getStatus(), "公式状态");
        validateDictValue(DICT_TYPE_RETURN_TYPE, formula.getReturnType(), "返回类型");
        validateAssetType(formula.getAssetType());
        formula.setFormulaCode(StringUtils.trim(formula.getFormulaCode()));
        formula.setFormulaName(StringUtils.trim(formula.getFormulaName()));
        formula.setFormulaDesc(StringUtils.defaultString(formula.getFormulaDesc()));
        formula.setBusinessFormula(StringUtils.defaultString(formula.getBusinessFormula()));
        formula.setNamespaceScope(normalizeNamespaceScope(formula.getNamespaceScope()));
        expressionService.validateExpression(formula.getFormulaExpr(), formula.getNamespaceScope());
        validateTestCaseJson(formula.getTestCaseJson());
        normalizeWorkbenchConfig(formula);
    }

    /**
     * 停用前校验。
     */
    private void validateDisableBeforeUpdate(CostFormula formula) {
        if (formula.getFormulaId() == null) {
            return;
        }
        CostFormula current = formulaMapper.selectById(formula.getFormulaId());
        if (current == null) {
            throw new ServiceException("当前公式不存在，请刷新后重试");
        }
        if (STATUS_ENABLED.equals(current.getStatus()) && !STATUS_ENABLED.equals(formula.getStatus())) {
            CostFormulaGovernanceCheckVo check = selectFormulaGovernanceCheck(formula.getFormulaId());
            if (check != null && !Boolean.TRUE.equals(check.getCanDisable())) {
                throw new ServiceException(check.getDisableBlockingReason());
            }
        }
    }

    /**
     * 回填默认值。
     */
    private void fillDefaultFields(CostFormula formula) {
        if (formula.getSortNo() == null) {
            formula.setSortNo(10);
        }
        if (StringUtils.isEmpty(formula.getStatus())) {
            formula.setStatus(STATUS_ENABLED);
        }
        if (StringUtils.isEmpty(formula.getReturnType())) {
            formula.setReturnType("NUMBER");
        }
        if (StringUtils.isEmpty(formula.getAssetType())) {
            formula.setAssetType(ASSET_TYPE_FORMULA);
        }
        if (StringUtils.isEmpty(formula.getWorkbenchMode())) {
            formula.setWorkbenchMode("GUIDED");
        }
        if (StringUtils.isEmpty(formula.getWorkbenchPattern())) {
            formula.setWorkbenchPattern("IF_ELSE");
        }
    }

    /**
     * 规范化工作台点选配置，保证工作台可以按原结构重新回填。
     */
    private void normalizeWorkbenchConfig(CostFormula formula) {
        String mode = StringUtils.defaultIfEmpty(StringUtils.trim(formula.getWorkbenchMode()), "GUIDED");
        String pattern = StringUtils.defaultIfEmpty(StringUtils.trim(formula.getWorkbenchPattern()), "IF_ELSE");
        formula.setWorkbenchMode(mode);
        formula.setWorkbenchPattern(pattern);
        formula.setTemplateCode(StringUtils.trimToEmpty(formula.getTemplateCode()));
        String configJson = StringUtils.trimToEmpty(formula.getWorkbenchConfigJson());
        if (StringUtils.isEmpty(configJson)) {
            formula.setWorkbenchConfigJson(writeJson(buildWorkbenchConfigPayload(formula, null)));
            return;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {
            });
            formula.setWorkbenchConfigJson(writeJson(buildWorkbenchConfigPayload(formula, config)));
        } catch (Exception ex) {
            throw new ServiceException("工作台配置格式不正确，请检查后重试");
        }
    }

    /**
     * 校验场景状态。
     */
    private void validateSceneEnabled(Long sceneId) {
        CostScene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new ServiceException("所属场景不存在，请刷新后重试");
        }
        if (!STATUS_ENABLED.equals(scene.getStatus())) {
            throw new ServiceException("所属场景已停用，不能维护公式");
        }
    }

    /**
     * 校验字典值合法性。
     */
    private void validateDictValue(String dictType, String dictValue, String label) {
        if (StringUtils.isEmpty(dictValue)) {
            throw new ServiceException(label + "不能为空");
        }
        if (!dictionaryProvider.containsValue(dictType, dictValue)) {
            throw new ServiceException(label + "不在系统字典范围内");
        }
    }

    /**
     * 校验资产类型。
     */
    private void validateAssetType(String assetType) {
        if (StringUtils.isEmpty(assetType)) {
            return;
        }
        if (!ASSET_TYPE_FORMULA.equals(assetType) && !ASSET_TYPE_TEMPLATE.equals(assetType)) {
            throw new ServiceException("资产类型不在系统允许范围内");
        }
    }

    /**
     * 校验测试样例 JSON。
     */
    private void validateTestCaseJson(String testCaseJson) {
        if (StringUtils.isEmpty(testCaseJson)) {
            return;
        }
        try {
            objectMapper.readValue(testCaseJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new ServiceException("公式测试样例必须是 JSON 对象");
        }
    }

    /**
     * 解析测试上下文。
     */
    private Map<String, Object> parseInputContext(String inputJson) {
        if (StringUtils.isEmpty(inputJson)) {
            LinkedHashMap<String, Object> empty = new LinkedHashMap<>();
            empty.put("I", new LinkedHashMap<>());
            return empty;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(inputJson, new TypeReference<Map<String, Object>>() {
            });
            if (raw.containsKey("V") || raw.containsKey("C") || raw.containsKey("I") || raw.containsKey("F") || raw.containsKey("T")) {
                return raw;
            }
            LinkedHashMap<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("I", raw);
            return wrapped;
        } catch (Exception e) {
            throw new ServiceException("公式测试输入必须是 JSON 对象");
        }
    }

    /**
     * 解析测试时使用的公式。
     */
    private CostFormula resolveFormulaForTest(CostFormulaTestBo bo) {
        if (bo.getFormulaId() != null) {
            CostFormula formula = formulaMapper.selectById(bo.getFormulaId());
            if (formula == null) {
                throw new ServiceException("待测试公式不存在");
            }
            return formula;
        }
        if (bo.getSceneId() != null && StringUtils.isNotEmpty(bo.getFormulaCode())) {
            CostFormula formula = formulaMapper.selectOne(Wrappers.<CostFormula>lambdaQuery()
                    .eq(CostFormula::getSceneId, bo.getSceneId())
                    .eq(CostFormula::getFormulaCode, bo.getFormulaCode()));
            if (formula == null) {
                throw new ServiceException("公式编码不存在，请检查后重试");
            }
            return formula;
        }
        if (StringUtils.isEmpty(bo.getFormulaExpr())) {
            throw new ServiceException("测试时必须提供公式编码或表达式");
        }
        CostFormula temporary = new CostFormula();
        temporary.setFormulaCode(StringUtils.defaultIfEmpty(bo.getFormulaCode(), "TEMP_FORMULA"));
        temporary.setFormulaName("临时测试公式");
        temporary.setBusinessFormula("临时测试公式");
        temporary.setFormulaExpr(bo.getFormulaExpr());
        temporary.setNamespaceScope(normalizeNamespaceScope(bo.getNamespaceScope()));
        expressionService.validateExpression(temporary.getFormulaExpr(), temporary.getNamespaceScope());
        return temporary;
    }

    private String normalizeNamespaceScope(String namespaceScope) {
        return StringUtils.defaultIfEmpty(StringUtils.trim(namespaceScope), DEFAULT_NAMESPACE_SCOPE);
    }

    /**
     * 删除阻断说明。
     */
    private String buildRemoveBlockingReason(long variableRefCount, long ruleRefCount, long publishedVersionCount) {
        StringBuilder builder = new StringBuilder("当前公式已被");
        boolean appended = false;
        if (variableRefCount > 0) {
            builder.append(variableRefCount).append("个变量引用");
            appended = true;
        }
        if (ruleRefCount > 0) {
            if (appended) {
                builder.append("、");
            }
            builder.append(ruleRefCount).append("条规则引用");
            appended = true;
        }
        if (publishedVersionCount > 0) {
            if (appended) {
                builder.append("、");
            }
            builder.append(publishedVersionCount).append("个发布版本快照引用");
        }
        builder.append("，请先解除引用后再删除。");
        return builder.toString();
    }

    /**
     * 序列化测试结果。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("公式测试结果序列化失败：" + e.getMessage());
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object valueOf(Map<String, Object> config, String key) {
        return config == null ? null : config.get(key);
    }

    private List<?> listValue(Object value) {
        return value instanceof List ? (List<?>) value : new ArrayList<>();
    }

    private int updateFormulaInternal(CostFormula formula, String changeType) {
        validateDisableBeforeUpdate(formula);
        validateFormula(formula);
        fillDefaultFields(formula);
        ensureBaselineVersionExists(formula.getFormulaId());
        int rows = formulaMapper.updateById(formula);
        saveFormulaVersion(formulaMapper.selectById(formula.getFormulaId()), changeType);
        return rows;
    }

    private void ensureBaselineVersionExists(Long formulaId) {
        if (formulaId == null) {
            return;
        }
        Long versionCount = formulaVersionMapper.selectCount(Wrappers.<CostFormulaVersion>lambdaQuery()
                .eq(CostFormulaVersion::getFormulaId, formulaId));
        if (versionCount != null && versionCount > 0) {
            return;
        }
        CostFormula current = formulaMapper.selectById(formulaId);
        if (current == null) {
            return;
        }
        CostFormulaVersion version = new CostFormulaVersion();
        version.setFormulaId(current.getFormulaId());
        version.setSceneId(current.getSceneId());
        version.setFormulaCode(current.getFormulaCode());
        version.setFormulaName(current.getFormulaName());
        version.setAssetType(current.getAssetType());
        version.setVersionNo(1);
        version.setChangeType("CREATE");
        version.setBusinessFormula(current.getBusinessFormula());
        version.setFormulaExpr(current.getFormulaExpr());
        version.setWorkbenchMode(current.getWorkbenchMode());
        version.setWorkbenchPattern(current.getWorkbenchPattern());
        version.setTemplateCode(current.getTemplateCode());
        version.setWorkbenchConfigJson(current.getWorkbenchConfigJson());
        version.setSnapshotJson(writeJson(buildFormulaSnapshot(current)));
        version.setCreateBy(StringUtils.defaultIfEmpty(current.getUpdateBy(), current.getCreateBy()));
        version.setCreateTime(current.getUpdateTime() != null
                ? current.getUpdateTime()
                : (current.getCreateTime() != null ? current.getCreateTime() : DateUtils.getNowDate()));
        formulaVersionMapper.insert(version);
    }

    /**
     * 保存公式版本快照。
     */
    private void saveFormulaVersion(CostFormula formula, String changeType) {
        if (formula == null || formula.getFormulaId() == null) {
            return;
        }
        CostFormulaVersion latestVersion = formulaVersionMapper.selectOne(Wrappers.<CostFormulaVersion>lambdaQuery()
                .select(CostFormulaVersion::getVersionNo)
                .eq(CostFormulaVersion::getFormulaId, formula.getFormulaId())
                .orderByDesc(CostFormulaVersion::getVersionNo)
                .last("limit 1"));
        CostFormulaVersion version = new CostFormulaVersion();
        version.setFormulaId(formula.getFormulaId());
        version.setSceneId(formula.getSceneId());
        version.setFormulaCode(formula.getFormulaCode());
        version.setFormulaName(formula.getFormulaName());
        version.setAssetType(formula.getAssetType());
        version.setVersionNo(latestVersion == null || latestVersion.getVersionNo() == null ? 1 : latestVersion.getVersionNo() + 1);
        version.setChangeType(changeType);
        version.setBusinessFormula(formula.getBusinessFormula());
        version.setFormulaExpr(formula.getFormulaExpr());
        version.setWorkbenchMode(formula.getWorkbenchMode());
        version.setWorkbenchPattern(formula.getWorkbenchPattern());
        version.setTemplateCode(formula.getTemplateCode());
        version.setWorkbenchConfigJson(formula.getWorkbenchConfigJson());
        version.setSnapshotJson(writeJson(buildFormulaSnapshot(formula)));
        version.setCreateBy(StringUtils.defaultIfEmpty(formula.getUpdateBy(), formula.getCreateBy()));
        version.setCreateTime(DateUtils.getNowDate());
        formulaVersionMapper.insert(version);
    }

    /**
     * 构造公式快照对象。
     */
    private Map<String, Object> buildFormulaSnapshot(CostFormula formula) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sceneId", formula.getSceneId());
        snapshot.put("formulaCode", formula.getFormulaCode());
        snapshot.put("formulaName", formula.getFormulaName());
        snapshot.put("formulaDesc", formula.getFormulaDesc());
        snapshot.put("businessFormula", formula.getBusinessFormula());
        snapshot.put("formulaExpr", formula.getFormulaExpr());
        snapshot.put("assetType", formula.getAssetType());
        snapshot.put("workbenchMode", formula.getWorkbenchMode());
        snapshot.put("workbenchPattern", formula.getWorkbenchPattern());
        snapshot.put("templateCode", formula.getTemplateCode());
        snapshot.put("workbenchConfigJson", formula.getWorkbenchConfigJson());
        snapshot.put("namespaceScope", formula.getNamespaceScope());
        snapshot.put("returnType", formula.getReturnType());
        snapshot.put("testCaseJson", formula.getTestCaseJson());
        snapshot.put("sampleResultJson", formula.getSampleResultJson());
        snapshot.put("lastTestTime", formula.getLastTestTime());
        snapshot.put("status", formula.getStatus());
        snapshot.put("sortNo", formula.getSortNo());
        snapshot.put("remark", formula.getRemark());
        return snapshot;
    }

    /**
     * 反序列化公式版本快照。
     */
    private CostFormula parseFormulaSnapshot(String snapshotJson) {
        if (StringUtils.isEmpty(snapshotJson)) {
            return new CostFormula();
        }
        try {
            return objectMapper.readValue(snapshotJson, CostFormula.class);
        } catch (Exception e) {
            throw new ServiceException("公式版本快照读取失败，请检查历史数据");
        }
    }
}
