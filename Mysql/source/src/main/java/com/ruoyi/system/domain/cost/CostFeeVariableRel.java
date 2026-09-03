package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Fee-variable contract relation.
 */
@Data
@TableName("cost_fee_variable_rel")
public class CostFeeVariableRel {
    @TableId(value = "rel_id", type = IdType.AUTO)
    private Long relId;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("fee_id")
    private Long feeId;

    @TableField("variable_id")
    private Long variableId;

    @TableField("relation_type")
    private String relationType;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_rule_id")
    private Long sourceRuleId;

    @TableField("source_code")
    private String sourceCode;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("remark")
    private String remark;
}
