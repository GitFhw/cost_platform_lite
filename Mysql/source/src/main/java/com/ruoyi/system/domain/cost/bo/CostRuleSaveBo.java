package com.ruoyi.system.domain.cost.bo;

import com.ruoyi.system.domain.cost.CostRuleCondition;
import com.ruoyi.system.domain.cost.CostRuleTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则保存对象
 * <p>
 * 用于统一承接规则主表、条件列表、阶梯列表和结构化定价配置。
 *
 * @author HwFan
 */
@Data
public class CostRuleSaveBo {
    /**
     * 规则主键
     */
    private Long ruleId;

    /**
     * 所属场景主键
     */
    private Long sceneId;

    /**
     * 所属费用主键
     */
    @NotNull(message = "所属费用不能为空")
    private Long feeId;

    /**
     * 规则编码
     */
    @NotBlank(message = "规则编码不能为空")
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型
     */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /**
     * 条件逻辑
     */
    private String conditionLogic;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 计量变量编码
     */
    private String quantityVariableCode;

    /**
     * 定价模式
     */
    private String pricingMode;

    /**
     * 结构化定价配置
     */
    private Map<String, Object> pricingConfig = new LinkedHashMap<>();

    /**
     * 金额公式
     */
    private String amountFormula;

    /**
     * 金额公式编码
     */
    private String amountFormulaCode;

    /**
     * 说明模板
     */
    private String noteTemplate;

    /**
     * 状态
     */
    @NotBlank(message = "规则状态不能为空")
    private String status;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 条件列表
     */
    @Valid
    private List<CostRuleCondition> conditions = new ArrayList<>();

    /**
     * 阶梯列表
     */
    @Valid
    private List<CostRuleTier> tiers = new ArrayList<>();
}
