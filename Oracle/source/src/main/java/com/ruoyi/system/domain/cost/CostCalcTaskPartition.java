package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 正式核算任务分片对象 cost_calc_task_partition
 *
 * <p>分片表用于承接大批量正式核算的调度状态、执行统计和错误摘要，
 * 让任务头、分片、明细三层职责清晰分离，为后续百万级任务重试和监控提供基础。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_calc_task_partition")
public class CostCalcTaskPartition implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分片主键
     */
    @TableId(value = "partition_id", type = IdType.AUTO)
    private Long partitionId;

    /**
     * 任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 任务编号
     */
    @TableField("task_no")
    private String taskNo;

    /**
     * 分片序号
     */
    @TableField("partition_no")
    private Integer partitionNo;

    /**
     * 起始明细序号
     */
    @TableField("start_item_no")
    private Integer startItemNo;

    /**
     * 结束明细序号
     */
    @TableField("end_item_no")
    private Integer endItemNo;

    /**
     * 分片状态
     */
    @TableField("partition_status")
    private String partitionStatus;

    /**
     * 分片总条数
     */
    @TableField("total_count")
    private Integer totalCount;

    /**
     * 分片已处理条数
     */
    @TableField("processed_count")
    private Integer processedCount;

    /**
     * 分片成功条数
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 分片失败条数
     */
    @TableField("fail_count")
    private Integer failCount;

    /**
     * 当前认领执行节点
     */
    @TableField("execute_node")
    private String executeNode;

    /**
     * 最近认领时间
     */
    @TableField("claim_time")
    private Date claimTime;

    /**
     * 分片金额汇总
     */
    @TableField("amount_total")
    private BigDecimal amountTotal;

    /**
     * 结果落库模式
     */
    @TableField("persist_mode")
    private String persistMode;

    /**
     * 恢复提示
     */
    @TableField("recovery_hint")
    private String recoveryHint;

    /**
     * 最近错误阶段
     */
    @TableField("last_error_stage")
    private String lastErrorStage;

    /**
     * 分片开始时间
     */
    @TableField("started_time")
    private Date startedTime;

    /**
     * 分片结束时间
     */
    @TableField("finished_time")
    private Date finishedTime;

    /**
     * 分片耗时毫秒
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 最近错误摘要
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private Date updateTime;
}
