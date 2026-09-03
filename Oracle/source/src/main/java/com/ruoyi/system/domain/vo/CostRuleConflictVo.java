package com.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * 规则冲突检测结果。
 */
@Data
public class CostRuleConflictVo {
    /**
     * 冲突类型
     */
    private String conflictType;

    /**
     * 提示等级
     */
    private String severity;

    /**
     * 提示文案
     */
    private String message;

    /**
     * 当前规则编码
     */
    private String ruleCode;

    /**
     * 冲突规则主键
     */
    private Long targetRuleId;

    /**
     * 冲突规则编码
     */
    private String targetRuleCode;

    /**
     * 冲突规则名称
     */
    private String targetRuleName;

    /**
     * 冲突规则优先级
     */
    private Integer targetPriority;
}
