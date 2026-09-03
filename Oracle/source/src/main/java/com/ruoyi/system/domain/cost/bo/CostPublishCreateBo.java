package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布版本创建入参
 *
 * @author HwFan
 */
@Data
public class CostPublishCreateBo {
    /**
     * 场景主键
     */
    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    /**
     * 发布说明
     */
    @NotBlank(message = "发布说明不能为空")
    @Size(max = 1000, message = "发布说明长度不能超过1000个字符")
    private String publishDesc;

    /**
     * 发布后是否立即设为生效
     */
    private Boolean activateNow;
}
