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
 * 账期治理对象 cost_bill_period
 *
 * <p>线程六使用该表承接账期状态、封存控制、最近任务与结果汇总，
 * 为重算治理、账期封存和差异分析提供统一入口。</p>
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_bill_period")
public class CostBillPeriod extends BaseEntity {
    @TableId(value = "period_id", type = IdType.AUTO)
    private Long periodId;

    @TableField("scene_id")
    private Long sceneId;

    @Excel(name = "账期")
    @TableField("bill_month")
    private String billMonth;

    @Excel(name = "账期状态", dictType = "cost_bill_period_status")
    @TableField("period_status")
    private String periodStatus;

    @TableField("active_version_id")
    private Long activeVersionId;

    @Excel(name = "结果条数")
    @TableField("result_count")
    private Long resultCount;

    @Excel(name = "金额汇总")
    @TableField("amount_total")
    private BigDecimal amountTotal;

    @TableField("last_task_id")
    private Long lastTaskId;

    @Excel(name = "最近任务")
    @TableField("last_task_no")
    private String lastTaskNo;

    @Excel(name = "封存人")
    @TableField("sealed_by")
    private String sealedBy;

    @Excel(name = "封存时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @TableField("sealed_time")
    private Date sealedTime;

    @Excel(name = "场景编码")
    @TableField(exist = false)
    private String sceneCode;

    @Excel(name = "场景名称")
    @TableField(exist = false)
    private String sceneName;

    @Excel(name = "默认版本")
    @TableField(exist = false)
    private String versionNo;
}
