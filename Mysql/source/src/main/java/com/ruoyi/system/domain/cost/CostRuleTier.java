package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 规则阶梯对象 cost_rule_tier
 * <p>
 * 阶梯明细显式保存区间上下界和边界口径，
 * 便于线程四/五继续承接解释、快照和追溯链路。
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_rule_tier")
public class CostRuleTier extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 阶梯主键
     */
    @TableId(value = "tier_id", type = IdType.AUTO)
    private Long tierId;

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
     * 起始值
     */
    @Excel(name = "起始值")
    @TableField("start_value")
    private BigDecimal startValue;

    /**
     * 截止值
     */
    @Excel(name = "截止值")
    @TableField("end_value")
    private BigDecimal endValue;

    /**
     * 费率
     */
    @Excel(name = "费率")
    @TableField("rate_value")
    @NotNull(message = "阶梯费率不能为空")
    private BigDecimal rateValue;

    /**
     * 区间模式
     */
    @Excel(name = "区间模式", dictType = "cost_rule_interval_mode")
    @TableField("interval_mode")
    @Size(max = 32, message = "区间模式长度不能超过32个字符")
    private String intervalMode;

    /**
     * 阶梯序号
     */
    @Excel(name = "阶梯序号")
    @TableField("tier_no")
    private Integer tierNo;

    /**
     * 状态
     */
    @TableField("status")
    private String status;

    /**
     * 区间摘要
     */
    @TableField(exist = false)
    private String rangeSummary;
}
