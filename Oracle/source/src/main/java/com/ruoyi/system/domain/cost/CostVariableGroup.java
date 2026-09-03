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
 * 变量分组对象 cost_variable_group
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_variable_group")
public class CostVariableGroup extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 分组主键
     */
    @TableId(value = "group_id", type = IdType.AUTO)
    private Long groupId;

    /**
     * 所属场景主键
     */
    @Excel(name = "场景主键")
    @TableField("scene_id")
    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    /**
     * 检索关键词（分组编码/分组名称）
     */
    @TableField(exist = false)
    private String keyword;

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
     * 业务域
     */
    @Excel(name = "业务域", dictType = "cost_business_domain")
    @TableField(exist = false)
    private String businessDomain;

    /**
     * 分组编码
     */
    @Excel(name = "分组编码")
    @TableField("group_code")
    @NotBlank(message = "分组编码不能为空")
    @Size(max = 64, message = "分组编码长度不能超过64个字符")
    private String groupCode;

    /**
     * 分组名称
     */
    @Excel(name = "分组名称")
    @TableField("group_name")
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 128, message = "分组名称长度不能超过128个字符")
    private String groupName;

    /**
     * 排序号
     */
    @Excel(name = "排序号")
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 状态
     */
    @Excel(name = "状态", dictType = "cost_variable_group_status")
    @TableField("status")
    @NotBlank(message = "分组状态不能为空")
    private String status;

    /**
     * Variables currently assigned to this group.
     */
    @TableField(exist = false)
    private Long variableCount;
}
