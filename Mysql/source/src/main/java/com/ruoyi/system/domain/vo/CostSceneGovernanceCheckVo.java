package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景治理预检查结果
 *
 * @author HwFan
 */
@Data
public class CostSceneGovernanceCheckVo {
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
     * 业务域
     */
    private String businessDomain;

    /**
     * Default object dimension.
     */
    private String defaultObjectDimension;

    /**
     * 场景状态
     */
    private String status;

    /**
     * 当前生效版本
     */
    private Long activeVersionId;

    /**
     * Active version number.
     */
    private String activeVersionNo;

    /**
     * 费用数量
     */
    private Long feeCount;

    /**
     * 变量组数量
     */
    private Long variableGroupCount;

    /**
     * 变量数量
     */
    private Long variableCount;

    /**
     * 规则数量
     */
    private Long ruleCount;

    /**
     * 已发布版本数量
     */
    private Long publishedVersionCount;

    /**
     * Calculation task count.
     */
    private Long taskCount;

    /**
     * Running calculation task count.
     */
    private Long runningTaskCount;

    /**
     * Failed calculation task count.
     */
    private Long failedTaskCount;

    /**
     * Result ledger count.
     */
    private Long resultLedgerCount;

    /**
     * 同步试算记录数量。
     */
    private Long simulationRecordCount;

    /**
     * 正式核算输入批次数量。
     */
    private Long inputBatchCount;

    /**
     * 结果追溯记录数量。
     */
    private Long traceCount;

    /**
     * 配置对象总数
     */
    private Long totalConfigCount;

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
     * Recent calculation tasks.
     */
    private List<CostSceneRecentTaskVo> recentTasks = new ArrayList<>();
}
