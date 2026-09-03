package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 费用主数据对象 cost_fee_item
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_fee_item")
public class CostFeeItem extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 费用主键
     */
    @TableId(value = "fee_id", type = IdType.AUTO)
    private Long feeId;

    /**
     * 所属场景主键
     */
    @Excel(name = "场景主键")
    @TableField("scene_id")
    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    /**
     * 检索关键词（费用编码/费用名称）
     */
    @TableField(exist = false)
    private String keyword;

    /**
     * 批量过滤费用主键
     */
    @TableField(exist = false)
    private Long[] feeIds;

    /**
     * 场景编码
     */
    @Excel(name = "场景编码")
    @TableField(exist = false)
    private String sceneCode;

    /**
     * 场景名称
     */
    @Excel(name = "所属场景")
    @TableField(exist = false)
    private String sceneName;

    /**
     * 业务域
     */
    @Excel(name = "业务域", dictType = "cost_business_domain")
    @TableField(exist = false)
    private String businessDomain;

    /**
     * 场景默认对象维度
     */
    @TableField(exist = false)
    private String sceneDefaultObjectDimension;

    /**
     * 费用编码
     */
    @Excel(name = "费用编码")
    @TableField("fee_code")
    @NotBlank(message = "费用编码不能为空")
    @Size(max = 64, message = "费用编码长度不能超过64个字符")
    private String feeCode;

    /**
     * 费用名称
     */
    @Excel(name = "费用名称")
    @TableField("fee_name")
    @NotBlank(message = "费用名称不能为空")
    @Size(max = 128, message = "费用名称长度不能超过128个字符")
    private String feeName;

    /**
     * 费用分类
     */
    @Excel(name = "费用分类")
    @TableField("fee_category")
    @Size(max = 64, message = "费用分类长度不能超过64个字符")
    private String feeCategory;

    /**
     * 计价单位
     */
    @Excel(name = "计价单位")
    @TableField("unit_code")
    @Size(max = 32, message = "计价单位长度不能超过32个字符")
    private String unitCode;

    /**
     * 影响因素摘要
     */
    @Excel(name = "影响因素摘要")
    @TableField("factor_summary")
    @Size(max = 255, message = "影响因素摘要长度不能超过255个字符")
    private String factorSummary;

    /**
     * 适用范围说明
     */
    @Excel(name = "适用范围说明")
    @TableField("scope_description")
    @Size(max = 255, message = "适用范围说明长度不能超过255个字符")
    private String scopeDescription;

    /**
     * 业务对象维度
     */
    @Excel(name = "业务对象维度")
    @TableField("object_dimension")
    @Size(max = 64, message = "业务对象维度长度不能超过64个字符")
    private String objectDimension;

    /**
     * 排序号
     */
    @Excel(name = "排序号")
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 状态
     */
    @Excel(name = "状态", dictType = "cost_fee_status")
    @TableField("status")
    @NotBlank(message = "费用状态不能为空")
    private String status;
}
