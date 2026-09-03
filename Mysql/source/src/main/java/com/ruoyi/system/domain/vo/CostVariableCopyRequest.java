package com.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * 变量复制请求。
 *
 * <p>线程二要求变量中心支持复制能力，便于在同场景或跨场景快速复用影响因素配置。</p>
 *
 * @author HwFan
 */
@Data
public class CostVariableCopyRequest {
    /**
     * 源变量主键
     */
    private Long variableId;

    /**
     * 目标场景主键，为空时沿用源场景
     */
    private Long targetSceneId;

    /**
     * 目标分组主键，为空时沿用源分组
     */
    private Long targetGroupId;

    /**
     * 新变量编码
     */
    private String variableCode;

    /**
     * 新变量名称
     */
    private String variableName;
}
