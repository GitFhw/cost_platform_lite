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
 * 正式核算输入批次对象 cost_calc_input_batch
 *
 * <p>输入批次用于承接正式核算的导入型输入源，保留批次号、账期、场景版本和条数统计，
 * 与原有 inline JSON 提交方式并存，为后续独立导入中心对接提供正式模型。</p>
 */
@Data
@TableName("cost_calc_input_batch")
public class CostCalcInputBatch implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "batch_id", type = IdType.AUTO)
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("version_id")
    private Long versionId;

    @TableField("bill_month")
    private String billMonth;

    @TableField("source_type")
    private String sourceType;

    @TableField("batch_status")
    private String batchStatus;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("valid_count")
    private Integer validCount;

    @TableField("error_count")
    private Integer errorCount;

    @TableField("remark")
    private String remark;

    @TableField("error_message")
    private String errorMessage;

    @TableField("access_profile_id")
    private Long accessProfileId;

    @TableField("checkpoint_json")
    private String checkpointJson;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private Date updateTime;

    @TableField(exist = false)
    private String sceneCode;

    @TableField(exist = false)
    private String sceneName;

    @TableField(exist = false)
    private String versionNo;
}
