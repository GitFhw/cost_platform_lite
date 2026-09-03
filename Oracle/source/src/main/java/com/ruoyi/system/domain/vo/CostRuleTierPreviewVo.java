package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阶梯命中预演结果对象。
 * <p>
 * 统一返回条件命中、阶梯命中、计量值和解释明细，
 * 便于规则中心工作区直接展示可读解释。
 *
 * @author HwFan
 */
@Data
public class CostRuleTierPreviewVo {
    /**
     * 是否通过条件匹配
     */
    private Boolean conditionMatched;

    /**
     * 是否命中阶梯
     */
    private Boolean tierMatched;

    /**
     * 命中的条件组合组号
     */
    private Integer matchedGroupNo;

    /**
     * 计量变量编码
     */
    private String quantityVariableCode;

    /**
     * 计量变量名称
     */
    private String quantityVariableName;

    /**
     * 计量值
     */
    private BigDecimal quantityValue;

    /**
     * 命中阶梯序号
     */
    private Integer matchedTierNo;

    /**
     * 命中阶梯区间摘要
     */
    private String matchedTierRange;

    /**
     * 命中阶梯费率
     */
    private BigDecimal matchedTierRate;

    /**
     * 预估单价
     */
    private BigDecimal unitPrice;

    /**
     * 预估金额
     */
    private BigDecimal amountValue;

    /**
     * 计价来源
     */
    private String pricingSource;

    /**
     * 计价解释
     */
    private String pricingExplain;

    /**
     * 结果摘要
     */
    private String summary;

    /**
     * 录入样本回显
     */
    private List<Map<String, Object>> inputValues = new ArrayList<>();

    /**
     * 条件明细解释
     */
    private List<Map<String, Object>> conditionResults = new ArrayList<>();

    /**
     * 分组结果解释
     */
    private List<Map<String, Object>> groupResults = new ArrayList<>();

    /**
     * 阶梯明细解释
     */
    private List<Map<String, Object>> tierResults = new ArrayList<>();
}
