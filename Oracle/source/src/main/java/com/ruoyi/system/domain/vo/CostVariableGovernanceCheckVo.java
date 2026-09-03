package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 变量治理预检查结果
 *
 * @author HwFan
 */
@Data
public class CostVariableGovernanceCheckVo {
    /**
     * 变量主键
     */
    private Long variableId;

    /**
     * 场景主键
     */
    private Long sceneId;

    /**
     * 场景编码
     */
    private String sceneCode;

    /**
     * 场景名称
     */
    private String sceneName;

    /**
     * 变量编码
     */
    private String variableCode;

    /**
     * 变量名称
     */
    private String variableName;

    /**
     * 状态
     */
    private String status;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 公式编码
     */
    private String formulaCode;

    /**
     * 公式名称
     */
    private String formulaName;

    /**
     * 费用关系引用数量
     */
    private Long feeRelCount;

    /**
     * 规则条件引用数量
     */
    private Long ruleConditionCount;

    /**
     * 规则计量引用数量
     */
    private Long ruleQuantityCount;

    /**
     * 公式引用数量
     */
    private Long formulaRefCount;

    /**
     * 发布版本引用数量
     */
    private Long publishedVersionCount;

    /**
     * 是否允许删除
     */
    private Boolean canDelete;

    /**
     * 是否允许停用
     */
    private Boolean canDisable;

    /**
     * 删除阻断说明
     */
    private String removeBlockingReason;

    /**
     * 停用阻断说明
     */
    private String disableBlockingReason;

    /**
     * 删除治理建议
     */
    private String removeAdvice;

    /**
     * 停用治理建议
     */
    private String disableAdvice;

    /**
     * 关联影响明细
     */
    private List<CostGovernanceImpactVo> impactItems = new ArrayList<>();

    /**
     * 公式变量输入依赖树
     */
    private List<CostVariableFormulaDependencyVo> formulaDependencies = new ArrayList<>();
}
