package com.ruoyi.system.domain.cost.bo;

import lombok.Data;

/**
 * Result ledger comparison query.
 *
 * @author HwFan
 */
@Data
public class CostResultCompareBo {
    /** 对比维度：FEE-按费用汇总，OBJECT-按对象维度，RULE-按规则命中 */
    private String compareDimension;

    private String leftSourceType;
    private Long leftTaskId;
    private Long leftSimulationId;
    private Long leftSceneId;
    private Long leftVersionId;
    private String leftBillMonth;

    private String rightSourceType;
    private Long rightTaskId;
    private Long rightSimulationId;
    private Long rightSceneId;
    private Long rightVersionId;
    private String rightBillMonth;
}
