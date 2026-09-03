package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 场景主数据对象 cost_scene
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_scene")
public class CostScene extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 场景主键
     */
    @TableId(value = "scene_id", type = IdType.AUTO)
    private Long sceneId;

    /**
     * 检索关键词（场景编码/场景名称）
     */
    @TableField(exist = false)
    private String keyword;

    /**
     * 场景编码
     */
    @Excel(name = "场景编码")
    @TableField("scene_code")
    @NotBlank(message = "场景编码不能为空")
    @Size(min = 0, max = 64, message = "场景编码长度不能超过64个字符")
    private String sceneCode;

    /**
     * 场景名称
     */
    @Excel(name = "场景名称")
    @TableField("scene_name")
    @NotBlank(message = "场景名称不能为空")
    @Size(min = 0, max = 128, message = "场景名称长度不能超过128个字符")
    private String sceneName;

    /**
     * 业务域
     */
    @Excel(name = "业务域", dictType = "cost_business_domain")
    @TableField("business_domain")
    @NotBlank(message = "业务域不能为空")
    @Size(min = 0, max = 64, message = "业务域长度不能超过64个字符")
    private String businessDomain;

    /**
     * 适用组织
     */
    @Excel(name = "适用组织")
    @TableField("org_code")
    @Size(min = 0, max = 64, message = "适用组织长度不能超过64个字符")
    private String orgCode;

    /**
     * 场景类型
     */
    @Excel(name = "场景类型", dictType = "cost_scene_type")
    @TableField("scene_type")
    @NotBlank(message = "场景类型不能为空")
    @Size(min = 0, max = 32, message = "场景类型长度不能超过32个字符")
    private String sceneType;

    /**
     * 默认对象维度
     */
    @Excel(name = "默认对象维度")
    @TableField("default_object_dimension")
    @Size(min = 0, max = 64, message = "默认对象维度长度不能超过64个字符")
    private String defaultObjectDimension;

    /**
     * 当前生效版本主键
     */
    @Excel(name = "当前生效版本")
    @TableField("active_version_id")
    private Long activeVersionId;

    /**
     * 当前生效版本号
     */
    @TableField(exist = false)
    private String activeVersionNo;

    /**
     * 状态
     */
    @Excel(name = "状态", dictType = "cost_scene_status")
    @TableField("status")
    @NotBlank(message = "场景状态不能为空")
    private String status;
}
