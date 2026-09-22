package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedConditionPublishValidationRuleTest {
    @Test
    void publishPrecheckBlocksDisabledPlatformDictionaryValue() {
        CostDictionaryProvider dictionaryProvider = new CostDictionaryProvider() {
            @Override
            public boolean containsType(String dictType) {
                return "cost_trade_type".equals(dictType);
            }

            @Override
            public boolean containsValue(String dictType, String dictValue) {
                return "cost_trade_type".equals(dictType) && "OUT".equals(dictValue);
            }
        };
        TypedConditionPublishValidationRule rule = new TypedConditionPublishValidationRule(dictionaryProvider);
        PublishValidationContext context = context(
                variable("tradeType", "DICT", "DICT", "PLATFORM_DICT", "cost_trade_type", "STRING"),
                condition("R1", "tradeType", "EQ", "DISABLED"));
        List<CostPublishCheckItemVo> items = new ArrayList<>();

        rule.validate(context, items);

        assertTrue(items.stream().anyMatch(item -> "RULE_CONDITION_DICTIONARY_INVALID".equals(item.getCode())));
        assertTrue(items.stream().anyMatch(item -> "BLOCK".equals(item.getLevel())));
    }

    @Test
    void publishPrecheckUsesTypedDateComparison() {
        TypedConditionPublishValidationRule rule = new TypedConditionPublishValidationRule(alwaysValidDictionary());
        PublishValidationContext context = context(
                variable("workDate", "DATE", "INPUT", "NONE", "", "DATE"),
                condition("R1", "workDate", "BETWEEN", "2026-09-01,2026-09-30"));
        List<CostPublishCheckItemVo> items = new ArrayList<>();

        rule.validate(context, items);

        assertTrue(items.stream().noneMatch(item -> "BLOCK".equals(item.getLevel())));
    }

    private static PublishValidationContext context(Map<String, Object> variable,
                                                    Map<String, Object> condition) {
        PublishValidationContext context = new PublishValidationContext();
        context.setVariablesByCode(Map.of(String.valueOf(variable.get("variableCode")), variable));
        context.setRulesByCode(Map.of("R1", Map.of("ruleCode", "R1", "ruleName", "测试规则")));
        context.setRuleConditionsByRuleCode(Map.of("R1", List.of(condition)));
        return context;
    }

    private static Map<String, Object> variable(String code, String variableType, String sourceType,
                                                String optionSourceType, String dictType, String dataType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("variableCode", code);
        result.put("variableType", variableType);
        result.put("sourceType", sourceType);
        result.put("optionSourceType", optionSourceType);
        result.put("dictType", dictType);
        result.put("dataType", dataType);
        return result;
    }

    private static Map<String, Object> condition(String ruleCode, String variableCode,
                                                 String operatorCode, String compareValue) {
        return Map.of("ruleCode", ruleCode, "variableCode", variableCode,
                "displayName", variableCode, "operatorCode", operatorCode,
                "compareValue", compareValue);
    }

    private static CostDictionaryProvider alwaysValidDictionary() {
        return new CostDictionaryProvider() {
            @Override
            public boolean containsType(String dictType) {
                return true;
            }

            @Override
            public boolean containsValue(String dictType, String dictValue) {
                return true;
            }
        };
    }
}
