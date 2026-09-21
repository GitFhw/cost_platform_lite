package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 正式核算输入批次创建请求
 *
 * @author HwFan
 */
@Data
public class CostCalcInputBatchCreateBo {
    @NotNull(message = "场景不能为空")
    private Long sceneId;

    private Long versionId;

    @NotBlank(message = "账期不能为空")
    private String billMonth;

    /**
     * 批量输入 JSON 数组。
     */
    @NotBlank(message = "导入输入不能为空")
    private String inputJson;

    private String remark;
}
