package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Recent calculation task shown in scene governance checks.
 */
@Data
public class CostSceneRecentTaskVo {
    private Long taskId;
    private String taskNo;
    private Long sceneId;
    private Long versionId;
    private String versionNo;
    private String taskType;
    private String billMonth;
    private Integer sourceCount;
    private Integer successCount;
    private Integer failCount;
    private String taskStatus;
    private BigDecimal progressPercent;
    private Date startedTime;
    private Date finishedTime;
    private Long durationMs;
    private String requestNo;
    private String executeNode;
    private String inputSourceType;
    private String sourceBatchNo;
    private String errorMessage;
}
