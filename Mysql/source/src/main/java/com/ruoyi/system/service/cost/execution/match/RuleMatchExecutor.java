package com.ruoyi.system.service.cost.execution.match;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.cost.execution.model.ConditionMatchResult;
import com.ruoyi.system.service.cost.execution.model.RuleMatchResult;
import com.ruoyi.system.service.cost.execution.node.PricingSupport;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

import static com.ruoyi.system.service.cost.execution.CostExecutionConstants.*;

@Component
public class RuleMatchExecutor {

    public RuleMatchResult matchRule(List<CostRunServiceImpl.RuntimeRule> rules,
                                     Map<String, Object> variableValues,
                                     Map<String, Object> baseContext,
                                     Map<String, CostRunServiceImpl.RuntimeVariable> variableMap,
                                     boolean includeExplain,
                                     PricingSupport support) {
        Map<String, Object> conditionContext = support.mergeContext(baseContext, variableValues, Collections.emptyMap());
        RuleMatchResult fallbackResult = includeExplain ? new RuleMatchResult() : null;
        for (CostRunServiceImpl.RuntimeRule rule : rules) {
            List<Map<String, Object>> conditionExplain = new ArrayList<>();
            ConditionMatchResult conditionMatchResult = matchConditions(rule, variableValues, conditionContext, conditionExplain, support);
            if (conditionMatchResult.matched && !matchQuantityInputPresence(rule, baseContext, variableMap, conditionExplain)) {
                conditionMatchResult.matched = false;
                conditionMatchResult.matchedGroupNo = null;
            }
            if (includeExplain) {
                fallbackResult.ruleEvaluations.add(buildRuleEvaluation(rule, conditionMatchResult, conditionExplain));
            }
            if (conditionMatchResult.matched) {
                RuleMatchResult result = new RuleMatchResult();
                result.rule = rule;
                result.matchedGroupNo = conditionMatchResult.matchedGroupNo;
                result.rule.matchedGroupNo = conditionMatchResult.matchedGroupNo;
                result.conditionExplain = conditionExplain;
                result.ruleEvaluations = includeExplain ? fallbackResult.ruleEvaluations : Collections.emptyList();
                if (RULE_TYPE_TIER_RATE.equals(rule.ruleType)) {
                    BigDecimal quantityValue = support.toBigDecimal(variableValues.get(rule.quantityVariableCode));
                    result.tier = locateTier(rule.tiers, quantityValue);
                    if (result.tier == null) {
                        throw new ServiceException(String.format("规则 %s 未找到可命中的阶梯区间", rule.ruleCode));
                    }
                }
                return result;
            }
        }
        return fallbackResult;
    }

    private boolean matchQuantityInputPresence(CostRunServiceImpl.RuntimeRule rule,
                                               Map<String, Object> baseContext,
                                               Map<String, CostRunServiceImpl.RuntimeVariable> variableMap,
                                               List<Map<String, Object>> explain) {
        if (StringUtils.isEmpty(rule.quantityVariableCode)) {
            return true;
        }
        CostRunServiceImpl.RuntimeVariable quantityVariable = variableMap == null
                ? null
                : variableMap.get(rule.quantityVariableCode);
        if (quantityVariable == null || !"INPUT".equalsIgnoreCase(quantityVariable.sourceType)) {
            return true;
        }
        boolean provided = hasInputPath(baseContext, quantityVariable.variableCode)
                || hasInputPath(baseContext, quantityVariable.dataPath);
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("groupNo", null);
        item.put("displayName", "数量变量输入存在校验");
        item.put("variableCode", rule.quantityVariableCode);
        item.put("leftValue", resolveInputValue(baseContext, quantityVariable));
        item.put("operatorCode", "PRESENT");
        item.put("compareValue", true);
        item.put("pass", provided);
        item.put("systemCheck", true);
        explain.add(item);
        return provided;
    }

    private Map<String, Object> buildRuleEvaluation(CostRunServiceImpl.RuntimeRule rule,
                                                    ConditionMatchResult matchResult,
                                                    List<Map<String, Object>> conditionExplain) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("ruleCode", rule.ruleCode);
        item.put("ruleName", rule.ruleName);
        item.put("ruleType", rule.ruleType);
        item.put("pricingMode", rule.pricingMode);
        item.put("priority", rule.priority);
        item.put("matched", matchResult.matched);
        item.put("matchedGroupNo", matchResult.matchedGroupNo);
        item.put("conditions", conditionExplain);
        return item;
    }

    private ConditionMatchResult matchConditions(CostRunServiceImpl.RuntimeRule rule,
                                                 Map<String, Object> variableValues,
                                                 Map<String, Object> conditionContext,
                                                 List<Map<String, Object>> explain,
                                                 PricingSupport support) {
        ConditionMatchResult result = new ConditionMatchResult();
        if (rule.conditionGroups == null || rule.conditionGroups.isEmpty()) {
            result.matched = true;
            result.matchedGroupNo = 1;
            return result;
        }
        List<Boolean> groupResults = new ArrayList<>(rule.conditionGroups.size());
        for (CostRunServiceImpl.RuntimeConditionGroup group : rule.conditionGroups) {
            boolean groupPass = true;
            for (CostRunServiceImpl.RuntimeCondition condition : group.conditions) {
                Object leftValue = variableValues.get(condition.variableCode);
                boolean pass = evaluateCondition(condition, leftValue, conditionContext, support);
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("groupNo", group.groupNo);
                item.put("displayName", condition.displayName);
                item.put("variableCode", condition.variableCode);
                item.put("leftValue", leftValue);
                item.put("operatorCode", condition.operatorCode);
                item.put("compareValue", condition.compareValue);
                item.put("pass", pass);
                explain.add(item);
                groupPass = groupPass && pass;
            }
            groupResults.add(groupPass);
            if (groupPass && result.matchedGroupNo == null) {
                result.matchedGroupNo = group.groupNo;
            }
        }
        if ("OR".equalsIgnoreCase(rule.conditionLogic)) {
            result.matched = groupResults.stream().anyMatch(Boolean::booleanValue);
            if (!result.matched) {
                result.matchedGroupNo = null;
            }
            return result;
        }
        result.matched = groupResults.stream().allMatch(Boolean::booleanValue);
        if (!result.matched) {
            result.matchedGroupNo = null;
        }
        return result;
    }

    private boolean evaluateCondition(CostRunServiceImpl.RuntimeCondition condition,
                                      Object leftValue,
                                      Map<String, Object> context,
                                      PricingSupport support) {
        String operatorCode = condition.operatorCode;
        if (OP_EXPR.equals(operatorCode)) {
            Object exprResult = support.evaluateExpression(condition.compareValue, context);
            return Boolean.TRUE.equals(convertBoolean(exprResult));
        }
        if (OP_IN.equals(operatorCode) || OP_NOT_IN.equals(operatorCode)) {
            List<String> values = splitValues(condition.compareValue);
            boolean contains = values.contains(String.valueOf(leftValue));
            return OP_IN.equals(operatorCode) ? contains : !contains;
        }
        if (OP_BETWEEN.equals(operatorCode)) {
            List<String> values = splitValues(condition.compareValue);
            if (values.size() < 2) {
                return false;
            }
            BigDecimal left = support.toBigDecimal(leftValue);
            BigDecimal start = support.toBigDecimal(values.get(0));
            BigDecimal end = support.toBigDecimal(values.get(1));
            if (left == null || start == null || end == null) {
                return false;
            }
            return left.compareTo(start) >= 0 && left.compareTo(end) <= 0;
        }
        BigDecimal leftNumber = support.toBigDecimal(leftValue);
        BigDecimal rightNumber = support.toBigDecimal(condition.compareValue);
        switch (operatorCode) {
            case OP_EQ:
                return Objects.equals(String.valueOf(leftValue), String.valueOf(condition.compareValue));
            case OP_NE:
                return !Objects.equals(String.valueOf(leftValue), String.valueOf(condition.compareValue));
            case OP_GT:
                return leftNumber != null && rightNumber != null && leftNumber.compareTo(rightNumber) > 0;
            case OP_GE:
                return leftNumber != null && rightNumber != null && leftNumber.compareTo(rightNumber) >= 0;
            case OP_LT:
                return leftNumber != null && rightNumber != null && leftNumber.compareTo(rightNumber) < 0;
            case OP_LE:
                return leftNumber != null && rightNumber != null && leftNumber.compareTo(rightNumber) <= 0;
            default:
                return false;
        }
    }

    private CostRunServiceImpl.RuntimeTier locateTier(List<CostRunServiceImpl.RuntimeTier> tiers, BigDecimal quantityValue) {
        if (quantityValue == null || tiers == null) {
            return null;
        }
        for (CostRunServiceImpl.RuntimeTier tier : tiers) {
            BigDecimal start = tier.startValue;
            BigDecimal end = tier.endValue;
            boolean pass;
            if (INTERVAL_LORC.equals(tier.intervalMode)) {
                pass = (start == null || quantityValue.compareTo(start) > 0)
                        && (end == null || quantityValue.compareTo(end) <= 0);
            } else {
                pass = (start == null || quantityValue.compareTo(start) >= 0)
                        && (end == null || quantityValue.compareTo(end) < 0);
            }
            if (pass) {
                return tier;
            }
        }
        return null;
    }

    private Boolean convertBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }

    private List<String> splitValues(String compareValue) {
        if (StringUtils.isEmpty(compareValue)) {
            return Collections.emptyList();
        }
        String[] segments = compareValue.split(",");
        List<String> result = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (StringUtils.isNotEmpty(StringUtils.trim(segment))) {
                result.add(StringUtils.trim(segment));
            }
        }
        return result;
    }

    private Object resolveInputValue(Map<String, Object> input, CostRunServiceImpl.RuntimeVariable variable) {
        if (variable == null) {
            return null;
        }
        Object value = resolveByPath(input, variable.variableCode);
        if (value == null) {
            value = resolveByPath(input, variable.dataPath);
        }
        return value;
    }

    private boolean hasInputPath(Map<String, Object> input, String path) {
        return resolveByPath(input, path) != null;
    }

    private Object resolveByPath(Map<String, Object> input, String path) {
        if (input == null || StringUtils.isEmpty(path)) {
            return null;
        }
        String[] pieces = path.split("\\.");
        Object current = input;
        for (String piece : pieces) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(piece);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
