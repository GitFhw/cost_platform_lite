package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 场景复制请求对象。
 *
 * @author HwFan
 */
@Data
public class CostSceneCopyBo {
    /**
     * 来源场景主键
     */
    @NotNull(message = "来源场景不能为空")
    private Long sourceSceneId;

    /**
     * 新场景编码
     */
    @NotBlank(message = "新场景编码不能为空")
    @Size(max = 64, message = "新场景编码长度不能超过64个字符")
    private String sceneCode;

    /**
     * 新场景名称
     */
    @NotBlank(message = "新场景名称不能为空")
    @Size(max = 128, message = "新场景名称长度不能超过128个字符")
    private String sceneName;

    /**
     * 业务域
     */
    @NotBlank(message = "业务域不能为空")
    @Size(max = 64, message = "业务域长度不能超过64个字符")
    private String businessDomain;

    /**
     * 适用组织
     */
    @Size(max = 64, message = "适用组织长度不能超过64个字符")
    private String orgCode;

    /**
     * 场景类型
     */
    @NotBlank(message = "场景类型不能为空")
    @Size(max = 32, message = "场景类型长度不能超过32个字符")
    private String sceneType;

    /**
     * 默认对象维度
     */
    @Size(max = 64, message = "默认对象维度长度不能超过64个字符")
    private String defaultObjectDimension;

    /**
     * 新场景状态
     */
    @NotBlank(message = "场景状态不能为空")
    private String status;

    /**
     * 是否复制下游配置
     */
    private Boolean copyConfig;

    /**
     * 场景说明
     */
    private String remark;
}
