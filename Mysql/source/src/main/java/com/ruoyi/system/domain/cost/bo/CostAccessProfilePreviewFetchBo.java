package com.ruoyi.system.domain.cost.bo;

import lombok.Data;

/**
 * 接入方案直连预演请求。
 */
@Data
public class CostAccessProfilePreviewFetchBo {
    /**
     * 当前预演时覆盖方案中的请求载荷样例。
     */
    private String requestPayloadJson;
}
