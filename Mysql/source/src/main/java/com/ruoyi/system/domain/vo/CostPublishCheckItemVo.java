package com.ruoyi.system.domain.vo;

import lombok.Data;

/**
 * 发布前检查项
 *
 * @author HwFan
 */
@Data
public class CostPublishCheckItemVo {
    /**
     * 检查级别：BLOCK/WARN/PASS
     */
    private String level;

    /**
     * 检查编码
     */
    private String code;

    /**
     * 检查标题
     */
    private String title;

    /**
     * 检查说明
     */
    private String message;
}
