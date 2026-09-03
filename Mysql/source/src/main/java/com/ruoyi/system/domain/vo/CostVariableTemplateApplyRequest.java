package com.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * 共享变量模板应用请求。
 *
 * @author HwFan
 */
@Data
public class CostVariableTemplateApplyRequest {
    /**
     * 目标场景
     */
    private Long sceneId;

    /**
     * 目标分组
     */
    private Long groupId;

    /**
     * 模板编码
     */
    private String templateCode;

    /**
     * 是否覆盖已存在变量
     */
    private Boolean updateSupport;
}
