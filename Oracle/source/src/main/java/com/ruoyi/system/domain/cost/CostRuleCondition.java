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
 * 规则条件对象 cost_rule_condition
 * <p>
 * 条件编辑由变量元数据驱动，compareValue 统一按字符串落库，
 * 由前端和值编辑器按变量类型解释展示。
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_rule_condition")
public class CostRuleCondition extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 条件主键
     */
    @TableId(value = "condition_id", type = IdType.AUTO)
    private Long conditionId;

    /**
     * 所属场景主键
     */
    @TableField("scene_id")
    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    /**
     * 所属规则主键
     */
    @TableField("rule_id")
    private Long ruleId;

    /**
     * 组号
     */
    @TableField("group_no")
    private Integer groupNo;

    /**
     * 排序号
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 变量编码
     */
    @Excel(name = "变量编码")
    @TableField("variable_code")
    @NotBlank(message = "条件变量不能为空")
    @Size(max = 64, message = "变量编码长度不能超过64个字符")
    private String variableCode;

    /**
     * 展示名称
     */
    @TableField("display_name")
    @Size(max = 128, message = "显示名称长度不能超过128个字符")
    private String displayName;

    /**
     * 操作符
     */
    @Excel(name = "操作符", dictType = "cost_rule_operator")
    @TableField("operator_code")
    @NotBlank(message = "操作符不能为空")
    @Size(max = 32, message = "操作符长度不能超过32个字符")
    private String operatorCode;

    /**
     * 比较值
     */
    @Excel(name = "比较值")
    @TableField("compare_value")
    @Size(max = 1000, message = "比较值长度不能超过1000个字符")
    private String compareValue;

    /**
     * 状态
     */
    @TableField("status")
    private String status;

    /**
     * 变量名称
     */
    @TableField(exist = false)
    private String variableName;

    /**
     * 变量类型
     */
    @TableField(exist = false)
    private String variableType;

    /**
     * 数据类型
     */
    @TableField(exist = false)
    private String dataType;

    /**
     * 字典类型
     */
    @TableField(exist = false)
    private String dictType;
}
