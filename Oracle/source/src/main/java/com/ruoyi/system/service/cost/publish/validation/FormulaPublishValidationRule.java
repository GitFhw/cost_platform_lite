package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import com.ruoyi.system.service.cost.engine.FormulaDependencyCheckResult;
import com.ruoyi.system.service.cost.engine.FormulaDependencyGraphService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FormulaPublishValidationRule implements PublishValidationRule {
    private final FormulaDependencyGraphService formulaDependencyGraphService;

    public FormulaPublishValidationRule(FormulaDependencyGraphService formulaDependencyGraphService) {
        this.formulaDependencyGraphService = formulaDependencyGraphService;
    }

    @Override
    public void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        List<String> variableMissingCode = new ArrayList<>();
        List<String> variableMissingAsset = new ArrayList<>();
        int formulaVariableCount = 0;
        for (Map<String, Object> variable : context.getVariablesByCode().values()) {
            if (!"FORMULA".equalsIgnoreCase(PublishValidationSupport.stringValue(variable.get("sourceType")))) {
                continue;
            }
            formulaVariableCount++;
            String formulaCode = StringUtils.trim(PublishValidationSupport.stringValue(variable.get("formulaCode")));
            if (StringUtils.isEmpty(formulaCode)) {
                variableMissingCode.add(PublishValidationSupport.assetLabel(variable, "variableName", "variableCode"));
                continue;
            }
            if (!context.getFormulasByCode().containsKey(formulaCode)) {
                variableMissingAsset.add(PublishValidationSupport.assetLabel(variable, "variableName", "variableCode") + " -> " + formulaCode);
            }
        }

        List<String> ruleMissingCode = new ArrayList<>();
        List<String> ruleMissingAsset = new ArrayList<>();
        int formulaRuleCount = 0;
        for (Map<String, Object> rule : context.getRulesByCode().values()) {
            if (!"FORMULA".equalsIgnoreCase(PublishValidationSupport.stringValue(rule.get("ruleType")))) {
                continue;
            }
            formulaRuleCount++;
            String formulaCode = StringUtils.trim(PublishValidationSupport.stringValue(rule.get("amountFormulaCode")));
            if (StringUtils.isEmpty(formulaCode)) {
                ruleMissingCode.add(PublishValidationSupport.assetLabel(rule, "ruleName", "ruleCode"));
                continue;
            }
            if (!context.getFormulasByCode().containsKey(formulaCode)) {
                ruleMissingAsset.add(PublishValidationSupport.assetLabel(rule, "ruleName", "ruleCode") + " -> " + formulaCode);
            }
        }

        appendFormulaReferenceItems(items, variableMissingCode, variableMissingAsset, ruleMissingCode, ruleMissingAsset);
        appendFormulaDependencyItems(items, formulaDependencyGraphService.inspectPublishBundle(
                context.getVariablesByCode(), context.getFormulasByCode(), context.getFeesByCode()));
        appendFormulaReferencePassItem(items, variableMissingCode, variableMissingAsset, ruleMissingCode, ruleMissingAsset,
                formulaVariableCount, formulaRuleCount);
    }

    private void appendFormulaReferenceItems(List<CostPublishCheckItemVo> items,
                                             List<String> variableMissingCode,
                                             List<String> variableMissingAsset,
                                             List<String> ruleMissingCode,
                                             List<String> ruleMissingAsset) {
        if (!variableMissingCode.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "FORMULA_VARIABLE_CODE_MISSING", "公式编码治理",
                    "以下公式变量仍未绑定公式编码，发布前请先在公式实验室沉淀后重新选择：" + String.join("、", variableMissingCode)));
        }
        if (!variableMissingAsset.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "FORMULA_VARIABLE_ASSET_MISSING", "公式编码治理",
                    "以下公式变量引用的公式编码当前场景不存在或未启用：" + String.join("、", variableMissingAsset)));
        }
        if (!ruleMissingCode.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "FORMULA_RULE_CODE_MISSING", "公式编码治理",
                    "以下公式金额规则仍未绑定金额公式编码，发布前请先在公式实验室选择金额公式：" + String.join("、", ruleMissingCode)));
        }
        if (!ruleMissingAsset.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "FORMULA_RULE_ASSET_MISSING", "公式编码治理",
                    "以下公式金额规则引用的金额公式编码当前场景不存在或未启用：" + String.join("、", ruleMissingAsset)));
        }
    }

    private void appendFormulaDependencyItems(List<CostPublishCheckItemVo> items, FormulaDependencyCheckResult dependencyResult) {
        if (dependencyResult == null) {
            return;
        }
        if (dependencyResult.getMissingVariableRefs() != null && !dependencyResult.getMissingVariableRefs().isEmpty()) {
            List<String> messages = new ArrayList<>();
            dependencyResult.getMissingVariableRefs().forEach((variableCode, missingRefs) ->
                    messages.add(variableCode + " -> " + String.join(", ", missingRefs)));
            items.add(PublishValidationSupport.checkItem("BLOCK", "FORMULA_DEPENDENCY_VARIABLE_MISSING", "公式依赖图校验",
                    "以下公式变量引用了当前场景不存在的变量编码：" + String.join("；", messages)));
        }
        if (dependencyResult.getMissingFeeRefs() != null && !dependencyResult.getMissingFeeRefs().isEmpty()) {
            List<String> messages = new ArrayList<>();
            dependencyResult.getMissingFeeRefs().forEach((variableCode, missingRefs) ->
                    messages.add(variableCode + " -> " + String.join(", ", missingRefs)));
            items.add(PublishValidationSupport.checkItem("BLOCK", "FORMULA_DEPENDENCY_FEE_MISSING", "公式依赖图校验",
                    "以下公式变量引用了当前场景不存在的费用编码：" + String.join("；", messages)));
        }
        if (dependencyResult.getFormulaVariableCycles() != null && !dependencyResult.getFormulaVariableCycles().isEmpty()) {
            List<String> messages = dependencyResult.getFormulaVariableCycles().stream()
                    .map(path -> String.join(" -> ", path))
                    .collect(Collectors.toList());
            items.add(PublishValidationSupport.checkItem("BLOCK", "FORMULA_DEPENDENCY_CYCLE", "公式依赖图校验",
                    "以下公式变量存在循环依赖：" + String.join("；", messages)));
        }
        if ((dependencyResult.getMissingVariableRefs() == null || dependencyResult.getMissingVariableRefs().isEmpty())
                && (dependencyResult.getMissingFeeRefs() == null || dependencyResult.getMissingFeeRefs().isEmpty())
                && (dependencyResult.getFormulaVariableCycles() == null || dependencyResult.getFormulaVariableCycles().isEmpty())) {
            items.add(PublishValidationSupport.checkItem("PASS", "FORMULA_DEPENDENCY_OK", "公式依赖图校验",
                    "当前草稿中的公式变量依赖关系完整，未发现缺失引用或循环依赖。"));
        }
    }

    private void appendFormulaReferencePassItem(List<CostPublishCheckItemVo> items,
                                                List<String> variableMissingCode,
                                                List<String> variableMissingAsset,
                                                List<String> ruleMissingCode,
                                                List<String> ruleMissingAsset,
                                                int formulaVariableCount,
                                                int formulaRuleCount) {
        if (!variableMissingCode.isEmpty() || !variableMissingAsset.isEmpty()
                || !ruleMissingCode.isEmpty() || !ruleMissingAsset.isEmpty()) {
            return;
        }
        if ((formulaVariableCount > 0 || formulaRuleCount > 0) && !hasFormulaDependencyBlockingItem(items)) {
            items.add(PublishValidationSupport.checkItem("PASS", "FORMULA_REFERENCE_OK", "公式编码治理",
                    String.format(Locale.ROOT, "当前场景 %1$d 个公式变量、%2$d 条公式金额规则均已绑定有效公式编码。",
                            formulaVariableCount, formulaRuleCount)));
            return;
        }
        if (formulaVariableCount <= 0 && formulaRuleCount <= 0) {
            items.add(PublishValidationSupport.checkItem("PASS", "FORMULA_REFERENCE_EMPTY", "公式编码治理",
                    "当前场景暂无公式变量或公式金额规则，无需执行公式编码治理检查。"));
        }
    }

    private boolean hasFormulaDependencyBlockingItem(List<CostPublishCheckItemVo> items) {
        return items.stream().anyMatch(item -> "BLOCK".equals(item.getLevel())
                && StringUtils.startsWith(item.getCode(), "FORMULA_DEPENDENCY_"));
    }
}
