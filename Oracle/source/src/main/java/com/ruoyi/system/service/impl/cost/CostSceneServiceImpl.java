package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.*;
import com.ruoyi.system.domain.cost.bo.CostSceneCopyBo;
import com.ruoyi.system.domain.vo.CostSceneGovernanceCheckVo;
import com.ruoyi.system.mapper.cost.*;
import com.ruoyi.system.service.cost.ICostSceneService;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 场景中心服务实现
 *
 * @author HwFan
 */
@Service
public class CostSceneServiceImpl implements ICostSceneService {
    private static final String DICT_TYPE_BUSINESS_DOMAIN = "cost_business_domain";
    private static final String DICT_TYPE_SCENE_TYPE = "cost_scene_type";
    private static final String DICT_TYPE_SCENE_STATUS = "cost_scene_status";

    @Autowired
    private CostSceneMapper sceneMapper;

    @Autowired
    private CostDictionaryProvider dictionaryProvider;

    @Autowired
    private CostGovernanceImpactSupport governanceImpactSupport;

    @Autowired
    private CostFormulaMapper formulaMapper;

    @Autowired
    private CostVariableGroupMapper variableGroupMapper;

    @Autowired
    private CostVariableMapper variableMapper;

    @Autowired
    private CostFeeMapper feeMapper;

    @Autowired
    private CostRuleMapper ruleMapper;

    @Autowired
    private CostRuleConditionMapper conditionMapper;

    @Autowired
    private CostRuleTierMapper tierMapper;

    @Autowired
    private CostFeeVariableRelMapper feeVariableRelMapper;

    /**
     * 查询场景列表
     *
     * @param scene 场景查询对象
     *
     * @return 场景集合
     */
    @Override
    public List<CostScene> selectSceneList(CostScene scene) {
        return sceneMapper.selectSceneList(scene);
    }

    /**
     * 查询场景详情
     *
     * @param sceneId 场景主键
     *
     * @return 场景对象
     */
    @Override
    public CostScene selectSceneById(Long sceneId) {
        return sceneMapper.selectById(sceneId);
    }

    /**
     * 查询场景选择框
     *
     * @param scene 场景查询对象
     *
     * @return 场景集合
     */
    @Override
    public List<CostScene> selectSceneOptions(CostScene scene) {
        return sceneMapper.selectSceneOptions(scene);
    }

    /**
     * 查询场景统计
     *
     * @param scene 场景查询对象
     *
     * @return 统计结果
     */
    @Override
    public Map<String, Object> selectSceneStats(CostScene scene) {
        Map<String, Object> stats = sceneMapper.selectSceneStats(scene);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneCount", 0);
        result.put("enabledSceneCount", 0);
        result.put("businessDomainCount", 0);
        if (stats == null) {
            return result;
        }
        for (String key : result.keySet()) {
            Object value = stats.get(key);
            result.put(key, value == null ? 0 : value);
        }
        return result;
    }

    /**
     * 查询场景治理预检查结果
     *
     * @param sceneId 场景主键
     *
     * @return 结果
     */
    @Override
    public CostSceneGovernanceCheckVo selectSceneGovernanceCheck(Long sceneId) {
        CostSceneGovernanceCheckVo check = sceneMapper.selectSceneGovernanceCheck(sceneId);
        if (StringUtils.isNull(check)) {
            return null;
        }
        normalizeGovernanceCount(check);

        boolean hasPublishedVersion = check.getPublishedVersionCount() > 0;
        boolean hasActiveVersion = StringUtils.isNotNull(check.getActiveVersionId());
        boolean hasDownstreamConfig = check.getTotalConfigCount() > 0;
        boolean hasRunningTask = check.getRunningTaskCount() > 0;
        boolean hasResultLedger = check.getResultLedgerCount() > 0;
        boolean hasRuntimeRecord = check.getSimulationRecordCount() > 0
                || check.getInputBatchCount() > 0
                || check.getTraceCount() > 0;

        check.setCanDelete(!hasActiveVersion && !hasPublishedVersion && !hasDownstreamConfig
                && !hasRunningTask && !hasResultLedger && !hasRuntimeRecord);
        check.setCanDisable(!hasActiveVersion && !hasPublishedVersion && !hasRunningTask && !hasResultLedger);
        check.setRemoveBlockingReason(buildRemoveBlockingReason(check, hasActiveVersion, hasPublishedVersion,
                hasDownstreamConfig, hasRunningTask, hasResultLedger, hasRuntimeRecord));
        check.setDisableBlockingReason(buildDisableBlockingReason(check, hasActiveVersion, hasPublishedVersion, hasRunningTask, hasResultLedger));
        check.setRemoveAdvice(check.getCanDelete() ? "当前场景未被下游配置或发布版本占用，可直接删除。"
                : "请先清理场景下配置、发布版本、运行中任务和结果台账影响后再删除。");
        check.setDisableAdvice(check.getCanDisable() ? buildDisableAdvice(check)
                : "请先处理当前生效版本、已发布版本、运行中任务或结果台账后，再执行停用。");
        check.setImpactItems(governanceImpactSupport.buildSceneImpacts(check));
        check.setRecentTasks(sceneMapper.selectRecentSceneTasks(sceneId));
        return check;
    }

    /**
     * 校验场景编码是否唯一
     *
     * @param scene 场景对象
     *
     * @return 结果
     */
    @Override
    public boolean checkSceneCodeUnique(CostScene scene) {
        Long sceneId = StringUtils.isNull(scene.getSceneId()) ? -1L : scene.getSceneId();
        Long count = sceneMapper.selectCount(Wrappers.<CostScene>lambdaQuery()
                .eq(CostScene::getSceneCode, scene.getSceneCode())
                .ne(sceneId.longValue() != -1L, CostScene::getSceneId, sceneId));
        return count != null && count > 0 ? UserConstants.NOT_UNIQUE : UserConstants.UNIQUE;
    }

    /**
     * 新增场景
     *
     * @param scene 场景对象
     *
     * @return 结果
     */
    @Override
    public int insertScene(CostScene scene) {
        normalizeSceneDimension(scene);
        validateSceneDictValue(scene);
        return sceneMapper.insert(scene);
    }

    /**
     * 复制场景及配置。
     *
     * <p>复制仅覆盖配置主数据，不复制发布版本、核算任务和结果台账，避免新场景继承运行态数据。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CostScene copyScene(CostSceneCopyBo request) {
        if (request == null || request.getSourceSceneId() == null) {
            throw new ServiceException("来源场景不能为空");
        }
        CostScene source = sceneMapper.selectById(request.getSourceSceneId());
        if (StringUtils.isNull(source)) {
            throw new ServiceException("来源场景不存在，请刷新后重试");
        }

        CostScene target = buildCopiedScene(source, request);
        normalizeSceneDimension(target);
        validateSceneDictValue(target);
        if (!checkSceneCodeUnique(target)) {
            throw new ServiceException("复制场景失败，场景编码已存在");
        }
        sceneMapper.insert(target);

        if (!Boolean.FALSE.equals(request.getCopyConfig())) {
            copySceneConfig(source.getSceneId(), target.getSceneId());
        }
        return sceneMapper.selectById(target.getSceneId());
    }

    /**
     * 修改场景
     *
     * @param scene 场景对象
     *
     * @return 结果
     */
    @Override
    public int updateScene(CostScene scene) {
        normalizeSceneDimension(scene);
        validateSceneDictValue(scene);
        validateDisableBeforeUpdate(scene);
        return sceneMapper.updateById(scene);
    }

    private CostScene buildCopiedScene(CostScene source, CostSceneCopyBo request) {
        CostScene target = new CostScene();
        target.setSceneCode(StringUtils.trim(request.getSceneCode()));
        target.setSceneName(StringUtils.trim(request.getSceneName()));
        target.setBusinessDomain(firstNonEmpty(request.getBusinessDomain(), source.getBusinessDomain()));
        target.setOrgCode(firstNonEmpty(request.getOrgCode(), source.getOrgCode()));
        target.setSceneType(firstNonEmpty(request.getSceneType(), source.getSceneType()));
        target.setDefaultObjectDimension(firstNonEmpty(request.getDefaultObjectDimension(), source.getDefaultObjectDimension()));
        target.setStatus(firstNonEmpty(request.getStatus(), "2"));
        target.setRemark(firstNonEmpty(request.getRemark(), source.getRemark()));
        target.setActiveVersionId(null);
        return target;
    }

    private void copySceneConfig(Long sourceSceneId, Long targetSceneId) {
        copyFormulas(sourceSceneId, targetSceneId);
        Map<Long, Long> groupIdMap = copyVariableGroups(sourceSceneId, targetSceneId);
        Map<Long, Long> variableIdMap = copyVariables(sourceSceneId, targetSceneId, groupIdMap);
        Map<Long, Long> feeIdMap = copyFees(sourceSceneId, targetSceneId);
        Map<Long, Long> ruleIdMap = copyRules(sourceSceneId, targetSceneId, feeIdMap);
        copyRuleConditions(sourceSceneId, targetSceneId, ruleIdMap);
        copyRuleTiers(sourceSceneId, targetSceneId, ruleIdMap);
        copyFeeVariableRels(sourceSceneId, targetSceneId, feeIdMap, variableIdMap, ruleIdMap);
    }

    private void copyFormulas(Long sourceSceneId, Long targetSceneId) {
        List<CostFormula> formulas = formulaMapper.selectList(Wrappers.<CostFormula>lambdaQuery()
                .eq(CostFormula::getSceneId, sourceSceneId)
                .orderByAsc(CostFormula::getSortNo)
                .orderByAsc(CostFormula::getFormulaId));
        for (CostFormula source : formulas) {
            CostFormula target = new CostFormula();
            BeanUtils.copyProperties(source, target);
            target.setFormulaId(null);
            target.setSceneId(targetSceneId);
            target.setSampleResultJson(null);
            target.setLastTestTime(null);
            resetAudit(target);
            formulaMapper.insert(target);
        }
    }

    private Map<Long, Long> copyVariableGroups(Long sourceSceneId, Long targetSceneId) {
        List<CostVariableGroup> groups = variableGroupMapper.selectList(Wrappers.<CostVariableGroup>lambdaQuery()
                .eq(CostVariableGroup::getSceneId, sourceSceneId)
                .orderByAsc(CostVariableGroup::getSortNo)
                .orderByAsc(CostVariableGroup::getGroupId));
        Map<Long, Long> groupIdMap = new LinkedHashMap<>();
        for (CostVariableGroup source : groups) {
            Long sourceGroupId = source.getGroupId();
            CostVariableGroup target = new CostVariableGroup();
            BeanUtils.copyProperties(source, target);
            target.setGroupId(null);
            target.setSceneId(targetSceneId);
            resetAudit(target);
            variableGroupMapper.insert(target);
            groupIdMap.put(sourceGroupId, target.getGroupId());
        }
        return groupIdMap;
    }

    private Map<Long, Long> copyVariables(Long sourceSceneId, Long targetSceneId, Map<Long, Long> groupIdMap) {
        List<CostVariable> variables = variableMapper.selectList(Wrappers.<CostVariable>lambdaQuery()
                .eq(CostVariable::getSceneId, sourceSceneId)
                .orderByAsc(CostVariable::getSortNo)
                .orderByAsc(CostVariable::getVariableId));
        Map<Long, Long> variableIdMap = new LinkedHashMap<>();
        for (CostVariable source : variables) {
            Long sourceVariableId = source.getVariableId();
            CostVariable target = new CostVariable();
            BeanUtils.copyProperties(source, target);
            target.setVariableId(null);
            target.setSceneId(targetSceneId);
            target.setGroupId(source.getGroupId() == null ? null : groupIdMap.get(source.getGroupId()));
            resetAudit(target);
            variableMapper.insert(target);
            variableIdMap.put(sourceVariableId, target.getVariableId());
        }
        return variableIdMap;
    }

    private Map<Long, Long> copyFees(Long sourceSceneId, Long targetSceneId) {
        List<CostFeeItem> fees = feeMapper.selectList(Wrappers.<CostFeeItem>lambdaQuery()
                .eq(CostFeeItem::getSceneId, sourceSceneId)
                .orderByAsc(CostFeeItem::getSortNo)
                .orderByAsc(CostFeeItem::getFeeId));
        Map<Long, Long> feeIdMap = new LinkedHashMap<>();
        for (CostFeeItem source : fees) {
            Long sourceFeeId = source.getFeeId();
            CostFeeItem target = new CostFeeItem();
            BeanUtils.copyProperties(source, target);
            target.setFeeId(null);
            target.setSceneId(targetSceneId);
            resetAudit(target);
            feeMapper.insert(target);
            feeIdMap.put(sourceFeeId, target.getFeeId());
        }
        return feeIdMap;
    }

    private Map<Long, Long> copyRules(Long sourceSceneId, Long targetSceneId, Map<Long, Long> feeIdMap) {
        List<CostRule> rules = ruleMapper.selectList(Wrappers.<CostRule>lambdaQuery()
                .eq(CostRule::getSceneId, sourceSceneId)
                .orderByAsc(CostRule::getPriority)
                .orderByAsc(CostRule::getSortNo)
                .orderByAsc(CostRule::getRuleId));
        Map<Long, Long> ruleIdMap = new LinkedHashMap<>();
        for (CostRule source : rules) {
            Long targetFeeId = feeIdMap.get(source.getFeeId());
            if (targetFeeId == null) {
                throw new ServiceException("来源场景规则引用的费用不存在，无法复制");
            }
            Long sourceRuleId = source.getRuleId();
            CostRule target = new CostRule();
            BeanUtils.copyProperties(source, target);
            target.setRuleId(null);
            target.setSceneId(targetSceneId);
            target.setFeeId(targetFeeId);
            resetAudit(target);
            ruleMapper.insert(target);
            ruleIdMap.put(sourceRuleId, target.getRuleId());
        }
        return ruleIdMap;
    }

    private void copyRuleConditions(Long sourceSceneId, Long targetSceneId, Map<Long, Long> ruleIdMap) {
        List<CostRuleCondition> conditions = conditionMapper.selectList(Wrappers.<CostRuleCondition>lambdaQuery()
                .eq(CostRuleCondition::getSceneId, sourceSceneId)
                .orderByAsc(CostRuleCondition::getRuleId)
                .orderByAsc(CostRuleCondition::getGroupNo)
                .orderByAsc(CostRuleCondition::getSortNo)
                .orderByAsc(CostRuleCondition::getConditionId));
        for (CostRuleCondition source : conditions) {
            Long targetRuleId = ruleIdMap.get(source.getRuleId());
            if (targetRuleId == null) {
                continue;
            }
            CostRuleCondition target = new CostRuleCondition();
            BeanUtils.copyProperties(source, target);
            target.setConditionId(null);
            target.setSceneId(targetSceneId);
            target.setRuleId(targetRuleId);
            resetAudit(target);
            conditionMapper.insert(target);
        }
    }

    private void copyRuleTiers(Long sourceSceneId, Long targetSceneId, Map<Long, Long> ruleIdMap) {
        List<CostRuleTier> tiers = tierMapper.selectList(Wrappers.<CostRuleTier>lambdaQuery()
                .eq(CostRuleTier::getSceneId, sourceSceneId)
                .orderByAsc(CostRuleTier::getRuleId)
                .orderByAsc(CostRuleTier::getTierNo)
                .orderByAsc(CostRuleTier::getTierId));
        for (CostRuleTier source : tiers) {
            Long targetRuleId = ruleIdMap.get(source.getRuleId());
            if (targetRuleId == null) {
                continue;
            }
            CostRuleTier target = new CostRuleTier();
            BeanUtils.copyProperties(source, target);
            target.setTierId(null);
            target.setSceneId(targetSceneId);
            target.setRuleId(targetRuleId);
            resetAudit(target);
            tierMapper.insert(target);
        }
    }

    private void copyFeeVariableRels(Long sourceSceneId, Long targetSceneId, Map<Long, Long> feeIdMap,
                                     Map<Long, Long> variableIdMap, Map<Long, Long> ruleIdMap) {
        List<CostFeeVariableRel> rels = feeVariableRelMapper.selectList(Wrappers.<CostFeeVariableRel>lambdaQuery()
                .eq(CostFeeVariableRel::getSceneId, sourceSceneId)
                .orderByAsc(CostFeeVariableRel::getSortNo)
                .orderByAsc(CostFeeVariableRel::getRelId));
        List<CostFeeVariableRel> targetRels = new ArrayList<>();
        for (CostFeeVariableRel source : rels) {
            Long targetFeeId = feeIdMap.get(source.getFeeId());
            Long targetVariableId = variableIdMap.get(source.getVariableId());
            if (targetFeeId == null || targetVariableId == null) {
                continue;
            }
            CostFeeVariableRel target = new CostFeeVariableRel();
            BeanUtils.copyProperties(source, target);
            target.setRelId(null);
            target.setSceneId(targetSceneId);
            target.setFeeId(targetFeeId);
            target.setVariableId(targetVariableId);
            target.setSourceRuleId(source.getSourceRuleId() == null ? null : ruleIdMap.get(source.getSourceRuleId()));
            targetRels.add(target);
        }
        if (!targetRels.isEmpty()) {
            feeMapper.insertFeeVariableRels(targetRels);
        }
    }

    private void resetAudit(BaseEntity entity) {
        entity.setSearchValue(null);
        entity.setCreateBy(null);
        entity.setCreateTime(null);
        entity.setUpdateBy(null);
        entity.setUpdateTime(null);
        entity.setParams(null);
    }

    private String firstNonEmpty(String value, String fallback) {
        return StringUtils.isEmpty(value) ? fallback : StringUtils.trim(value);
    }

    private void normalizeSceneDimension(CostScene scene) {
        if (scene == null) {
            return;
        }
        scene.setDefaultObjectDimension(StringUtils.trim(scene.getDefaultObjectDimension()));
    }

    /**
     * 校验场景中心核心字段必须来自系统字典。
     *
     * <p>线程一的治理边界要求“业务域字典化、系统字典收口”必须在后端硬约束，
     * 不能只依赖前端下拉防止非法值写入。这里统一校验业务域、场景类型和场景状态，
     * 让场景主数据始终受控于 cost_ 前缀字典体系。</p>
     *
     * @param scene 场景对象
     */
    private void validateSceneDictValue(CostScene scene) {
        validateDictValueExists(DICT_TYPE_BUSINESS_DOMAIN, scene.getBusinessDomain(), "业务域");
        validateDictValueExists(DICT_TYPE_SCENE_TYPE, scene.getSceneType(), "场景类型");
        validateDictValueExists(DICT_TYPE_SCENE_STATUS, scene.getStatus(), "场景状态");
    }

    /**
     * 批量删除场景
     *
     * @param sceneIds 场景主键数组
     *
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSceneByIds(Long[] sceneIds) {
        for (Long sceneId : sceneIds) {
            CostSceneGovernanceCheckVo check = selectSceneGovernanceCheck(sceneId);
            if (StringUtils.isNull(check)) {
                continue;
            }
            if (!Boolean.TRUE.equals(check.getCanDelete())) {
                throw new ServiceException(String.format("%1$s不能删除：%2$s", check.getSceneName(), check.getRemoveBlockingReason()));
            }
        }
        cleanupEmptyVariableGroups(sceneIds);
        return sceneMapper.deleteBatchIds(Arrays.asList(sceneIds));
    }

    private void cleanupEmptyVariableGroups(Long[] sceneIds) {
        if (sceneIds == null || sceneIds.length == 0) {
            return;
        }
        variableGroupMapper.delete(Wrappers.<CostVariableGroup>lambdaQuery()
                .in(CostVariableGroup::getSceneId, Arrays.asList(sceneIds))
                .notExists("select 1 from cost_variable v where v.group_id = cost_variable_group.group_id"));
    }

    /**
     * 标准化治理计数
     *
     * @param check 结果
     */
    private void normalizeGovernanceCount(CostSceneGovernanceCheckVo check) {
        check.setFeeCount(nullSafeLong(check.getFeeCount()));
        check.setVariableGroupCount(nullSafeLong(check.getVariableGroupCount()));
        check.setVariableCount(nullSafeLong(check.getVariableCount()));
        check.setRuleCount(nullSafeLong(check.getRuleCount()));
        check.setPublishedVersionCount(nullSafeLong(check.getPublishedVersionCount()));
        check.setTaskCount(nullSafeLong(check.getTaskCount()));
        check.setRunningTaskCount(nullSafeLong(check.getRunningTaskCount()));
        check.setFailedTaskCount(nullSafeLong(check.getFailedTaskCount()));
        check.setResultLedgerCount(nullSafeLong(check.getResultLedgerCount()));
        check.setSimulationRecordCount(nullSafeLong(check.getSimulationRecordCount()));
        check.setInputBatchCount(nullSafeLong(check.getInputBatchCount()));
        check.setTraceCount(nullSafeLong(check.getTraceCount()));
        check.setTotalConfigCount(check.getFeeCount() + check.getVariableGroupCount() + check.getVariableCount() + check.getRuleCount());
    }

    /**
     * 更新前校验停用动作
     *
     * @param scene 提交对象
     */
    private void validateDisableBeforeUpdate(CostScene scene) {
        if (StringUtils.isNull(scene.getSceneId()) || !"1".equals(scene.getStatus())) {
            return;
        }
        CostScene current = selectSceneById(scene.getSceneId());
        if (StringUtils.isNull(current) || "1".equals(current.getStatus())) {
            return;
        }
        CostSceneGovernanceCheckVo check = selectSceneGovernanceCheck(scene.getSceneId());
        if (StringUtils.isNotNull(check) && !Boolean.TRUE.equals(check.getCanDisable())) {
            throw new ServiceException(String.format("%1$s不能停用：%2$s", check.getSceneName(), check.getDisableBlockingReason()));
        }
    }

    /**
     * 构造删除阻断说明
     *
     * @param check               结果
     * @param hasActiveVersion    是否有生效版本
     * @param hasPublishedVersion 是否有发布版本
     * @param hasDownstreamConfig 是否存在下游配置
     * @param hasRuntimeRecord   是否存在运行留痕
     *
     * @return 说明
     */
    private String buildRemoveBlockingReason(CostSceneGovernanceCheckVo check, boolean hasActiveVersion, boolean hasPublishedVersion,
                                             boolean hasDownstreamConfig, boolean hasRunningTask, boolean hasResultLedger,
                                             boolean hasRuntimeRecord) {
        if (!hasActiveVersion && !hasPublishedVersion && !hasDownstreamConfig && !hasRunningTask
                && !hasResultLedger && !hasRuntimeRecord) {
            return "当前场景未被下游配置、发布版本、运行任务、结果台账或运行留痕占用";
        }
        StringJoiner joiner = new StringJoiner("；");
        if (hasDownstreamConfig) {
            joiner.add(String.format("已挂载%1$d项下游配置（费用%2$d、变量组%3$d、变量%4$d、规则%5$d）", check.getTotalConfigCount(), check.getFeeCount(),
                    check.getVariableGroupCount(), check.getVariableCount(), check.getRuleCount()));
        }
        if (hasPublishedVersion) {
            joiner.add(String.format("已有%1$d个发布版本", check.getPublishedVersionCount()));
        }
        if (hasActiveVersion) {
            joiner.add(String.format("当前仍绑定生效版本%1$d", check.getActiveVersionId()));
        }
        if (hasRunningTask) {
            joiner.add(String.format("仍有%1$d个核算任务正在运行", check.getRunningTaskCount()));
        }
        if (hasResultLedger) {
            joiner.add(String.format("结果台账已有%1$d条历史结果", check.getResultLedgerCount()));
        }
        if (hasRuntimeRecord) {
            if (check.getSimulationRecordCount() > 0) {
                joiner.add(String.format("同步试算记录已有%1$d条历史记录", check.getSimulationRecordCount()));
            }
            if (check.getInputBatchCount() > 0) {
                joiner.add(String.format("正式核算输入批次已有%1$d条历史记录", check.getInputBatchCount()));
            }
            if (check.getTraceCount() > 0) {
                joiner.add(String.format("结果追溯记录已有%1$d条历史记录", check.getTraceCount()));
            }
        }
        return joiner.toString();
    }

    /**
     * 构造停用阻断说明
     *
     * @param check               结果
     * @param hasActiveVersion    是否有生效版本
     * @param hasPublishedVersion 是否有发布版本
     *
     * @return 说明
     */
    private String buildDisableBlockingReason(CostSceneGovernanceCheckVo check, boolean hasActiveVersion, boolean hasPublishedVersion,
                                              boolean hasRunningTask, boolean hasResultLedger) {
        if (!hasActiveVersion && !hasPublishedVersion && !hasRunningTask && !hasResultLedger) {
            return "当前场景未进入发布生效、运行任务或结果台账治理，可安全停用";
        }
        StringJoiner joiner = new StringJoiner("；");
        if (hasPublishedVersion) {
            joiner.add(String.format("已有%1$d个发布版本", check.getPublishedVersionCount()));
        }
        if (hasActiveVersion) {
            joiner.add(String.format("当前仍绑定生效版本%1$d", check.getActiveVersionId()));
        }
        if (hasRunningTask) {
            joiner.add(String.format("仍有%1$d个核算任务正在运行", check.getRunningTaskCount()));
        }
        if (hasResultLedger) {
            joiner.add(String.format("结果台账已有%1$d条历史结果", check.getResultLedgerCount()));
        }
        return joiner.toString();
    }

    /**
     * 构造停用建议
     *
     * @param check 结果
     *
     * @return 建议
     */
    private String buildDisableAdvice(CostSceneGovernanceCheckVo check) {
        if (check.getTotalConfigCount() <= 0) {
            return "当前场景无下游配置，停用后将从后续维护和选择范围中移除。";
        }
        return String.format("当前场景下已有%1$d项配置对象，停用后将从业务选择范围中移除，但配置数据会继续保留。", check.getTotalConfigCount());
    }

    /**
     * 空值转0
     *
     * @param value 值
     *
     * @return 结果
     */
    private long nullSafeLong(Long value) {
        return StringUtils.isNull(value) ? 0L : value.longValue();
    }

    /**
     * 校验字典值是否存在且处于可用状态。
     *
     * @param dictType   字典类型
     * @param dictValue  字典值
     * @param fieldLabel 业务字段名称
     */
    private void validateDictValueExists(String dictType, String dictValue, String fieldLabel) {
        if (StringUtils.isEmpty(dictValue)) {
            return;
        }
        if (!dictionaryProvider.containsValue(dictType, dictValue)) {
            throw new ServiceException(String.format("%s取值无效，请从系统字典 %s 中选择合法值：%s", fieldLabel, dictType, dictValue));
        }
    }
}
