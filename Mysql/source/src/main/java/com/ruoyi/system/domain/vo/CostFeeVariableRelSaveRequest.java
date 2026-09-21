package com.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * 手工维护费目—要素关系的请求项。
 *
 * <p>规则自动引用的关系由规则服务维护，页面只提交 MANUAL_REQUIRED 关系，
 * 避免设置要素时误删规则派生关系。</p>
 */
@Data
public class CostFeeVariableRelSaveRequest {
    private Long variableId;
    private String relationType;
    private Integer sortNo;
    private String remark;
}
