package com.ruoyi.system.domain.cost.bo;

import lombok.Data;

/**
 * 审核重算请求对象
 *
 * @author HwFan
 */
@Data
public class CostRecalcApproveBo {
    private Boolean approved;

    private String approveOpinion;
}
