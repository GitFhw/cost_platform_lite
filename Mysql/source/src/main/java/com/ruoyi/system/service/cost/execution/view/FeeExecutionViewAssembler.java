package com.ruoyi.system.service.cost.execution.view;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.cost.execution.model.FeeExecutionResult;
import com.ruoyi.system.service.cost.execution.model.PricingResult;
import com.ruoyi.system.service.cost.execution.model.RuleMatchResult;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.ruoyi.system.service.cost.execution.CostExecutionConstants.RULE_TYPE_FIXED_AMOUNT;

@Component
public class FeeExecutionViewAssembler {

    public FeeExecutionResult buildFeeResult(CostRunServiceImpl.RuntimeFee fee,
                                             RuleMatchResult matchResult,
                                             PricingResult pricingResult,
                                             Map<String, Object> variableValues) {
        FeeExecutionResult feeResult = new FeeExecutionResult();
        feeResult.feeId = fee.feeId;
        feeResult.feeCode = fee.feeCode;
        feeResult.feeName = fee.feeName;
        feeResult.unitCode = fee.unitCode;
        feeResult.objectDimension = fee.objectDimension;
        feeResult.ruleId = matchResult.rule.ruleId;
        feeResult.ruleCode = matchResult.rule.ruleCode;
        feeResult.ruleName = matchResult.rule.ruleName;
        feeResult.tierId = matchResult.tier == null ? null : matchResult.tier.tierId;
        feeResult.quantityValue = pricingResult.quantityValue;
        feeResult.unitPrice = pricingResult.unitPrice;
        feeResult.amountValue = pricingResult.amountValue;
        feeResult.variableExplain = variableValues;
        feeResult.conditionExplain = matchResult.conditionExplain;
        feeResult.pricingExplain = pricingResult.pricingExplain;
        feeResult.timelineSteps = buildFeeTimeline(fee, matchResult, pricingResult);
        return feeResult;
    }

    public Map<String, Object> buildFeeNoMatchExplain(CostRunServiceImpl.RuntimeFee fee,
                                                      List<CostRunServiceImpl.RuntimeRule> rules,
                                                      Map<String, Object> variableValues,
                                                      List<Map<String, Object>> ruleEvaluations) {
        LinkedHashMap<String, Object> explain = new LinkedHashMap<>();
        explain.put("matched", false);
        explain.put("feeCode", fee.feeCode);
        explain.put("feeName", fee.feeName);
        explain.put("attemptedRuleCount", rules == null ? 0 : rules.size());
        explain.put("variables", variableValues);
        explain.put("candidateRules", ruleEvaluations == null ? Collections.emptyList() : ruleEvaluations);
        explain.put("timeline", Collections.singletonList(buildStep("FEE_SKIP", fee.feeCode, fee.feeName,
                rules == null || rules.isEmpty() ? "当前费用在当前运行配置下未挂载启用规则" : "当前费用未命中任何启用规则")));
        return explain;
    }

    public Map<String, Object> buildFeeFailureExplain(CostRunServiceImpl.RuntimeFee fee, Exception exception) {
        LinkedHashMap<String, Object> explain = new LinkedHashMap<>();
        explain.put("matched", false);
        explain.put("feeCode", fee.feeCode);
        explain.put("feeName", fee.feeName);
        explain.put("timeline", Collections.singletonList(buildStep("FEE_FAILED", fee.feeCode, fee.feeName,
                limitLength(exception == null ? "" : exception.getMessage(), 300))));
        return explain;
    }

    public Map<String, Object> buildFeeCalculationExplain(FeeExecutionResult feeResult) {
        LinkedHashMap<String, Object> explain = new LinkedHashMap<>();
        explain.put("matched", true);
        explain.put("feeCode", feeResult.feeCode);
        explain.put("feeName", feeResult.feeName);
        explain.put("ruleId", feeResult.ruleId);
        explain.put("ruleCode", feeResult.ruleCode);
        explain.put("ruleName", feeResult.ruleName);
        explain.put("tierId", feeResult.tierId);
        explain.put("variables", feeResult.variableExplain);
        explain.put("conditions", feeResult.conditionExplain);
        explain.put("pricing", feeResult.pricingExplain);
        explain.put("timeline", feeResult.timelineSteps);
        return explain;
    }

    public Map<String, Object> buildSkipStep(CostRunServiceImpl.RuntimeFee fee) {
        return buildStep("FEE_SKIP", fee.feeCode, fee.feeName, "当前费用未命中任何启用规则");
    }

    public Map<String, Object> buildResultStep(FeeExecutionResult feeResult) {
        return buildStep("FEE_RESULT", feeResult.feeCode, feeResult.feeName,
                String.format(Locale.ROOT, "命中规则 %s，金额 %s", feeResult.ruleCode, feeResult.amountValue));
    }

    public String buildUnitSemanticSummary(String unitCode) {
        if ("吨".equals(unitCode)) {
            return "按重量吨数计价";
        }
        if ("天".equals(unitCode)) {
            return "按天数计价";
        }
        if ("次".equals(unitCode)) {
            return "按次数计价";
        }
        if ("航次".equals(unitCode)) {
            return "按航次计价";
        }
        if ("人".equals(unitCode)) {
            return "按人数计价";
        }
        if ("箱".equals(unitCode)) {
            return "按箱量计价";
        }
        if ("元".equals(unitCode)) {
            return "按固定金额计价";
        }
        if ("平方米*天".equals(unitCode) || "平方米·天".equals(unitCode)) {
            return "按面积天复合量计价";
        }
        return "按当前计价单位口径计价";
    }

    private List<Map<String, Object>> buildFeeTimeline(CostRunServiceImpl.RuntimeFee fee,
                                                       RuleMatchResult matchResult,
                                                       PricingResult pricingResult) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(buildStep("FEE", fee.feeCode, fee.feeName, "进入费用计算"));
        steps.add(buildStep("RULE", matchResult.rule.ruleCode, matchResult.rule.ruleName, "命中规则"));
        if (matchResult.tier != null) {
            steps.add(buildStep("TIER", String.valueOf(matchResult.tier.tierNo), matchResult.tier.buildRangeSummary(), "命中阶梯区间"));
        }
        steps.add(buildStep("PRICING", fee.feeCode, fee.feeName,
                buildPricingStepSummary(fee.unitCode, pricingResult)));
        return steps;
    }

    private String buildPricingStepSummary(String unitCode, PricingResult pricingResult) {
        String pricingSource = pricingResult.pricingExplain == null ? "" : stringValue(pricingResult.pricingExplain.get("pricingSource"));
        if (RULE_TYPE_FIXED_AMOUNT.equals(pricingSource) || "FIXED_AMOUNT".equals(pricingSource) || "元".equals(unitCode)) {
            return String.format(Locale.ROOT, "计价单位 %s，按固定金额计价，结果 %s 元", firstNonBlank(unitCode, "元"), pricingResult.amountValue);
        }
        if (pricingResult.quantityValue == null) {
            return String.format(Locale.ROOT, "计价单位 %s，单价 %s，金额 %s", firstNonBlank(unitCode, "-"), pricingResult.unitPrice, pricingResult.amountValue);
        }
        String normalizedUnit = firstNonBlank(unitCode, "-");
        return String.format(Locale.ROOT, "数量 %s %s，单价 %s 元/%s，金额 %s 元",
                pricingResult.quantityValue,
                normalizedUnit,
                pricingResult.unitPrice,
                normalizedUnit,
                pricingResult.amountValue);
    }

    private Map<String, Object> buildStep(String stepType, String objectCode, String objectName, String resultSummary) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("stepType", stepType);
        item.put("objectCode", objectCode);
        item.put("objectName", objectName);
        item.put("summary", resultSummary);
        return item;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
