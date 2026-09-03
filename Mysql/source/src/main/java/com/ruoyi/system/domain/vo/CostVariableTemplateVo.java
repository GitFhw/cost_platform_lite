package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 共享影响因素模板。
 *
 * @author HwFan
 */
@Data
public class CostVariableTemplateVo {
    /**
     * 模板编码
     */
    private String templateCode;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 适用说明
     */
    private String description;

    /**
     * 命名空间说明
     */
    private String namespaceHint;

    /**
     * 变量数量
     */
    private Integer variableCount;

    /**
     * 已复用场景数
     */
    private Integer appliedSceneCount = 0;

    /**
     * 完整覆盖场景数
     */
    private Integer fullyAppliedSceneCount = 0;

    /**
     * 已落地变量数
     */
    private Integer matchedVariableCount = 0;

    /**
     * 最近一次复用场景
     */
    private String latestSceneName;

    /**
     * 最近一次复用时间
     */
    private Date latestAppliedTime;

    /**
     * 最近复用场景示例
     */
    private List<String> recentSceneNames = new ArrayList<>();

    /**
     * 场景级复用摘要
     */
    private List<Map<String, Object>> sceneSummaries = new ArrayList<>();

    /**
     * 模板变量清单
     */
    private List<Map<String, Object>> items = new ArrayList<>();
}
