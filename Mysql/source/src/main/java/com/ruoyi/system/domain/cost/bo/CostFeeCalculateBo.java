package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Fee-scoped calculation request.
 *
 * @author HwFan
 */
@Data
public class CostFeeCalculateBo {
    /**
     * Scene id
     */
    @NotNull(message = "场景不能为空")
    private Long sceneId;

    /**
     * Published version id
     */
    private Long versionId;

    /**
     * Snapshot mode when versionId is not provided. Supported values: ACTIVE, DRAFT.
     */
    private String snapshotMode;

    /**
     * Fee id
     */
    private Long feeId;

    /**
     * Fee ids. Empty means all fees in the scene/runtime snapshot.
     */
    private List<Long> feeIds;

    /**
     * Fee code
     */
    private String feeCode;

    /**
     * Optional bill month context
     */
    private String billMonth;

    /**
     * Input payload, supports object or array
     */
    @NotBlank(message = "费用计算输入数据不能为空")
    private String inputJson;

    /**
     * Whether to include lightweight explain payload for debugging
     */
    private Boolean includeExplain;
}
