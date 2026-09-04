package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostFeeItem;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.vo.CostFeeGovernanceCheckVo;
import com.ruoyi.system.mapper.cost.CostFeeMapper;
import com.ruoyi.system.mapper.cost.CostSceneMapper;
import com.ruoyi.system.service.cost.ICostFeeService;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 费用中心服务实现
 *
 * @author HwFan
 */
@Service
public class CostFeeServiceImpl implements ICostFeeService {
    private static final String DICT_TYPE_FEE_STATUS = "cost_fee_status";
    private static final String DICT_TYPE_UNIT_CODE = "cost_unit_code";

    @Autowired
    private CostFeeMapper feeMapper;

    @Autowired
    private CostSceneMapper sceneMapper;

    @Autowired
    private CostDictionaryProvider dictionaryProvider;

    @Autowired
    private CostGovernanceImpactSupport governanceImpactSupport;

    /**
     * 查询费用列表
     */
    @Override
    public List<CostFeeItem> selectFeeList(CostFeeItem feeItem) {
        return feeMapper.selectFeeList(feeItem);
    }

    /**
     * 查询费用详情
     */
    @Override
    public CostFeeItem selectFeeById(Long feeId) {
        return feeMapper.selectById(feeId);
    }

    /**
     * 查询费用选择框
     */
    @Override
    public List<CostFeeItem> selectFeeOptions(CostFeeItem feeItem) {
        return feeMapper.selectFeeOptions(feeItem);
    }

    /**
     * 查询费用统计
     */
    @Override
    public Map<String, Object> selectFeeStats(CostFeeItem feeItem) {
        Map<String, Object> stats = feeMapper.selectFeeStats(feeItem);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("feeCount", 0);
        result.put("enabledFeeCount", 0);
        result.put("sceneCoverageCount", 0);
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
     * 查询费用治理预检查
     */
    @Override
    public CostFeeGovernanceCheckVo selectFeeGovernanceCheck(Long feeId) {
        CostFeeGovernanceCheckVo check = feeMapper.selectFeeGovernanceCheck(feeId);
        if (StringUtils.isNull(check)) {
            return null;
        }
        normalizeGovernanceCount(check);
        populateRunReadiness(check);

        boolean hasRuleRef = check.getRuleCount() > 0;
        boolean hasPublishedVersionRef = check.getPublishedVersionCount() > 0;
        boolean hasResultRef = check.getResultLedgerCount() > 0;

        check.setCanDelete(!hasRuleRef && !hasPublishedVersionRef && !hasResultRef);
        check.setCanDisable(!hasPublishedVersionRef && !hasResultRef);
        check.setRemoveBlockingReason(buildRemoveBlockingReason(check, hasRuleRef, hasPublishedVersionRef, hasResultRef));
        check.setDisableBlockingReason(buildDisableBlockingReason(check, hasPublishedVersionRef, hasResultRef));
        check.setRemoveAdvice(check.getCanDelete() ? "当前费用未被规则、版本或结果占用，可直接删除。"
                : "请先解除规则依赖、发布版本引用和结果台账影响，再删除费用。");
        check.setDisableAdvice(check.getCanDisable() ? buildDisableAdvice(check)
                : "请先处理发布版本或结果台账引用，再执行停用。");
        check.setImpactItems(governanceImpactSupport.buildFeeImpacts(check));
        check.setVariableContracts(feeMapper.selectFeeVariableContracts(feeId));
        check.setRuleSummaries(feeMapper.selectFeeRuleSummaries(feeId));
        check.setPublishRefs(feeMapper.selectFeePublishRefs(feeId));
        check.setResultRefs(feeMapper.selectFeeResultRefs(feeId));
        return check;
    }

    /**
     * 校验费用编码是否唯一（同一场景内唯一）
     */
    @Override
    public boolean checkFeeCodeUnique(CostFeeItem feeItem) {
        Long feeId = StringUtils.isNull(feeItem.getFeeId()) ? -1L : feeItem.getFeeId();
        Long count = feeMapper.selectCount(Wrappers.<CostFeeItem>lambdaQuery()
                .eq(CostFeeItem::getSceneId, feeItem.getSceneId())
                .eq(CostFeeItem::getFeeCode, feeItem.getFeeCode())
                .ne(feeId.longValue() != -1L, CostFeeItem::getFeeId, feeId));
        return count != null && count > 0 ? UserConstants.NOT_UNIQUE : UserConstants.UNIQUE;
    }

    /**
     * 新增费用
     */
    @Override
    public int insertFee(CostFeeItem feeItem) {
        validateFeeConfig(feeItem);
        if (feeItem.getSortNo() == null) {
            feeItem.setSortNo(10);
        }
        return feeMapper.insert(feeItem);
    }

    /**
     * 修改费用
     */
    @Override
    public int updateFee(CostFeeItem feeItem) {
        validateDisableBeforeUpdate(feeItem);
        validateFeeConfig(feeItem);
        return feeMapper.updateById(feeItem);
    }

    /**
     * 批量停用费用
     */
    @Override
    @Transactional(transactionManager = "costLiteTransactionManager", rollbackFor = Exception.class)
    public int disableFeeByIds(Long[] feeIds) {
        if (feeIds == null || feeIds.length == 0) {
            return 0;
        }
        for (Long feeId : feeIds) {
            CostFeeGovernanceCheckVo check = selectFeeGovernanceCheck(feeId);
            if (StringUtils.isNull(check)) {
                continue;
            }
            if (!Boolean.TRUE.equals(check.getCanDisable())) {
                throw new ServiceException(String.format("%1$s不能停用：%2$s", check.getFeeName(), check.getDisableBlockingReason()));
            }
        }
        return feeMapper.update(null, Wrappers.<CostFeeItem>lambdaUpdate()
                .in(CostFeeItem::getFeeId, Arrays.asList(feeIds))
                .set(CostFeeItem::getStatus, "1")
                .set(CostFeeItem::getUpdateBy, SecurityUtils.getUsername())
                .set(CostFeeItem::getUpdateTime, DateUtils.getNowDate()));
    }

    /**
     * 批量删除费用
     * <p>
     * 删除前先执行治理预检查，避免被规则、版本或结果链路引用时误删。
     */
    @Override
    @Transactional(transactionManager = "costLiteTransactionManager", rollbackFor = Exception.class)
    public int deleteFeeByIds(Long[] feeIds) {
        if (feeIds == null || feeIds.length == 0) {
            return 0;
        }
        for (Long feeId : feeIds) {
            CostFeeGovernanceCheckVo check = selectFeeGovernanceCheck(feeId);
            if (StringUtils.isNull(check)) {
                continue;
            }
            if (!Boolean.TRUE.equals(check.getCanDelete())) {
                throw new ServiceException(String.format("%1$s不能删除：%2$s", check.getFeeName(), check.getRemoveBlockingReason()));
            }
        }
        feeMapper.deleteFeeVariableRelByFeeIds(feeIds);
        return feeMapper.deleteBatchIds(Arrays.asList(feeIds));
    }

    /**
     * 标准化治理计数
     */
    private void normalizeGovernanceCount(CostFeeGovernanceCheckVo check) {
        check.setRuleCount(nullSafeLong(check.getRuleCount()));
        check.setVariableRelCount(nullSafeLong(check.getVariableRelCount()));
        check.setPublishedVersionCount(nullSafeLong(check.getPublishedVersionCount()));
        check.setResultLedgerCount(nullSafeLong(check.getResultLedgerCount()));
        check.setEnabledRuleCount(nullSafeLong(check.getEnabledRuleCount()));
        check.setDisabledRuleCount(nullSafeLong(check.getDisabledRuleCount()));
        check.setEnabledVariableRelCount(nullSafeLong(check.getEnabledVariableRelCount()));
        check.setInvalidVariableRelCount(nullSafeLong(check.getInvalidVariableRelCount()));
        check.setTierRuleMissingTierCount(nullSafeLong(check.getTierRuleMissingTierCount()));
        check.setMissingQuantityVariableCount(nullSafeLong(check.getMissingQuantityVariableCount()));
        check.setMissingConditionVariableCount(nullSafeLong(check.getMissingConditionVariableCount()));
        check.setMissingFormulaCodeCount(nullSafeLong(check.getMissingFormulaCodeCount()));
        check.setMissingFormulaAssetCount(nullSafeLong(check.getMissingFormulaAssetCount()));
    }

    /**
     * 生成费用运行与发布前置结论。
     */
    private void populateRunReadiness(CostFeeGovernanceCheckVo check) {
        List<String> blockingReasons = new ArrayList<>();
        List<String> warningReasons = new ArrayList<>();

        if (!"0".equals(check.getStatus())) {
            blockingReasons.add("费用已停用，不能进入新的发布快照和运行链路。");
        }
        if (check.getEnabledRuleCount() <= 0) {
            blockingReasons.add("当前费用没有启用规则，发布预检会判定为费用规则覆盖缺失。");
        }
        if (check.getTierRuleMissingTierCount() > 0) {
            blockingReasons.add(String.format("存在%1$d条启用阶梯规则缺少启用阶梯明细。", check.getTierRuleMissingTierCount()));
        }
        if (check.getMissingQuantityVariableCount() > 0) {
            blockingReasons.add(String.format("存在%1$d条启用规则的计量变量不存在或未启用。", check.getMissingQuantityVariableCount()));
        }
        if (check.getMissingConditionVariableCount() > 0) {
            blockingReasons.add(String.format("存在%1$d个启用规则条件引用的变量不存在或未启用。", check.getMissingConditionVariableCount()));
        }
        if (check.getMissingFormulaCodeCount() > 0) {
            blockingReasons.add(String.format("存在%1$d条启用公式规则未绑定公式编码。", check.getMissingFormulaCodeCount()));
        }
        if (check.getMissingFormulaAssetCount() > 0) {
            blockingReasons.add(String.format("存在%1$d条启用公式规则引用的公式不存在或未启用。", check.getMissingFormulaAssetCount()));
        }
        if (check.getInvalidVariableRelCount() > 0) {
            warningReasons.add(String.format("存在%1$d个费用输入契约关联的变量不存在或已停用，建议同步修复变量契约。", check.getInvalidVariableRelCount()));
        }
        if (check.getDisabledRuleCount() > 0) {
            warningReasons.add(String.format("当前费用还有%1$d条停用规则，不参与发布和运行。", check.getDisabledRuleCount()));
        }
        if (check.getVariableRelCount() <= 0 && check.getEnabledRuleCount() > 0) {
            warningReasons.add("当前费用暂无输入契约，固定金额类规则可运行，但建议确认是否符合业务口径。");
        }
        if (check.getPublishedVersionCount() <= 0) {
            warningReasons.add("当前费用尚未进入任何发布版本，正式核算前需要先发布场景版本。");
        }

        boolean publishable = blockingReasons.isEmpty();
        boolean runnable = publishable && check.getInvalidVariableRelCount() <= 0;
        check.setPublishable(publishable);
        check.setRunnable(runnable);
        check.setRunBlockingReasons(blockingReasons);
        check.setRunWarningReasons(warningReasons);
        if (!blockingReasons.isEmpty()) {
            check.setRunCheckLevel("BLOCK");
            check.setRunCheckLabel("存在阻断");
        } else if (!warningReasons.isEmpty()) {
            check.setRunCheckLevel("WARN");
            check.setRunCheckLabel("可发布，需确认");
        } else {
            check.setRunCheckLevel("PASS");
            check.setRunCheckLabel("可运行");
        }
    }

    /**
     * 更新前停用校验
     */
    private void validateDisableBeforeUpdate(CostFeeItem feeItem) {
        if (StringUtils.isNull(feeItem.getFeeId()) || !"1".equals(feeItem.getStatus())) {
            return;
        }
        CostFeeItem current = selectFeeById(feeItem.getFeeId());
        if (StringUtils.isNull(current) || "1".equals(current.getStatus())) {
            return;
        }
        CostFeeGovernanceCheckVo check = selectFeeGovernanceCheck(feeItem.getFeeId());
        if (StringUtils.isNotNull(check) && !Boolean.TRUE.equals(check.getCanDisable())) {
            throw new ServiceException(String.format("%1$s不能停用：%2$s", check.getFeeName(), check.getDisableBlockingReason()));
        }
    }

    /**
     * 构造删除阻断说明
     */
    private String buildRemoveBlockingReason(CostFeeGovernanceCheckVo check, boolean hasRuleRef, boolean hasPublishedVersionRef,
                                             boolean hasResultRef) {
        if (!hasRuleRef && !hasPublishedVersionRef && !hasResultRef) {
            return "当前费用未被规则、版本或结果占用";
        }
        StringJoiner joiner = new StringJoiner("；");
        if (hasRuleRef) {
            joiner.add(String.format("已有%1$d条规则引用当前费用", check.getRuleCount()));
        }
        if (hasPublishedVersionRef) {
            joiner.add(String.format("已有%1$d个发布版本快照引用当前费用", check.getPublishedVersionCount()));
        }
        if (hasResultRef) {
            joiner.add(String.format("已有%1$d条结果台账记录绑定当前费用", check.getResultLedgerCount()));
        }
        return joiner.toString();
    }

    /**
     * 构造停用阻断说明
     */
    private String buildDisableBlockingReason(CostFeeGovernanceCheckVo check, boolean hasPublishedVersionRef, boolean hasResultRef) {
        if (!hasPublishedVersionRef && !hasResultRef) {
            return "当前费用未进入发布/结果链路，可安全停用";
        }
        StringJoiner joiner = new StringJoiner("；");
        if (hasPublishedVersionRef) {
            joiner.add(String.format("已有%1$d个发布版本快照引用当前费用", check.getPublishedVersionCount()));
        }
        if (hasResultRef) {
            joiner.add(String.format("已有%1$d条结果台账记录绑定当前费用", check.getResultLedgerCount()));
        }
        return joiner.toString();
    }

    /**
     * 构造停用建议
     */
    private String buildDisableAdvice(CostFeeGovernanceCheckVo check) {
        if (check.getRuleCount() <= 0) {
            return "当前费用无规则挂载，停用后将从维护和计费选择范围中移除。";
        }
        return String.format("当前费用仍挂载%1$d条规则，停用后将不再参与后续配置与计算选择。", check.getRuleCount());
    }

    /**
     * 空值转0
     */
    private long nullSafeLong(Long value) {
        return StringUtils.isNull(value) ? 0L : value.longValue();
    }

    /**
     * 校验费用中心核心配置必须符合线程二治理要求。
     *
     * @param feeItem 费用对象
     */
    private void validateFeeConfig(CostFeeItem feeItem) {
        validateSceneEnabled(feeItem.getSceneId());
        validateDictValueExists(DICT_TYPE_FEE_STATUS, feeItem.getStatus(), "费用状态");
        if (StringUtils.isNotEmpty(feeItem.getUnitCode())) {
            validateDictValueExists(DICT_TYPE_UNIT_CODE, feeItem.getUnitCode(), "计价单位");
        }
    }

    /**
     * 校验费用所属场景必须存在且处于正常状态。
     */
    private void validateSceneEnabled(Long sceneId) {
        if (sceneId == null) {
            throw new ServiceException("费用所属场景不能为空");
        }
        CostScene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new ServiceException("费用所属场景不存在");
        }
        if (!"0".equals(scene.getStatus())) {
            throw new ServiceException("费用所属场景不是正常状态，当前不允许继续维护");
        }
    }

    /**
     * 校验字典值是否存在且启用。
     */
    private void validateDictValueExists(String dictType, String dictValue, String fieldLabel) {
        if (!dictionaryProvider.containsValue(dictType, dictValue)) {
            throw new ServiceException(String.format("%s取值无效，请从系统字典 %s 中选择合法值：%s", fieldLabel, dictType, dictValue));
        }
    }
}
