package com.ruoyi.system.domain.vo;

import lombok.Data;

/** Manual fee-variable relation submitted by the lightweight workbench. */
@Data
public class CostFeeVariableRelSaveRequest {
    private Long variableId;
    private String relationType;
    private Integer sortNo;
    private String remark;
}
