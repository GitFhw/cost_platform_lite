package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 费用治理预检查结果
 *
 * @author HwFan
 */
@Data
public class CostFeeGovernanceCheckVo {
    /**
     * 费用主键
     */
    private Long feeId;

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
     * 费用编码
     */
    private String feeCode;

    /**
     * 费用名称
     */
    private String feeName;

    /**
     * 业务域
     */
    private String businessDomain;

    /**
     * 场景默认对象维度
     */
    private String sceneDefaultObjectDimension;

    /**
     * 费用分类
     */
    private String feeCategory;

    /**
     * 计价单位
     */
    private String unitCode;

    /**
     * 影响因素摘要
     */
    private String factorSummary;

    /**
     * 适用范围说明
     */
    private String scopeDescription;

    /**
     * 费用对象维度
     */
    private String objectDimension;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 规则引用数量
     */
    private Long ruleCount;

    /**
     * 变量关系数量
     */
    private Long variableRelCount;

    /**
     * 发布版本引用数量
     */
    private Long publishedVersionCount;

    /**
     * 结果台账引用数量
     */
    private Long resultLedgerCount;

    /**
     * 启用规则数量
     */
    private Long enabledRuleCount;

    /**
     * 停用规则数量
     */
    private Long disabledRuleCount;

    /**
     * 有效变量关系数量
     */
    private Long enabledVariableRelCount;

    /**
     * 无效变量关系数量
     */
    private Long invalidVariableRelCount;

    /**
     * 缺少阶梯明细的启用阶梯规则数量
     */
    private Long tierRuleMissingTierCount;

    /**
     * 缺少计量变量的启用规则数量
     */
    private Long missingQuantityVariableCount;

    /**
     * 缺少条件变量的启用规则条件数量
     */
    private Long missingConditionVariableCount;

    /**
     * 缺少公式编码的启用公式规则数量
     */
    private Long missingFormulaCodeCount;

    /**
     * 缺少公式资产的启用公式规则数量
     */
    private Long missingFormulaAssetCount;

    /**
     * 是否具备运行基础
     */
    private Boolean runnable;

    /**
     * 是否可参与下一次发布
     */
    private Boolean publishable;

    /**
     * 运行检查级别：PASS/WARN/BLOCK
     */
    private String runCheckLevel;

    /**
     * 运行检查文案
     */
    private String runCheckLabel;

    /**
     * 运行检查阻断原因
     */
    private List<String> runBlockingReasons = new ArrayList<>();

    /**
     * 运行检查告警原因
     */
    private List<String> runWarningReasons = new ArrayList<>();

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
     * Fee input contracts.
     */
    private List<CostFeeVariableContractVo> variableContracts = new ArrayList<>();

    /**
     * Linked rule summaries.
     */
    private List<CostFeeRuleSummaryVo> ruleSummaries = new ArrayList<>();

    /**
     * Publish version references.
     */
    private List<CostFeePublishRefVo> publishRefs = new ArrayList<>();

    /**
     * Recent result ledger references.
     */
    private List<CostFeeResultRefVo> resultRefs = new ArrayList<>();
}
