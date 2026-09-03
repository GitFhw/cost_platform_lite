package com.ruoyi.system.domain.cost.bo;

import com.ruoyi.system.domain.cost.CostRuleCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则复制并改条件值请求对象
 * <p>
 * 线程三要求复制规则时保留原规则结构，只调整新规则基础信息和条件值。
 *
 * @author HwFan
 */
@Data
public class CostRuleCopyBo {
    /**
     * 来源规则主键
     */
    @NotNull(message = "来源规则不能为空")
    private Long sourceRuleId;

    /**
     * 新规则编码
     */
    @NotBlank(message = "新规则编码不能为空")
    private String ruleCode;

    /**
     * 新规则名称
     */
    @NotBlank(message = "新规则名称不能为空")
    private String ruleName;

    /**
     * 新规则优先级
     */
    private Integer priority;

    /**
     * 新规则排序号
     */
    private Integer sortNo;

    /**
     * 新规则状态
     */
    @NotBlank(message = "新规则状态不能为空")
    private String status;

    /**
     * 覆盖后的条件列表
     */
    @Valid
    private List<CostRuleCondition> conditions = new ArrayList<>();
}
