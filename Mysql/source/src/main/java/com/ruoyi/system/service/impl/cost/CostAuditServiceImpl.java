package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAuditLog;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.mapper.cost.CostAuditLogMapper;
import com.ruoyi.system.mapper.cost.CostSceneMapper;
import com.ruoyi.system.service.cost.ICostAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 审计日志服务实现
 *
 * @author HwFan
 */
@Service
public class CostAuditServiceImpl implements ICostAuditService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CostAuditLogMapper auditLogMapper;

    @Autowired
    private CostSceneMapper sceneMapper;

    @Override
    public void recordAudit(Long sceneId, String objectType, String objectCode, String actionType,
                            String actionSummary, Object beforeData, Object afterData, String requestNo) {
        CostAuditLog log = new CostAuditLog();
        log.setSceneId(sceneId);
        log.setObjectType(firstNonBlank(objectType, "UNKNOWN"));
        log.setObjectCode(firstNonBlank(objectCode, ""));
        log.setActionType(firstNonBlank(actionType, "UNKNOWN"));
        log.setActionSummary(firstNonBlank(actionSummary, ""));
        log.setBeforeJson(writeJson(beforeData));
        log.setAfterJson(writeJson(afterData));
        log.setOperatorCode(resolveOperator());
        log.setOperatorName(log.getOperatorCode());
        log.setOperateTime(DateUtils.getNowDate());
        log.setRequestNo(firstNonBlank(requestNo, ""));
        auditLogMapper.insert(log);
    }

    @Override
    public List<CostAuditLog> selectAuditList(CostAuditLog query) {
        List<CostAuditLog> logs = auditLogMapper.selectList(Wrappers.<CostAuditLog>lambdaQuery()
                .eq(query.getSceneId() != null, CostAuditLog::getSceneId, query.getSceneId())
                .eq(StringUtils.isNotEmpty(query.getObjectType()), CostAuditLog::getObjectType, query.getObjectType())
                .eq(StringUtils.isNotEmpty(query.getActionType()), CostAuditLog::getActionType, query.getActionType())
                .like(StringUtils.isNotEmpty(query.getObjectCode()), CostAuditLog::getObjectCode, query.getObjectCode())
                .like(StringUtils.isNotEmpty(query.getOperatorCode()), CostAuditLog::getOperatorCode, query.getOperatorCode())
                .orderByDesc(CostAuditLog::getOperateTime)
                .orderByDesc(CostAuditLog::getAuditId));
        enrichSceneInfo(logs);
        return logs;
    }

    @Override
    public Map<String, Object> selectAuditStats(CostAuditLog query) {
        List<CostAuditLog> logs = selectAuditList(query);
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("auditCount", logs.size());
        stats.put("sceneCount", logs.stream().map(CostAuditLog::getSceneId).filter(Objects::nonNull).distinct().count());
        stats.put("operatorCount", logs.stream().map(CostAuditLog::getOperatorCode).filter(StringUtils::isNotEmpty).distinct().count());
        stats.put("todayCount", logs.stream().filter(item -> item.getOperateTime() != null
                && DateUtils.parseDateToStr("yyyy-MM-dd", item.getOperateTime())
                .equals(DateUtils.parseDateToStr("yyyy-MM-dd", DateUtils.getNowDate()))).count());
        return stats;
    }

    private void enrichSceneInfo(List<CostAuditLog> logs) {
        Set<Long> sceneIds = logs.stream().map(CostAuditLog::getSceneId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (sceneIds.isEmpty()) {
            return;
        }
        Map<Long, CostScene> sceneMap = sceneMapper.selectBatchIds(sceneIds).stream()
                .collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        logs.forEach(item -> {
            CostScene scene = sceneMap.get(item.getSceneId());
            if (scene != null) {
                item.setSceneCode(scene.getSceneCode());
                item.setSceneName(scene.getSceneName());
            }
        });
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    private String resolveOperator() {
        try {
            return firstNonBlank(SecurityUtils.getUsername(), "system");
        } catch (Exception ignored) {
            return "system";
        }
    }
}
