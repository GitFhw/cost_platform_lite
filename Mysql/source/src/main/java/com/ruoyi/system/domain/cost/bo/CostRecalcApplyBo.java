package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起重算申请请求对象
 *
 * @author HwFan
 */
@Data
public class CostRecalcApplyBo {
    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    @NotBlank(message = "账期不能为空")
    private String billMonth;

    @NotNull(message = "目标版本不能为空")
    private Long versionId;

    @NotNull(message = "基准任务不能为空")
    private Long baselineTaskId;

    @NotBlank(message = "申请原因不能为空")
    private String applyReason;

    private String requestNo;

    private String remark;
}
