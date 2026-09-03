package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则治理预检查结果
 *
 * @author HwFan
 */
@Data
public class CostRuleGovernanceCheckVo {
    private Long ruleId;
    private Long sceneId;
    private Long feeId;
    private String sceneCode;
    private String sceneName;
    private String feeCode;
    private String feeName;
    private String ruleCode;
    private String ruleName;
    private String status;
    private Long conditionCount;
    private Long tierCount;
    private Long publishedVersionCount;
    private Long traceCount;
    private Boolean canDelete;
    private Boolean canDisable;
    private String removeBlockingReason;
    private String disableBlockingReason;
    private String removeAdvice;
    private String disableAdvice;
    private List<CostGovernanceImpactVo> impactItems = new ArrayList<>();
}
