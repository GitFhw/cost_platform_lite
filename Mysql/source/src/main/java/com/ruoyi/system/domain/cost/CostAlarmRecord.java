package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

/**
 * 运行告警对象 {@code cost_alarm_record}。
 *
 * <p>当前主要承接任务失败、重试超限、运行缓存刷新异常等治理级告警，
 * 同时为外部通知与运营概览提供统一数据来源。</p>
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_alarm_record")
public class CostAlarmRecord extends BaseEntity {
    @TableId(value = "alarm_id", type = IdType.AUTO)
    private Long alarmId;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("version_id")
    private Long versionId;

    @TableField("task_id")
    private Long taskId;

    @TableField("detail_id")
    private Long detailId;

    @TableField("bill_month")
    private String billMonth;

    @TableField("alarm_type")
    private String alarmType;

    @TableField("alarm_level")
    private String alarmLevel;

    @TableField("alarm_status")
    private String alarmStatus;

    @TableField("source_key")
    private String sourceKey;

    @TableField("alarm_title")
    private String alarmTitle;

    @TableField("alarm_content")
    private String alarmContent;

    @TableField("trigger_time")
    private Date triggerTime;

    @TableField("first_trigger_time")
    private Date firstTriggerTime;

    @TableField("latest_trigger_time")
    private Date latestTriggerTime;

    @TableField("occurrence_count")
    private Integer occurrenceCount;

    @TableField("ack_by")
    private String ackBy;

    @TableField("ack_time")
    private Date ackTime;

    @TableField("resolve_by")
    private String resolveBy;

    @TableField("resolve_time")
    private Date resolveTime;

    @TableField(exist = false)
    private String sceneCode;

    @TableField(exist = false)
    private String sceneName;
}
