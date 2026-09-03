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
 * 规则主数据对象 cost_rule
 * <p>
 * 线程三以“费用 -> 规则 -> 阶梯”为维护主线，因此规则对象除了保存规则本身字段，
 * 还会承接费用、变量、条件摘要等工作台展示字段。
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_rule")
public class CostRule extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 规则主键
     */
    @TableId(value = "rule_id", type = IdType.AUTO)
    private Long ruleId;

    /**
     * 所属场景主键
     */
    @TableField("scene_id")
    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    /**
     * 所属费用主键
     */
    @TableField("fee_id")
    @NotNull(message = "所属费用不能为空")
    private Long feeId;

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
     * 费用编码
     */
    @Excel(name = "费用编码")
    @TableField(exist = false)
    private String feeCode;

    /**
     * 费用名称
     */
    @Excel(name = "费用名称")
    @TableField(exist = false)
    private String feeName;

    /**
     * 阶梯/公式依赖变量名称
     */
    @TableField(exist = false)
    private String quantityVariableName;

    /**
     * 检索关键词
     */
    @TableField(exist = false)
    private String keyword;

    /**
     * 条件摘要
     */
    @TableField(exist = false)
    private String conditionSummary;

    /**
     * 条件数量
     */
    @TableField(exist = false)
    private Integer conditionCount;

    /**
     * 阶梯数量
     */
    @TableField(exist = false)
    private Integer tierCount;

    /**
     * 规则编码
     */
    @Excel(name = "规则编码")
    @TableField("rule_code")
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过64个字符")
    private String ruleCode;

    /**
     * 规则名称
     */
    @Excel(name = "规则名称")
    @TableField("rule_name")
    @Size(max = 128, message = "规则名称长度不能超过128个字符")
    private String ruleName;

    /**
     * 规则类型
     */
    @Excel(name = "规则类型", dictType = "cost_rule_type")
    @TableField("rule_type")
    @NotBlank(message = "规则类型不能为空")
    @Size(max = 32, message = "规则类型长度不能超过32个字符")
    private String ruleType;

    /**
     * 条件逻辑
     */
    @Excel(name = "条件逻辑", dictType = "cost_rule_condition_logic")
    @TableField("condition_logic")
    private String conditionLogic;

    /**
     * 优先级
     */
    @Excel(name = "优先级")
    @TableField("priority")
    private Integer priority;

    /**
     * 计量变量编码
     */
    @Excel(name = "计量变量编码")
    @TableField("quantity_variable_code")
    @Size(max = 64, message = "计量变量编码长度不能超过64个字符")
    private String quantityVariableCode;

    /**
     * 定价模式
     */
    @TableField("pricing_mode")
    @Size(max = 32, message = "定价模式长度不能超过32个字符")
    private String pricingMode;

    /**
     * 结构化定价配置
     */
    @TableField("pricing_json")
    private String pricingJson;

    /**
     * 金额公式表达式
     */
    @Excel(name = "金额公式")
    @TableField("amount_formula")
    @Size(max = 2000, message = "金额公式长度不能超过2000个字符")
    private String amountFormula;

    /**
     * 金额公式编码
     */
    @Excel(name = "金额公式编码")
    @TableField("amount_formula_code")
    @Size(max = 64, message = "金额公式编码长度不能超过64个字符")
    private String amountFormulaCode;

    /**
     * 金额公式名称
     */
    @TableField(exist = false)
    private String amountFormulaName;

    /**
     * 金额业务中文公式
     */
    @TableField(exist = false)
    private String amountBusinessFormula;

    /**
     * 结果备注模板
     */
    @Excel(name = "说明模板")
    @TableField("note_template")
    @Size(max = 500, message = "结果备注模板长度不能超过500个字符")
    private String noteTemplate;

    /**
     * 状态
     */
    @Excel(name = "状态", dictType = "cost_rule_status")
    @TableField("status")
    @NotBlank(message = "规则状态不能为空")
    private String status;

    /**
     * 排序号
     */
    @Excel(name = "排序号")
    @TableField("sort_no")
    private Integer sortNo;
}
