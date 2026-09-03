package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostAuditLog;

import java.util.List;
import java.util.Map;

/**
 * 审计日志服务接口
 *
 * @author HwFan
 */
public interface ICostAuditService {
    void recordAudit(Long sceneId, String objectType, String objectCode, String actionType,
                     String actionSummary, Object beforeData, Object afterData, String requestNo);

    List<CostAuditLog> selectAuditList(CostAuditLog query);

    Map<String, Object> selectAuditStats(CostAuditLog query);
}
