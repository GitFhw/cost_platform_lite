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
 * 结果追溯对象 cost_result_trace
 *
 * <p>追溯表与台账表分离，运行链只在正式核算时落追溯记录，满足线程五“结果可解释”目标。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_result_trace")
public class CostResultTrace implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 追溯主键
     */
    @TableId(value = "trace_id", type = IdType.AUTO)
    private Long traceId;

    /**
     * 场景主键
     */
    @TableField("scene_id")
    private Long sceneId;

    /**
     * 发布版本主键
     */
    @TableField("version_id")
    private Long versionId;

    /**
     * 命中规则主键
     */
    @TableField("rule_id")
    private Long ruleId;

    /**
     * 命中阶梯主键
     */
    @TableField("tier_id")
    private Long tierId;

    /**
     * 变量解释
     */
    @TableField("variable_json")
    private String variableJson;

    /**
     * 条件解释
     */
    @TableField("condition_json")
    private String conditionJson;

    /**
     * 定价解释
     */
    @TableField("pricing_json")
    private String pricingJson;

    /**
     * 时间线解释
     */
    @TableField("timeline_json")
    private String timelineJson;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;
}
