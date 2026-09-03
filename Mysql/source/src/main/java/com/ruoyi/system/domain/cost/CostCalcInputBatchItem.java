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
 * 正式核算输入批次明细对象 cost_calc_input_batch_item
 *
 * @author HwFan
 */
@Data
@TableName("cost_calc_input_batch_item")
public class CostCalcInputBatchItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("item_no")
    private Integer itemNo;

    @TableField("biz_no")
    private String bizNo;

    @TableField("item_status")
    private String itemStatus;

    @TableField("input_json")
    private String inputJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
