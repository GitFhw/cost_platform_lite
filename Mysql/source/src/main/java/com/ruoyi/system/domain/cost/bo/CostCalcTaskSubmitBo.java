package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 核算任务发起请求
 *
 * @author HwFan
 */
@Data
public class CostCalcTaskSubmitBo {
    /**
     * 场景主键
     */
    @NotNull(message = "任务场景不能为空")
    private Long sceneId;

    /**
     * 发布版本主键
     */
    private Long versionId;

    /**
     * 任务类型
     */
    @NotBlank(message = "任务类型不能为空")
    private String taskType;

    /**
     * 账期
     */
    @NotBlank(message = "账期不能为空")
    private String billMonth;

    /**
     * 幂等请求号
     */
    private String requestNo;

    /**
     * 输入来源类型，默认保留原有 INLINE_JSON 提交方式
     */
    private String inputSourceType;

    /**
     * 来源导入批次号
     */
    private String sourceBatchNo;

    /**
     * 输入数据 JSON，单笔时为对象，批量时为数组；保留原有直传方式
     */
    private String inputJson;

    /**
     * 备注
     */
    private String remark;
}
