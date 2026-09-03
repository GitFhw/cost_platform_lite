package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 治理影响明细。
 */
@Data
public class CostGovernanceImpactVo {
    /**
     * 影响类型编码。
     */
    private String impactType;

    /**
     * 关联功能模块名称。
     */
    private String moduleName;

    /**
     * 展示标题。
     */
    private String title;

    /**
     * 影响数量。
     */
    private Long count;

    /**
     * 是否阻断删除。
     */
    private Boolean blocksDelete;

    /**
     * 是否阻断停用。
     */
    private Boolean blocksDisable;

    /**
     * 删除影响说明。
     */
    private String deleteImpact;

    /**
     * 停用影响说明。
     */
    private String disableImpact;

    /**
     * 处理建议。
     */
    private String actionAdvice;

    /**
     * 样例对象，最多返回少量摘要帮助用户定位。
     */
    private List<String> examples = new ArrayList<>();
}
