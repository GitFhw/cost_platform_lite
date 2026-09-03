package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Preview request for transforming third-party raw payload into standardized billing objects.
 *
 * @author HwFan
 */
@Data
public class CostInputBuildPreviewBo {
    @NotNull(message = "场景不能为空")
    private Long sceneId;

    private Long versionId;

    private Long feeId;

    private List<Long> feeIds;

    private String feeCode;

    private String taskType;

    @NotBlank(message = "原始输入 JSON 不能为空")
    private String rawJson;

    private String mappingJson;
}
