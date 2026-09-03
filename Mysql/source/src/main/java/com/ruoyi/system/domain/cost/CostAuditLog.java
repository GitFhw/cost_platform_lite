package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 配置审计日志对象 cost_audit_log
 *
 * <p>线程六开始把发布、运行链、账期治理等关键动作纳入审计台账，
 * 为后续验收、排障和追责提供统一审计视图。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_audit_log")
public class CostAuditLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;

    @Excel(name = "场景主键")
    @TableField("scene_id")
    private Long sceneId;

    @Excel(name = "对象类型")
    @TableField("object_type")
    private String objectType;

    @Excel(name = "对象编码")
    @TableField("object_code")
    private String objectCode;

    @Excel(name = "动作类型")
    @TableField("action_type")
    private String actionType;

    @Excel(name = "动作摘要")
    @TableField("action_summary")
    private String actionSummary;

    @TableField("before_json")
    private String beforeJson;

    @TableField("after_json")
    private String afterJson;

    @Excel(name = "操作人编码")
    @TableField("operator_code")
    private String operatorCode;

    @Excel(name = "操作人名称")
    @TableField("operator_name")
    private String operatorName;

    @Excel(name = "操作时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @TableField("operate_time")
    private Date operateTime;

    @Excel(name = "请求号")
    @TableField("request_no")
    private String requestNo;

    @Excel(name = "场景编码")
    @TableField(exist = false)
    private String sceneCode;

    @Excel(name = "场景名称")
    @TableField(exist = false)
    private String sceneName;
}
