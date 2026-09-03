package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 结果台账对象 cost_result_ledger
 *
 * <p>线程五按费用粒度写正式结果台账，便于列表按场景、账期、任务号和费用快速筛选。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_result_ledger")
public class CostResultLedger implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 结果主键
     */
    @Excel(name = "结果ID")
    @TableId(value = "result_id", type = IdType.AUTO)
    private Long resultId;

    /**
     * 任务主键
     */
    @Excel(name = "任务ID")
    @TableField("task_id")
    private Long taskId;

    /**
     * 任务编号
     */
    @Excel(name = "任务号")
    @TableField("task_no")
    private String taskNo;

    /**
     * 场景主键
     */
    @Excel(name = "场景ID")
    @TableField("scene_id")
    private Long sceneId;

    /**
     * 版本主键
     */
    @Excel(name = "版本ID")
    @TableField("version_id")
    private Long versionId;

    /**
     * 费用主键
     */
    @TableField("fee_id")
    private Long feeId;

    /**
     * 费用编码
     */
    @Excel(name = "费用编码")
    @TableField("fee_code")
    private String feeCode;

    /**
     * 费用名称
     */
    @Excel(name = "费用名称")
    @TableField("fee_name")
    private String feeName;

    /**
     * 业务单号
     */
    @Excel(name = "业务单号")
    @TableField("biz_no")
    private String bizNo;

    /**
     * 账期
     */
    @Excel(name = "账期")
    @TableField("bill_month")
    private String billMonth;

    /**
     * 核算对象维度
     */
    @Excel(name = "核算对象维度")
    @TableField("object_dimension")
    private String objectDimension;

    /**
     * 核算对象编码
     */
    @Excel(name = "核算对象编码")
    @TableField("object_code")
    private String objectCode;

    /**
     * 核算对象名称
     */
    @Excel(name = "核算对象名称")
    @TableField("object_name")
    private String objectName;

    /**
     * 计量值
     */
    @Excel(name = "数量")
    @TableField("quantity_value")
    private BigDecimal quantityValue;

    /**
     * 命中单价
     */
    @Excel(name = "单价")
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /**
     * 结果金额
     */
    @Excel(name = "金额")
    @TableField("amount_value")
    private BigDecimal amountValue;

    /**
     * 币种
     */
    @Excel(name = "币种")
    @TableField("currency_code")
    private String currencyCode;

    /**
     * 结果状态
     */
    @Excel(name = "结果状态", dictType = "cost_result_status")
    @TableField("result_status")
    private String resultStatus;

    /**
     * 追溯主键
     */
    @Excel(name = "追溯ID")
    @TableField("trace_id")
    private Long traceId;

    /**
     * 创建时间
     */
    @Excel(name = "生成时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private Date createTime;

    @Excel(name = "导出人")
    @TableField(exist = false)
    private String exportBy;

    @Excel(name = "导出时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @TableField(exist = false)
    private Date exportTime;

    /**
     * 场景编码
     */
    @Excel(name = "场景编码")
    @TableField(exist = false)
    private String sceneCode;

    /**
     * 场景名称
     */
    @Excel(name = "场景名称")
    @TableField(exist = false)
    private String sceneName;

    /**
     * 版本号
     */
    @Excel(name = "版本号")
    @TableField(exist = false)
    private String versionNo;

    /**
     * 请求号，仅用于查询条件
     */
    @TableField(exist = false)
    private String requestNo;

    @Excel(name = "计价单位")
    @TableField(exist = false)
    private String unitCode;

    /**
     * 命中的组合组号
     */
    @Excel(name = "命中组合组")
    @TableField(exist = false)
    private Integer matchedGroupNo;

    /**
     * 定价模式
     */
    @Excel(name = "定价模式")
    @TableField(exist = false)
    private String pricingMode;

    /**
     * 定价来源
     */
    @Excel(name = "定价来源")
    @TableField(exist = false)
    private String pricingSource;
}
