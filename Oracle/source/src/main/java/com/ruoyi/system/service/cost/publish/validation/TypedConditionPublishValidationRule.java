package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import com.ruoyi.system.service.cost.execution.CostConditionValueSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 发布前按变量元数据复核条件语义。
 *
 * <p>规则可能来自历史数据、复制接口或直接调用接口，不能只依赖页面下拉。
 * 这里与保存、预演和正式运行共用同一类型化比较器；平台字典值还必须仍处于启用状态。</p>
 */
@Component
public class TypedConditionPublishValidationRule implements PublishValidationRule {
    private final CostDictionaryProvider dictionaryProvider;

    public TypedConditionPublishValidationRule(CostDictionaryProvider dictionaryProvider) {
        this.dictionaryProvider = dictionaryProvider;
    }

    @Override
    public void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        List<String> semanticErrors = new ArrayList<>();
        List<String> dictionaryErrors = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : context.getRuleConditionsByRuleCode().entrySet()) {
            for (Map<String, Object> condition : entry.getValue()) {
                Map<String, Object> variable = context.getVariablesByCode().get(
                        PublishValidationSupport.stringValue(condition.get("variableCode")));
                if (variable == null) {
                    continue;
                }
                String operatorCode = PublishValidationSupport.stringValue(condition.get("operatorCode"))
                        .trim().toUpperCase(Locale.ROOT);
                String variableType = PublishValidationSupport.stringValue(variable.get("variableType"));
                String sourceType = PublishValidationSupport.stringValue(variable.get("sourceType"));
                String optionSourceType = PublishValidationSupport.stringValue(variable.get("optionSourceType"));
                String dictType = PublishValidationSupport.stringValue(variable.get("dictType"));
                String dataType = PublishValidationSupport.stringValue(variable.get("dataType"));
                CostConditionValueSupport.ValueKind kind = CostConditionValueSupport.classify(
                        variableType, sourceType, optionSourceType, dictType, dataType);
                Map<String, Object> rule = context.getRulesByCode().get(entry.getKey());
                String ruleLabel = rule == null
                        ? entry.getKey()
                        : PublishValidationSupport.assetLabel(rule, "ruleName", "ruleCode");
                String conditionLabel = PublishValidationSupport.firstNonBlank(
                        PublishValidationSupport.stringValue(condition.get("displayName")),
                        PublishValidationSupport.stringValue(condition.get("variableCode")));
                String label = ruleLabel + " -> " + conditionLabel;
                String compareValue = PublishValidationSupport.stringValue(condition.get("compareValue"));
                if (!CostConditionValueSupport.isOperatorAllowed(operatorCode, kind)
                        || !CostConditionValueSupport.isValidCompareValue(compareValue, operatorCode, dataType,
                        variableType, sourceType, optionSourceType, dictType)) {
                    semanticErrors.add(label + "[" + operatorCode + "]");
                    continue;
                }
                validatePlatformDictionary(optionSourceType, sourceType, dictType, operatorCode,
                        compareValue, label, dictionaryErrors);
            }
        }
        if (semanticErrors.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("PASS", "RULE_CONDITION_TYPED_OK", "条件类型检查",
                    "规则条件的操作符和值均符合要素类型定义。"));
        } else {
            items.add(PublishValidationSupport.checkItem("BLOCK", "RULE_CONDITION_TYPED_INVALID", "条件类型检查",
                    "以下条件的操作符或比较值与要素类型不匹配：" + String.join("、", semanticErrors)));
        }
        if (dictionaryErrors.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("PASS", "RULE_CONDITION_DICTIONARY_OK", "条件字典检查",
                    "平台字典条件值均来自当前启用字典。"));
        } else {
            items.add(PublishValidationSupport.checkItem("BLOCK", "RULE_CONDITION_DICTIONARY_INVALID", "条件字典检查",
                    "以下条件引用了不存在或已停用的平台字典值：" + String.join("、", dictionaryErrors)));
        }
    }

    private void validatePlatformDictionary(String optionSourceType, String sourceType, String dictType,
                                            String operatorCode, String compareValue, String label,
                                            List<String> errors) {
        boolean platformDictionary = "PLATFORM_DICT".equalsIgnoreCase(optionSourceType)
                || (StringUtils.isEmpty(optionSourceType) && "DICT".equalsIgnoreCase(sourceType));
        if (!platformDictionary || !CostConditionValueSupport.requiresCompareValue(operatorCode)) {
            return;
        }
        if (StringUtils.isEmpty(dictType) || !dictionaryProvider.containsType(dictType)) {
            errors.add(label + " -> 字典类型 " + StringUtils.defaultIfEmpty(dictType, "未配置"));
            return;
        }
        for (String value : CostConditionValueSupport.splitValues(compareValue)) {
            if (!dictionaryProvider.containsValue(dictType, value)) {
                errors.add(label + " -> " + dictType + ":" + value);
            }
        }
    }
}
