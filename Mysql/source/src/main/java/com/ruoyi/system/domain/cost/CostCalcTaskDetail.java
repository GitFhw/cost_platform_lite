package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 核算任务明细对象 cost_calc_task_detail
 *
 * <p>任务明细按业务单号拆分，便于线程五先落分页、失败重试和分片信息展示。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_calc_task_detail")
public class CostCalcTaskDetail implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明细主键
     */
    @TableId(value = "detail_id", type = IdType.AUTO)
    private Long detailId;

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
     * 业务单号
     */
    @TableField("biz_no")
    private String bizNo;

    /**
     * 分片号
     */
    @TableField("partition_no")
    private Integer partitionNo;

    /**
     * 明细状态
     */
    @TableField("detail_status")
    private String detailStatus;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 输入业务数据
     */
    @TableField("input_json")
    private String inputJson;

    /**
     * 结果摘要
     */
    @TableField("result_summary")
    private String resultSummary;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

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
