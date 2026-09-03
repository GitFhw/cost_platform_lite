package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.*;
import com.ruoyi.system.domain.vo.*;
import com.ruoyi.system.mapper.cost.*;
import com.ruoyi.system.service.cost.ICostExpressionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 治理影响明细构造器。
 */
@Component
public class CostGovernanceImpactSupport {
    private static final int SAMPLE_LIMIT = 5;

    @Autowired
    private CostFeeMapper feeMapper;

    @Autowired
    private CostFormulaMapper formulaMapper;

    @Autowired
    private CostVariableGroupMapper variableGroupMapper;

    @Autowired
    private CostVariableMapper variableMapper;

    @Autowired
    private CostRuleMapper ruleMapper;

    @Autowired
    private CostRuleConditionMapper conditionMapper;

    @Autowired
    private CostRuleTierMapper tierMapper;

    @Autowired
    private CostPublishVersionMapper publishVersionMapper;

    @Autowired
    private CostPublishSnapshotMapper publishSnapshotMapper;

    @Autowired
    private CostResultLedgerMapper resultLedgerMapper;

    @Autowired
    private CostCalcTaskMapper calcTaskMapper;

    @Autowired
    private CostResultTraceMapper resultTraceMapper;

    @Autowired
    private CostFeeVariableRelMapper feeVariableRelMapper;

    @Autowired
    private ICostExpressionService expressionService;

    public List<CostGovernanceImpactVo> buildSceneImpacts(CostSceneGovernanceCheckVo check) {
        List<CostGovernanceImpactVo> impacts = new ArrayList<>();
        if (positive(check.getFeeCount())) {
            impacts.add(impact("SCENE_FEE", "费用中心", "场景下已维护费用", check.getFeeCount(), true, false,
                    "删除场景前必须先删除或迁移这些费用，否则费用主数据会失去归属场景。",
                    "停用场景后，这些费用不会再作为新增配置和运行选择的推荐范围，但历史配置仍保留。",
                    "进入费用中心按场景筛选，逐项删除、迁移或停用费用。", sampleFeesByScene(check.getSceneId())));
        }
        if (positive(check.getVariableGroupCount())) {
            impacts.add(impact("SCENE_VARIABLE_GROUP", "变量中心", "场景下已维护变量组", check.getVariableGroupCount(), true, false,
                    "删除场景前必须先清理变量组，否则变量分组会失去归属场景。",
                    "停用场景后，变量组仍保留，但不建议继续在该场景下新增变量。",
                    "进入变量中心或变量组维护，清理无用分组或迁移到新场景。", sampleVariableGroupsByScene(check.getSceneId())));
        }
        if (positive(check.getVariableCount())) {
            impacts.add(impact("SCENE_VARIABLE", "变量中心", "场景下已维护变量", check.getVariableCount(), true, false,
                    "删除场景前必须先清理变量，否则规则条件、公式和输入模板会失去变量定义。",
                    "停用场景后，变量不再建议用于新增配置，历史发布和结果仍按快照解释。",
                    "进入变量中心按场景筛选，先处理被规则或发布引用的变量。", sampleVariablesByScene(check.getSceneId())));
        }
        if (positive(check.getRuleCount())) {
            impacts.add(impact("SCENE_RULE", "规则中心", "场景下已维护规则", check.getRuleCount(), true, false,
                    "删除场景前必须先清理规则，否则费用计算口径会失去场景归属。",
                    "停用场景后，规则不再建议参与新的发布和核算配置。",
                    "进入规则中心按场景筛选，先处理发布版本和追溯引用，再清理规则。", sampleRulesByScene(check.getSceneId())));
        }
        if (positive(check.getPublishedVersionCount())) {
            impacts.add(impact("SCENE_PUBLISH_VERSION", "发布中心", "场景已有发布版本", check.getPublishedVersionCount(), true, true,
                    "删除场景会破坏已发布快照的追溯边界，因此必须先处理发布版本。",
                    "停用场景前必须处理发布版本，避免仍有可运行版本指向停用场景。",
                    "进入发布中心查看版本，确认是否需要回滚、废弃或保留历史。", sampleVersionsByScene(check.getSceneId())));
        }
        if (check.getActiveVersionId() != null) {
            impacts.add(impact("SCENE_ACTIVE_VERSION", "发布中心", "当前仍绑定生效版本", 1L, true, true,
                    "当前生效版本仍指向该场景，删除会破坏运行入口。",
                    "当前生效版本仍指向该场景，停用会造成业务选择和运行状态不一致。",
                    "先在发布中心切换、回滚或解除当前生效版本，再执行删除或停用。", sampleActiveVersion(check.getActiveVersionId())));
        }
        if (positive(check.getRunningTaskCount())) {
            impacts.add(impact("SCENE_RUNNING_TASK", "核算任务", "场景仍有运行中任务", check.getRunningTaskCount(), true, true,
                    "运行中任务仍在写入或准备写入结果，删除场景会破坏执行上下文。",
                    "运行中任务仍指向该场景，停用会造成任务执行和业务选择状态不一致。",
                    "进入核算任务总台按场景筛选，等待任务完成、取消或处理失败后再停用。", sampleRunningTasksByScene(check.getSceneId())));
        }
        if (positive(check.getResultLedgerCount())) {
            impacts.add(impact("SCENE_RESULT_LEDGER", "结果台账", "场景已有结果台账", check.getResultLedgerCount(), true, true,
                    "结果台账已按该场景落账，删除会破坏历史结果查询和审计边界。",
                    "场景已有结果台账，停用前需要确认后续账期不再使用或已切换替代场景。",
                    "进入结果台账按场景筛选，确认账期、任务和业务对象影响范围。", sampleLedgersByScene(check.getSceneId())));
        }
        return impacts;
    }

    public List<CostGovernanceImpactVo> buildFeeImpacts(CostFeeGovernanceCheckVo check) {
        List<CostGovernanceImpactVo> impacts = new ArrayList<>();
        if (positive(check.getRuleCount())) {
            impacts.add(impact("FEE_RULE", "规则中心", "规则引用当前费用", check.getRuleCount(), true, false,
                    "删除费用前必须先删除或迁移这些规则，否则规则会失去归属费用。",
                    "停用费用后，这些规则不建议继续参与新增发布和核算选择。",
                    "进入规则中心按费用筛选，先处理规则发布和追溯引用，再清理规则。", sampleRulesByFee(check.getFeeId())));
        }
        if (positive(check.getVariableRelCount())) {
            impacts.add(impact("FEE_VARIABLE_CONTRACT", "变量中心", "费用输入契约关系", check.getVariableRelCount(), false, false,
                    "这些关系是费用依赖变量的契约。删除费用时会自动清理，不单独阻断删除。",
                    "停用费用后，这些变量关系仍用于解释历史配置和影响分析。",
                    "如需调整依赖变量，优先到规则中心修改规则；系统会重建规则派生关系。", sampleVariableRelsByFee(check.getFeeId())));
        }
        if (positive(check.getPublishedVersionCount())) {
            impacts.add(impact("FEE_PUBLISH_SNAPSHOT", "发布中心", "发布版本快照引用当前费用", check.getPublishedVersionCount(), true, true,
                    "费用已进入发布快照，删除会影响历史版本复现和差异对比。",
                    "费用已进入发布快照，停用前需要发布替代版本或确认历史保留口径。",
                    "进入发布中心查看包含该费用的版本，发布替代版本后再处理。", sampleSnapshots(check.getSceneId(), "FEE", check.getFeeCode())));
        }
        if (positive(check.getResultLedgerCount())) {
            impacts.add(impact("FEE_RESULT_LEDGER", "结果台账", "结果台账绑定当前费用", check.getResultLedgerCount(), true, true,
                    "结果台账已按该费用落账，删除会破坏历史结果查询和审计。",
                    "费用已有结果台账，停用前需要确认后续账期不再使用或已发布替代费用。",
                    "进入结果台账按费用筛选，确认账期、任务和业务对象影响范围。", sampleLedgersByFee(check.getFeeId())));
        }
        return impacts;
    }

    public List<CostGovernanceImpactVo> buildVariableImpacts(CostVariableGovernanceCheckVo check) {
        List<CostGovernanceImpactVo> impacts = new ArrayList<>();
        if (positive(check.getFeeRelCount())) {
            impacts.add(impact("VARIABLE_FEE_CONTRACT", "费用中心", "费用输入契约引用当前变量", check.getFeeRelCount(), false, false,
                    "费用输入契约会随变量删除自动清理；若它来自规则派生，应优先检查规则是否已同步。",
                    "停用变量后，费用输入契约仍用于影响分析和历史解释。",
                    "进入费用中心或规则中心查看依赖来源，确认是否需要替换变量。", sampleFeeRelsByVariable(check.getVariableId())));
        }
        if (positive(check.getRuleConditionCount())) {
            impacts.add(impact("VARIABLE_RULE_CONDITION", "规则中心", "规则条件引用当前变量", check.getRuleConditionCount(), true, false,
                    "删除变量会导致规则条件无法判断，必须先修改或删除相关规则条件。",
                    "停用变量不会删除规则条件，但会让后续规则维护和发布校验存在风险。",
                    "进入规则中心定位这些规则，替换条件变量或删除条件后再处理变量。", sampleRuleConditionsByVariable(check.getSceneId(), check.getVariableCode())));
        }
        if (positive(check.getRuleQuantityCount())) {
            impacts.add(impact("VARIABLE_RULE_QUANTITY", "规则中心", "规则计量字段引用当前变量", check.getRuleQuantityCount(), true, false,
                    "删除变量会导致规则计量或阶梯依据缺失，必须先替换规则计量变量。",
                    "停用变量后，相关规则仍可能引用它，发布前需要重新校验计量口径。",
                    "进入规则中心筛选计量变量，替换为新的变量后再删除。", sampleQuantityRulesByVariable(check.getSceneId(), check.getVariableCode())));
        }
        if (positive(check.getFormulaRefCount())) {
            impacts.add(impact("VARIABLE_FORMULA_REF", "公式实验室", "公式表达式引用当前变量", check.getFormulaRefCount(), true, false,
                    "删除变量会导致这些公式表达式缺少输入，必须先替换公式中的变量引用。",
                    "停用变量不会删除公式表达式，但后续发布前需要确认公式输入已替换为可用变量。",
                    "进入公式实验室定位这些公式，替换表达式中的变量后再处理变量。", sampleFormulasByVariable(check.getSceneId(), check.getVariableCode())));
        }
        if (positive(check.getPublishedVersionCount())) {
            impacts.add(impact("VARIABLE_PUBLISH_SNAPSHOT", "发布中心", "发布版本快照引用当前变量", check.getPublishedVersionCount(), true, true,
                    "变量已进入发布快照，删除会影响历史版本复现和运行解释。",
                    "变量已进入发布快照，停用前需要发布替代版本或确认历史保留口径。",
                    "进入发布中心查看包含该变量的版本，发布替代版本后再处理。", sampleSnapshots(check.getSceneId(), "VARIABLE", check.getVariableCode())));
        }
        return impacts;
    }

    public List<CostGovernanceImpactVo> buildFormulaImpacts(CostFormulaGovernanceCheckVo check) {
        List<CostGovernanceImpactVo> impacts = new ArrayList<>();
        if (positive(check.getVariableRefCount())) {
            impacts.add(impact("FORMULA_VARIABLE_REF", "变量中心", "变量引用当前公式", check.getVariableRefCount(), true, false,
                    "删除公式前必须先替换这些变量的公式编码，否则公式变量会失去计算来源。",
                    "停用公式后，变量仍保留引用关系，但后续发布前需要替换为可用公式。",
                    "进入变量中心按场景筛选，定位公式来源为当前公式的变量并替换公式编码。", sampleVariablesByFormula(check.getSceneId(), check.getFormulaCode())));
        }
        if (positive(check.getRuleRefCount())) {
            impacts.add(impact("FORMULA_RULE_REF", "规则中心", "规则引用当前公式", check.getRuleRefCount(), true, false,
                    "删除公式前必须先替换这些公式规则的金额公式，否则规则无法计算金额。",
                    "停用公式后，相关规则仍可能引用它，发布前需要重新选择可用公式。",
                    "进入规则中心按场景筛选，定位公式计价规则并替换金额公式。", sampleRulesByFormula(check.getSceneId(), check.getFormulaCode())));
        }
        if (positive(check.getPublishedVersionCount())) {
            impacts.add(impact("FORMULA_PUBLISH_SNAPSHOT", "发布中心", "发布版本快照引用当前公式", check.getPublishedVersionCount(), true, true,
                    "公式已进入发布快照，删除会影响历史版本复现和公式差异对比。",
                    "公式已进入发布快照，停用前需要发布替代版本或确认历史保留口径。",
                    "进入发布中心查看包含该公式的版本，发布替代公式后再处理。", sampleSnapshots(check.getSceneId(), "FORMULA", check.getFormulaCode())));
        }
        return impacts;
    }

    public List<CostGovernanceImpactVo> buildRuleImpacts(CostRuleGovernanceCheckVo check) {
        List<CostGovernanceImpactVo> impacts = new ArrayList<>();
        if (positive(check.getConditionCount())) {
            impacts.add(impact("RULE_CONDITION", "规则中心", "规则下已维护条件", check.getConditionCount(), false, false,
                    "删除规则时条件会随规则一起删除，不单独阻断。",
                    "停用规则后这些条件不再参与新核算匹配，但仍保留用于配置回看。",
                    "如只需调整命中范围，请修改条件而不是直接删除规则。", sampleConditionsByRule(check.getRuleId())));
        }
        if (positive(check.getTierCount())) {
            impacts.add(impact("RULE_TIER", "规则中心", "规则下已维护阶梯", check.getTierCount(), false, false,
                    "删除规则时阶梯会随规则一起删除，不单独阻断。",
                    "停用规则后这些阶梯不再参与新核算匹配，但仍保留用于配置回看。",
                    "如只需调整费率区间，请修改阶梯而不是直接删除规则。", sampleTiersByRule(check.getRuleId())));
        }
        if (positive(check.getPublishedVersionCount())) {
            impacts.add(impact("RULE_PUBLISH_SNAPSHOT", "发布中心", "发布版本快照引用当前规则", check.getPublishedVersionCount(), true, true,
                    "规则已进入发布快照，删除会影响历史版本复现和差异对比。",
                    "规则已进入发布快照，停用前需要发布替代版本或确认历史保留口径。",
                    "进入发布中心查看包含该规则的版本，发布替代规则后再处理。", sampleSnapshots(check.getSceneId(), "RULE", check.getRuleCode())));
        }
        if (positive(check.getTraceCount())) {
            impacts.add(impact("RULE_RESULT_TRACE", "结果追溯", "结果追溯命中过当前规则", check.getTraceCount(), true, true,
                    "追溯明细依赖该规则解释历史结果，删除会破坏结果可解释性。",
                    "该规则已命中过正式结果，停用前需要确认后续版本已替代。",
                    "进入结果追溯或结果台账，确认命中任务、账期和费用影响范围。", sampleTracesByRule(check.getRuleId())));
        }
        return impacts;
    }

    private CostGovernanceImpactVo impact(String impactType, String moduleName, String title, Long count,
                                          boolean blocksDelete, boolean blocksDisable, String deleteImpact,
                                          String disableImpact, String actionAdvice, List<String> examples) {
        CostGovernanceImpactVo item = new CostGovernanceImpactVo();
        item.setImpactType(impactType);
        item.setModuleName(moduleName);
        item.setTitle(title);
        item.setCount(count == null ? 0L : count);
        item.setBlocksDelete(blocksDelete);
        item.setBlocksDisable(blocksDisable);
        item.setDeleteImpact(deleteImpact);
        item.setDisableImpact(disableImpact);
        item.setActionAdvice(actionAdvice);
        item.setExamples(examples == null ? Collections.emptyList() : examples);
        return item;
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private List<String> sampleFeesByScene(Long sceneId) {
        return feeMapper.selectList(Wrappers.<CostFeeItem>lambdaQuery()
                        .eq(CostFeeItem::getSceneId, sceneId)
                        .orderByAsc(CostFeeItem::getSortNo)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(fee -> label(fee.getFeeName(), fee.getFeeCode())).collect(Collectors.toList());
    }

    private List<String> sampleVariableGroupsByScene(Long sceneId) {
        return variableGroupMapper.selectList(Wrappers.<CostVariableGroup>lambdaQuery()
                        .eq(CostVariableGroup::getSceneId, sceneId)
                        .orderByAsc(CostVariableGroup::getSortNo)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(group -> label(group.getGroupName(), group.getGroupCode())).collect(Collectors.toList());
    }

    private List<String> sampleVariablesByScene(Long sceneId) {
        return variableMapper.selectList(Wrappers.<CostVariable>lambdaQuery()
                        .eq(CostVariable::getSceneId, sceneId)
                        .orderByAsc(CostVariable::getSortNo)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(variable -> label(variable.getVariableName(), variable.getVariableCode())).collect(Collectors.toList());
    }

    private List<String> sampleRulesByScene(Long sceneId) {
        return ruleMapper.selectList(Wrappers.<CostRule>lambdaQuery()
                        .eq(CostRule::getSceneId, sceneId)
                        .orderByDesc(CostRule::getPriority)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(rule -> label(rule.getRuleName(), rule.getRuleCode())).collect(Collectors.toList());
    }

    private List<String> sampleRulesByFee(Long feeId) {
        return ruleMapper.selectList(Wrappers.<CostRule>lambdaQuery()
                        .eq(CostRule::getFeeId, feeId)
                        .orderByDesc(CostRule::getPriority)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(rule -> label(rule.getRuleName(), rule.getRuleCode())).collect(Collectors.toList());
    }

    private List<String> sampleVersionsByScene(Long sceneId) {
        return publishVersionMapper.selectList(Wrappers.<CostPublishVersion>lambdaQuery()
                        .eq(CostPublishVersion::getSceneId, sceneId)
                        .orderByDesc(CostPublishVersion::getPublishedTime)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(version -> "版本 " + StringUtils.defaultString(version.getVersionNo()) + " / 状态 " + versionStatusLabel(version.getVersionStatus()))
                .collect(Collectors.toList());
    }

    private List<String> sampleActiveVersion(Long versionId) {
        CostPublishVersion version = publishVersionMapper.selectById(versionId);
        if (version == null) {
            return Collections.singletonList("版本ID " + versionId);
        }
        return Collections.singletonList("版本 " + StringUtils.defaultString(version.getVersionNo()) + " / ID " + versionId);
    }

    private List<String> sampleSnapshots(Long sceneId, String snapshotType, String objectCode) {
        return publishSnapshotMapper.selectList(Wrappers.<CostPublishSnapshot>lambdaQuery()
                        .eq(CostPublishSnapshot::getSnapshotType, snapshotType)
                        .eq(CostPublishSnapshot::getObjectCode, objectCode)
                        .inSql(CostPublishSnapshot::getVersionId,
                                "select version_id from cost_publish_version where scene_id = " + sceneId)
                        .orderByDesc(CostPublishSnapshot::getVersionId)
                        .last("limit " + SAMPLE_LIMIT))
                .stream()
                .map(snapshot -> "版本ID " + snapshot.getVersionId() + " / " + label(snapshot.getObjectName(), snapshot.getObjectCode()))
                .collect(Collectors.toList());
    }

    private List<String> sampleLedgersByFee(Long feeId) {
        return resultLedgerMapper.selectList(Wrappers.<CostResultLedger>lambdaQuery()
                        .eq(CostResultLedger::getFeeId, feeId)
                        .orderByDesc(CostResultLedger::getResultId)
                        .last("limit " + SAMPLE_LIMIT))
                .stream()
                .map(ledger -> "任务 " + StringUtils.defaultString(ledger.getTaskNo()) + " / 账期 " + StringUtils.defaultString(ledger.getBillMonth())
                        + " / 对象 " + label(ledger.getObjectName(), ledger.getObjectCode()))
                .collect(Collectors.toList());
    }

    private List<String> sampleLedgersByScene(Long sceneId) {
        return resultLedgerMapper.selectList(Wrappers.<CostResultLedger>lambdaQuery()
                        .eq(CostResultLedger::getSceneId, sceneId)
                        .orderByDesc(CostResultLedger::getResultId)
                        .last("limit " + SAMPLE_LIMIT))
                .stream()
                .map(ledger -> "任务 " + StringUtils.defaultString(ledger.getTaskNo()) + " / 账期 " + StringUtils.defaultString(ledger.getBillMonth())
                        + " / 费用 " + label(ledger.getFeeName(), ledger.getFeeCode()))
                .collect(Collectors.toList());
    }

    private List<String> sampleRunningTasksByScene(Long sceneId) {
        return calcTaskMapper.selectList(Wrappers.<CostCalcTask>lambdaQuery()
                        .eq(CostCalcTask::getSceneId, sceneId)
                        .in(CostCalcTask::getTaskStatus, Arrays.asList("INIT", "RUNNING"))
                        .orderByDesc(CostCalcTask::getTaskId)
                        .last("limit " + SAMPLE_LIMIT))
                .stream()
                .map(task -> "任务 " + StringUtils.defaultString(task.getTaskNo()) + " / 状态 " + StringUtils.defaultString(task.getTaskStatus())
                        + " / 账期 " + StringUtils.defaultString(task.getBillMonth()))
                .collect(Collectors.toList());
    }

    private List<String> sampleVariableRelsByFee(Long feeId) {
        List<CostFeeVariableRel> rels = feeVariableRelMapper.selectList(Wrappers.<CostFeeVariableRel>lambdaQuery()
                .eq(CostFeeVariableRel::getFeeId, feeId)
                .orderByAsc(CostFeeVariableRel::getSortNo)
                .last("limit " + SAMPLE_LIMIT));
        Map<Long, CostVariable> variableMap = loadVariableMap(rels.stream().map(CostFeeVariableRel::getVariableId).collect(Collectors.toSet()));
        return rels.stream()
                .map(rel -> {
                    CostVariable variable = variableMap.get(rel.getVariableId());
                    String variableLabel = variable == null ? "变量ID " + rel.getVariableId() : label(variable.getVariableName(), variable.getVariableCode());
                    return variableLabel + " / " + relationTypeLabel(rel.getRelationType()) + " / " + relationSourceTypeLabel(rel.getSourceType());
                })
                .collect(Collectors.toList());
    }

    private List<String> sampleFeeRelsByVariable(Long variableId) {
        List<CostFeeVariableRel> rels = feeVariableRelMapper.selectList(Wrappers.<CostFeeVariableRel>lambdaQuery()
                .eq(CostFeeVariableRel::getVariableId, variableId)
                .orderByAsc(CostFeeVariableRel::getSortNo)
                .last("limit " + SAMPLE_LIMIT));
        Map<Long, CostFeeItem> feeMap = loadFeeMap(rels.stream().map(CostFeeVariableRel::getFeeId).collect(Collectors.toSet()));
        return rels.stream()
                .map(rel -> {
                    CostFeeItem fee = feeMap.get(rel.getFeeId());
                    String feeLabel = fee == null ? "费用ID " + rel.getFeeId() : label(fee.getFeeName(), fee.getFeeCode());
                    return feeLabel + " / " + relationTypeLabel(rel.getRelationType()) + " / " + relationSourceTypeLabel(rel.getSourceType());
                })
                .collect(Collectors.toList());
    }

    private List<String> sampleRuleConditionsByVariable(Long sceneId, String variableCode) {
        List<CostRuleCondition> conditions = conditionMapper.selectList(Wrappers.<CostRuleCondition>lambdaQuery()
                .eq(CostRuleCondition::getSceneId, sceneId)
                .eq(CostRuleCondition::getVariableCode, variableCode)
                .orderByAsc(CostRuleCondition::getRuleId)
                .last("limit " + SAMPLE_LIMIT));
        Map<Long, CostRule> ruleMap = loadRuleMap(conditions.stream().map(CostRuleCondition::getRuleId).collect(Collectors.toSet()));
        return conditions.stream()
                .map(condition -> {
                    CostRule rule = ruleMap.get(condition.getRuleId());
                    String ruleLabel = rule == null ? "规则ID " + condition.getRuleId() : label(rule.getRuleName(), rule.getRuleCode());
                    return ruleLabel + " / 条件 " + StringUtils.defaultString(condition.getDisplayName(), condition.getVariableCode());
                })
                .collect(Collectors.toList());
    }

    private List<String> sampleQuantityRulesByVariable(Long sceneId, String variableCode) {
        return ruleMapper.selectList(Wrappers.<CostRule>lambdaQuery()
                        .eq(CostRule::getSceneId, sceneId)
                        .eq(CostRule::getQuantityVariableCode, variableCode)
                        .orderByDesc(CostRule::getPriority)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(rule -> label(rule.getRuleName(), rule.getRuleCode())).collect(Collectors.toList());
    }

    private List<String> sampleVariablesByFormula(Long sceneId, String formulaCode) {
        return variableMapper.selectList(Wrappers.<CostVariable>lambdaQuery()
                        .eq(CostVariable::getSceneId, sceneId)
                        .eq(CostVariable::getFormulaCode, formulaCode)
                        .orderByAsc(CostVariable::getSortNo)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(variable -> label(variable.getVariableName(), variable.getVariableCode())).collect(Collectors.toList());
    }

    private List<String> sampleRulesByFormula(Long sceneId, String formulaCode) {
        return ruleMapper.selectList(Wrappers.<CostRule>lambdaQuery()
                        .eq(CostRule::getSceneId, sceneId)
                        .eq(CostRule::getAmountFormulaCode, formulaCode)
                        .orderByDesc(CostRule::getPriority)
                        .last("limit " + SAMPLE_LIMIT))
                .stream().map(rule -> label(rule.getRuleName(), rule.getRuleCode())).collect(Collectors.toList());
    }

    private List<String> sampleFormulasByVariable(Long sceneId, String variableCode) {
        return formulaMapper.selectList(Wrappers.<CostFormula>lambdaQuery()
                        .eq(CostFormula::getSceneId, sceneId)
                        .orderByAsc(CostFormula::getSortNo)
                        .last("limit " + SAMPLE_LIMIT * 4))
                .stream()
                .filter(formula -> formulaReferencesVariable(formula, variableCode))
                .limit(SAMPLE_LIMIT)
                .map(formula -> label(formula.getFormulaName(), formula.getFormulaCode()))
                .collect(Collectors.toList());
    }

    private boolean formulaReferencesVariable(CostFormula formula, String variableCode) {
        if (formula == null || StringUtils.isEmpty(formula.getFormulaExpr())) {
            return false;
        }
        try {
            CostExpressionAnalysisVo analysis = expressionService.analyzeExpression(formula.getFormulaExpr(), formula.getNamespaceScope());
            return analysis.getVariableReferences().contains(variableCode);
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> sampleConditionsByRule(Long ruleId) {
        return conditionMapper.selectList(Wrappers.<CostRuleCondition>lambdaQuery()
                        .eq(CostRuleCondition::getRuleId, ruleId)
                        .orderByAsc(CostRuleCondition::getGroupNo)
                        .orderByAsc(CostRuleCondition::getSortNo)
                        .last("limit " + SAMPLE_LIMIT))
                .stream()
                .map(condition -> "条件 " + StringUtils.defaultString(condition.getDisplayName(), condition.getVariableCode())
                        + " " + StringUtils.defaultString(condition.getOperatorCode())
                        + " " + StringUtils.defaultString(condition.getCompareValue()))
                .collect(Collectors.toList());
    }

    private List<String> sampleTiersByRule(Long ruleId) {
        return tierMapper.selectList(Wrappers.<CostRuleTier>lambdaQuery()
                        .eq(CostRuleTier::getRuleId, ruleId)
                        .orderByAsc(CostRuleTier::getTierNo)
                        .last("limit " + SAMPLE_LIMIT))
                .stream()
                .map(tier -> "阶梯 " + tier.getTierNo() + " / " + tier.getStartValue() + " - " + tier.getEndValue()
                        + " / 费率 " + tier.getRateValue())
                .collect(Collectors.toList());
    }

    private List<String> sampleTracesByRule(Long ruleId) {
        return resultTraceMapper.selectList(Wrappers.<CostResultTrace>lambdaQuery()
                        .eq(CostResultTrace::getRuleId, ruleId)
                        .orderByDesc(CostResultTrace::getTraceId)
                        .last("limit " + SAMPLE_LIMIT))
                .stream()
                .map(trace -> "追溯ID " + trace.getTraceId() + " / 版本ID " + trace.getVersionId()
                        + (trace.getTierId() == null ? "" : " / 阶梯ID " + trace.getTierId()))
                .collect(Collectors.toList());
    }

    private Map<Long, CostFeeItem> loadFeeMap(Set<Long> feeIds) {
        if (feeIds == null || feeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return feeMapper.selectList(Wrappers.<CostFeeItem>lambdaQuery().in(CostFeeItem::getFeeId, feeIds))
                .stream().collect(Collectors.toMap(CostFeeItem::getFeeId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, CostVariable> loadVariableMap(Set<Long> variableIds) {
        if (variableIds == null || variableIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return variableMapper.selectList(Wrappers.<CostVariable>lambdaQuery().in(CostVariable::getVariableId, variableIds))
                .stream().collect(Collectors.toMap(CostVariable::getVariableId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, CostRule> loadRuleMap(Set<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ruleMapper.selectList(Wrappers.<CostRule>lambdaQuery().in(CostRule::getRuleId, ruleIds))
                .stream().collect(Collectors.toMap(CostRule::getRuleId, Function.identity(), (left, right) -> left));
    }

    private String label(String name, String code) {
        if (StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(code)) {
            return name + "（" + code + "）";
        }
        return StringUtils.isNotEmpty(name) ? name : StringUtils.defaultString(code, "-");
    }

    private String relationTypeLabel(String relationType) {
        if ("REQUIRED".equalsIgnoreCase(relationType)) {
            return "必填输入";
        }
        if ("OPTIONAL".equalsIgnoreCase(relationType)) {
            return "可选输入";
        }
        if ("TIER_BASIS".equalsIgnoreCase(relationType)) {
            return "阶梯依据";
        }
        if ("FORMULA_INPUT".equalsIgnoreCase(relationType)) {
            return "公式输入";
        }
        return StringUtils.defaultString(relationType, "-");
    }

    private String relationSourceTypeLabel(String sourceType) {
        if ("RULE_DERIVED".equalsIgnoreCase(sourceType)) {
            return "规则派生";
        }
        if ("MANUAL_REQUIRED".equalsIgnoreCase(sourceType)) {
            return "手工维护";
        }
        return StringUtils.defaultString(sourceType, "-");
    }

    private String versionStatusLabel(String versionStatus) {
        if ("PUBLISHED".equalsIgnoreCase(versionStatus)) {
            return "已发布";
        }
        if ("ACTIVE".equalsIgnoreCase(versionStatus)) {
            return "生效中";
        }
        if ("ROLLED_BACK".equalsIgnoreCase(versionStatus)) {
            return "已回滚";
        }
        return StringUtils.defaultString(versionStatus, "-");
    }
}
