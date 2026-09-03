package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 重算申请与执行对象 cost_recalc_order
 *
 * <p>线程六将历史账期重算拆成申请、审核、执行和差异记录四段，
 * 避免已封存账期被静默覆盖。</p>
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_recalc_order")
public class CostRecalcOrder extends BaseEntity {
    @TableId(value = "recalc_id", type = IdType.AUTO)
    private Long recalcId;

    @TableField("scene_id")
    private Long sceneId;

    @Excel(name = "账期")
    @TableField("bill_month")
    private String billMonth;

    @TableField("version_id")
    private Long versionId;

    @TableField("period_id")
    private Long periodId;

    @TableField("baseline_task_id")
    private Long baselineTaskId;

    @Excel(name = "基准任务")
    @TableField("baseline_task_no")
    private String baselineTaskNo;

    @TableField("target_task_id")
    private Long targetTaskId;

    @Excel(name = "重算任务")
    @TableField("target_task_no")
    private String targetTaskNo;

    @Excel(name = "重算状态", dictType = "cost_recalc_status")
    @TableField("recalc_status")
    private String recalcStatus;

    @Excel(name = "申请原因")
    @TableField("apply_reason")
    private String applyReason;

    @TableField("approve_opinion")
    private String approveOpinion;

    @TableField("diff_summary_json")
    private String diffSummaryJson;

    @Excel(name = "差异金额")
    @TableField("diff_amount")
    private BigDecimal diffAmount;

    @Excel(name = "请求号")
    @TableField("request_no")
    private String requestNo;

    @Excel(name = "审核人")
    @TableField("approve_by")
    private String approveBy;

    @Excel(name = "审核时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @TableField("approve_time")
    private Date approveTime;

    @Excel(name = "执行人")
    @TableField("execute_by")
    private String executeBy;

    @Excel(name = "执行时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @TableField("execute_time")
    private Date executeTime;

    @Excel(name = "完成时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @TableField("finish_time")
    private Date finishTime;

    @Excel(name = "场景编码")
    @TableField(exist = false)
    private String sceneCode;

    @Excel(name = "场景名称")
    @TableField(exist = false)
    private String sceneName;

    @Excel(name = "目标版本")
    @TableField(exist = false)
    private String versionNo;
}
