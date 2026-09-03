package com.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * 费用详情中的规则摘要。
 *
 * @author HwFan
 */
@Data
public class CostFeeRuleSummaryVo {
    private Long ruleId;
    private Long sceneId;
    private Long feeId;
    private String ruleCode;
    private String ruleName;
    private String ruleType;
    private String pricingMode;
    private Integer priority;
    private String quantityVariableCode;
    private String amountFormulaCode;
    private String status;
    private Integer conditionCount;
    private Integer tierCount;
}
