package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阶梯命中预演请求对象。
 * <p>
 * 在规则中心工作区内，允许业务人员针对单个费目录入多变量样本值，
 * 直接预演当前规则草稿是否命中条件、命中哪一档阶梯。
 *
 * @author HwFan
 */
@Data
public class CostRuleTierPreviewBo {
    /**
     * 当前规则草稿
     */
    @Valid
    @NotNull(message = "预演规则不能为空")
    private CostRuleSaveBo rule;

    /**
     * 单个费目的多变量输入
     */
    private Map<String, Object> inputValues = new LinkedHashMap<>();
}
