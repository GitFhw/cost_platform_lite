package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 核算任务对象 cost_calc_task
 *
 * <p>线程五用该表承接正式核算和批量任务台账。
 * 当前阶段先落单笔正式核算与手工批量任务，后续线程六再增强分布式并发与 Redis 锁。</p>
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_calc_task")
public class CostCalcTask extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 任务主键
     */
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    /**
     * 任务编号
     */
    @TableField("task_no")
    private String taskNo;

    /**
     * 场景主键
     */
    @TableField("scene_id")
    private Long sceneId;

    /**
     * 发布版本主键
     */
    @TableField("version_id")
    private Long versionId;

    /**
     * 任务类型
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 账期
     */
    @TableField("bill_month")
    private String billMonth;

    /**
     * 输入总量
     */
    @TableField("source_count")
    private Integer sourceCount;

    /**
     * 成功数量
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 失败数量
     */
    @TableField("fail_count")
    private Integer failCount;

    /**
     * 任务状态
     */
    @TableField("task_status")
    private String taskStatus;

    /**
     * 任务进度
     */
    @TableField("progress_percent")
    private BigDecimal progressPercent;

    /**
     * 开始时间
     */
    @TableField("started_time")
    private Date startedTime;

    /**
     * 结束时间
     */
    @TableField("finished_time")
    private Date finishedTime;

    /**
     * 总耗时
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 幂等请求号
     */
    @TableField("request_no")
    private String requestNo;

    /**
     * 执行节点
     */
    @TableField("execute_node")
    private String executeNode;

    /**
     * 输入来源类型
     */
    @TableField("input_source_type")
    private String inputSourceType;

    /**
     * 来源批次号
     */
    @TableField("source_batch_no")
    private String sourceBatchNo;

    /**
     * 失败摘要
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 场景编码
     */
    @TableField(exist = false)
    private String sceneCode;

    /**
     * 场景名称
     */
    @TableField(exist = false)
    private String sceneName;

    /**
     * 版本号
     */
    @TableField(exist = false)
    private String versionNo;
}
