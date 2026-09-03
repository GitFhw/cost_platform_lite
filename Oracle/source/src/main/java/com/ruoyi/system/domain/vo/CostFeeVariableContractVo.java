package com.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * Fee input contract item shown in governance checks.
 */
@Data
public class CostFeeVariableContractVo {
    private Long relId;
    private Long sceneId;
    private Long feeId;
    private Long variableId;
    private String variableCode;
    private String variableName;
    private String variableType;
    private String variableSourceType;
    private String dataType;
    private String dataPath;
    private String variableStatus;
    private String relationType;
    private String sourceType;
    private Long sourceRuleId;
    private String sourceCode;
    private String sourceRuleCode;
    private String sourceRuleName;
    private Integer sortNo;
    private String remark;
}
