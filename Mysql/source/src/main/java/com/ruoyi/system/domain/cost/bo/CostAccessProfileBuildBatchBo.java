package com.ruoyi.system.domain.cost.bo;

import lombok.Data;

/**
 * 按接入方案直连业务接口并生成/继续导入批次。
 */
@Data
public class CostAccessProfileBuildBatchBo {
    /**
     * 新建批次时必填；继续拉取时可留空并回退到批次账期。
     */
    private String billMonth;

    /**
     * 当前执行时覆盖方案内保存的请求载荷样例。
     */
    private String requestPayloadJson;

    /**
     * 继续装载时指定已有批次；为空则创建新批次。
     */
    private Long resumeBatchId;

    private String remark;
}
