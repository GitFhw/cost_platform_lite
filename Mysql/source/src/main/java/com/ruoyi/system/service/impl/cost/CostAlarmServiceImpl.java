package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAlarmRecord;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.mapper.cost.CostAlarmRecordMapper;
import com.ruoyi.system.mapper.cost.CostSceneMapper;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.cost.ICostAlarmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运行告警服务实现。
 *
 * <p>当前阶段负责：
 * 1. 查询告警台账、统计与运营概览；
 * 2. 确认与关闭告警；
 * 3. 按系统参数触发 Webhook 外部通知。</p>
 */
@Service
@Slf4j
public class CostAlarmServiceImpl implements ICostAlarmService {
    private static final String ALARM_STATUS_OPEN = "OPEN";
    private static final String ALARM_STATUS_ACKED = "ACKED";
    private static final String ALARM_STATUS_RESOLVED = "RESOLVED";
    private static final int ALARM_ESCALATION_THRESHOLD = 3;

    private static final String ALARM_WEBHOOK_ENABLED_KEY = "cost.alarm.webhook.enabled";
    private static final String ALARM_WEBHOOK_URL_KEY = "cost.alarm.webhook.url";
    private static final String ALARM_WEBHOOK_HEADERS_KEY = "cost.alarm.webhook.headers";
    private static final String ALARM_WEBHOOK_SECRET_KEY = "cost.alarm.webhook.secret";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CostAlarmRecordMapper alarmRecordMapper;

    @Autowired
    private CostSceneMapper sceneMapper;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    @Qualifier("costAccessRestTemplate")
    private RestTemplate costAccessRestTemplate;

    @Override
    public void createAlarm(CostAlarmRecord alarmRecord) {
        Date triggerTime = alarmRecord.getTriggerTime() == null ? DateUtils.getNowDate() : alarmRecord.getTriggerTime();
        String operator = firstNonBlank(alarmRecord.getCreateBy(), resolveOperator());
        alarmRecord.setAlarmStatus(firstNonBlank(alarmRecord.getAlarmStatus(), ALARM_STATUS_OPEN));
        alarmRecord.setAlarmLevel(firstNonBlank(alarmRecord.getAlarmLevel(), "WARN"));
        alarmRecord.setTriggerTime(triggerTime);
        alarmRecord.setFirstTriggerTime(alarmRecord.getFirstTriggerTime() == null ? triggerTime : alarmRecord.getFirstTriggerTime());
        alarmRecord.setLatestTriggerTime(triggerTime);
        alarmRecord.setOccurrenceCount(alarmRecord.getOccurrenceCount() == null || alarmRecord.getOccurrenceCount() <= 0 ? 1 : alarmRecord.getOccurrenceCount());
        alarmRecord.setCreateBy(operator);
        alarmRecord.setCreateTime(alarmRecord.getCreateTime() == null ? DateUtils.getNowDate() : alarmRecord.getCreateTime());
        alarmRecord.setUpdateBy(firstNonBlank(alarmRecord.getUpdateBy(), alarmRecord.getCreateBy()));
        alarmRecord.setUpdateTime(alarmRecord.getUpdateTime() == null ? DateUtils.getNowDate() : alarmRecord.getUpdateTime());
        CostAlarmRecord mergedAlarm = mergeOpenAlarmIfNecessary(alarmRecord);
        if (mergedAlarm == null) {
            alarmRecordMapper.insert(alarmRecord);
            mergedAlarm = alarmRecord;
        }
        enrichSceneInfo(List.of(mergedAlarm));
        notifyAlarmWebhook(mergedAlarm);
    }

    @Override
    public List<CostAlarmRecord> selectAlarmList(CostAlarmRecord query) {
        List<CostAlarmRecord> alarms = alarmRecordMapper.selectList(Wrappers.<CostAlarmRecord>lambdaQuery()
                .eq(query.getSceneId() != null, CostAlarmRecord::getSceneId, query.getSceneId())
                .eq(query.getTaskId() != null, CostAlarmRecord::getTaskId, query.getTaskId())
                .eq(StringUtils.isNotEmpty(query.getBillMonth()), CostAlarmRecord::getBillMonth, query.getBillMonth())
                .eq(StringUtils.isNotEmpty(query.getAlarmType()), CostAlarmRecord::getAlarmType, query.getAlarmType())
                .eq(StringUtils.isNotEmpty(query.getAlarmLevel()), CostAlarmRecord::getAlarmLevel, query.getAlarmLevel())
                .eq(StringUtils.isNotEmpty(query.getAlarmStatus()), CostAlarmRecord::getAlarmStatus, query.getAlarmStatus())
                .like(StringUtils.isNotEmpty(query.getAlarmTitle()), CostAlarmRecord::getAlarmTitle, query.getAlarmTitle())
                .orderByDesc(CostAlarmRecord::getTriggerTime)
                .orderByDesc(CostAlarmRecord::getAlarmId));
        enrichSceneInfo(alarms);
        return alarms;
    }

    @Override
    public Map<String, Object> selectAlarmStats(CostAlarmRecord query) {
        List<CostAlarmRecord> alarms = selectAlarmList(query);
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("alarmCount", alarms.size());
        stats.put("occurrenceCount", alarms.stream().mapToLong(this::resolveOccurrenceCount).sum());
        stats.put("openCount", alarms.stream().filter(item -> ALARM_STATUS_OPEN.equals(item.getAlarmStatus())).count());
        stats.put("ackedCount", alarms.stream().filter(item -> ALARM_STATUS_ACKED.equals(item.getAlarmStatus())).count());
        stats.put("resolvedCount", alarms.stream().filter(item -> ALARM_STATUS_RESOLVED.equals(item.getAlarmStatus())).count());
        return stats;
    }

    @Override
    public Map<String, Object> selectAlarmOverview(CostAlarmRecord query) {
        List<CostAlarmRecord> alarms = selectAlarmList(query);
        LinkedHashMap<String, Object> overview = new LinkedHashMap<>();
        overview.put("recentTrend", buildRecentTrend(alarms, 7));
        overview.put("topAlarmTypes", buildTopAlarmTypes(alarms, 5));
        overview.put("topTasks", buildTopTasks(alarms, 5));
        overview.put("levelDistribution", buildLevelDistribution(alarms));
        overview.put("notificationSummary", buildNotificationSummary());
        return overview;
    }

    @Override
    public int ackAlarm(Long alarmId) {
        requireAlarm(alarmId);
        return alarmRecordMapper.update(null, Wrappers.<CostAlarmRecord>lambdaUpdate()
                .eq(CostAlarmRecord::getAlarmId, alarmId)
                .set(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_ACKED)
                .set(CostAlarmRecord::getAckBy, resolveOperator())
                .set(CostAlarmRecord::getAckTime, DateUtils.getNowDate())
                .set(CostAlarmRecord::getUpdateBy, resolveOperator())
                .set(CostAlarmRecord::getUpdateTime, DateUtils.getNowDate()));
    }

    @Override
    public int resolveAlarm(Long alarmId) {
        requireAlarm(alarmId);
        return alarmRecordMapper.update(null, Wrappers.<CostAlarmRecord>lambdaUpdate()
                .eq(CostAlarmRecord::getAlarmId, alarmId)
                .set(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_RESOLVED)
                .set(CostAlarmRecord::getResolveBy, resolveOperator())
                .set(CostAlarmRecord::getResolveTime, DateUtils.getNowDate())
                .set(CostAlarmRecord::getUpdateBy, resolveOperator())
                .set(CostAlarmRecord::getUpdateTime, DateUtils.getNowDate()));
    }

    @Override
    public int autoResolveBySourceKey(String sourceKey, String resolveSummary) {
        if (StringUtils.isEmpty(StringUtils.trim(sourceKey))) {
            return 0;
        }
        String operator = resolveOperator();
        String summary = normalizeResolveSummary(resolveSummary);
        return alarmRecordMapper.update(null, Wrappers.<CostAlarmRecord>lambdaUpdate()
                .eq(CostAlarmRecord::getSourceKey, StringUtils.trim(sourceKey))
                .in(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_OPEN, ALARM_STATUS_ACKED)
                .set(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_RESOLVED)
                .set(CostAlarmRecord::getResolveBy, operator)
                .set(CostAlarmRecord::getResolveTime, DateUtils.getNowDate())
                .set(CostAlarmRecord::getUpdateBy, operator)
                .set(CostAlarmRecord::getUpdateTime, DateUtils.getNowDate())
                .set(StringUtils.isNotEmpty(summary), CostAlarmRecord::getRemark, summary));
    }

    @Override
    public int autoResolveByTask(Long taskId, String resolveSummary) {
        if (taskId == null) {
            return 0;
        }
        String operator = resolveOperator();
        String summary = normalizeResolveSummary(resolveSummary);
        return alarmRecordMapper.update(null, Wrappers.<CostAlarmRecord>lambdaUpdate()
                .eq(CostAlarmRecord::getTaskId, taskId)
                .in(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_OPEN, ALARM_STATUS_ACKED)
                .set(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_RESOLVED)
                .set(CostAlarmRecord::getResolveBy, operator)
                .set(CostAlarmRecord::getResolveTime, DateUtils.getNowDate())
                .set(CostAlarmRecord::getUpdateBy, operator)
                .set(CostAlarmRecord::getUpdateTime, DateUtils.getNowDate())
                .set(StringUtils.isNotEmpty(summary), CostAlarmRecord::getRemark, summary));
    }

    private CostAlarmRecord requireAlarm(Long alarmId) {
        CostAlarmRecord alarm = alarmRecordMapper.selectById(alarmId);
        if (alarm == null) {
            throw new ServiceException("告警记录不存在，请刷新后重试");
        }
        return alarm;
    }

    private void enrichSceneInfo(List<CostAlarmRecord> alarms) {
        Set<Long> sceneIds = alarms.stream()
                .map(CostAlarmRecord::getSceneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sceneIds.isEmpty()) {
            return;
        }
        Map<Long, CostScene> sceneMap = sceneMapper.selectBatchIds(sceneIds).stream()
                .collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        alarms.forEach(item -> {
            CostScene scene = sceneMap.get(item.getSceneId());
            if (scene != null) {
                item.setSceneCode(scene.getSceneCode());
                item.setSceneName(scene.getSceneName());
            }
        });
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

    private CostAlarmRecord mergeOpenAlarmIfNecessary(CostAlarmRecord alarmRecord) {
        if (StringUtils.isEmpty(StringUtils.trim(alarmRecord.getSourceKey()))) {
            return null;
        }
        CostAlarmRecord existing = alarmRecordMapper.selectOne(Wrappers.<CostAlarmRecord>lambdaQuery()
                .eq(CostAlarmRecord::getSourceKey, StringUtils.trim(alarmRecord.getSourceKey()))
                .in(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_OPEN, ALARM_STATUS_ACKED)
                .orderByDesc(CostAlarmRecord::getAlarmId)
                .last("limit 1"));
        if (existing == null) {
            return null;
        }

        int nextOccurrence = resolveOccurrenceCount(existing) + Math.max(resolveOccurrenceCount(alarmRecord), 1);
        Date latestTriggerTime = alarmRecord.getLatestTriggerTime() == null ? alarmRecord.getTriggerTime() : alarmRecord.getLatestTriggerTime();
        String mergedLevel = resolveEscalatedLevel(existing.getAlarmLevel(), alarmRecord.getAlarmLevel(), nextOccurrence);
        Date updateTime = DateUtils.getNowDate();

        alarmRecordMapper.update(null, Wrappers.<CostAlarmRecord>lambdaUpdate()
                .eq(CostAlarmRecord::getAlarmId, existing.getAlarmId())
                .set(CostAlarmRecord::getSceneId, alarmRecord.getSceneId())
                .set(CostAlarmRecord::getVersionId, alarmRecord.getVersionId())
                .set(CostAlarmRecord::getTaskId, alarmRecord.getTaskId())
                .set(CostAlarmRecord::getDetailId, alarmRecord.getDetailId())
                .set(CostAlarmRecord::getBillMonth, alarmRecord.getBillMonth())
                .set(CostAlarmRecord::getAlarmStatus, ALARM_STATUS_OPEN)
                .set(CostAlarmRecord::getAlarmLevel, mergedLevel)
                .set(CostAlarmRecord::getAlarmTitle, firstNonBlank(alarmRecord.getAlarmTitle(), existing.getAlarmTitle()))
                .set(CostAlarmRecord::getAlarmContent, limitAlarmContent(alarmRecord.getAlarmContent(), nextOccurrence))
                .set(CostAlarmRecord::getTriggerTime, latestTriggerTime)
                .set(CostAlarmRecord::getFirstTriggerTime, existing.getFirstTriggerTime() == null ? existing.getTriggerTime() : existing.getFirstTriggerTime())
                .set(CostAlarmRecord::getLatestTriggerTime, latestTriggerTime)
                .set(CostAlarmRecord::getOccurrenceCount, nextOccurrence)
                .set(CostAlarmRecord::getAckBy, null)
                .set(CostAlarmRecord::getAckTime, null)
                .set(CostAlarmRecord::getResolveBy, null)
                .set(CostAlarmRecord::getResolveTime, null)
                .set(CostAlarmRecord::getUpdateBy, resolveOperator())
                .set(CostAlarmRecord::getUpdateTime, updateTime));
        return alarmRecordMapper.selectById(existing.getAlarmId());
    }

    private String limitAlarmContent(String content, int occurrenceCount) {
        String normalized = StringUtils.trimToEmpty(content);
        if (occurrenceCount > 1) {
            normalized = firstNonBlank(normalized, "重复触发告警")
                    + "（累计触发 " + occurrenceCount + " 次）";
        }
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private int resolveOccurrenceCount(CostAlarmRecord record) {
        if (record == null || record.getOccurrenceCount() == null || record.getOccurrenceCount() <= 0) {
            return 1;
        }
        return record.getOccurrenceCount();
    }

    private String resolveEscalatedLevel(String existingLevel, String incomingLevel, int occurrenceCount) {
        if ("ERROR".equalsIgnoreCase(existingLevel) || "ERROR".equalsIgnoreCase(incomingLevel)) {
            return "ERROR";
        }
        if (occurrenceCount >= ALARM_ESCALATION_THRESHOLD) {
            return "ERROR";
        }
        return firstNonBlank(incomingLevel, firstNonBlank(existingLevel, "WARN"));
    }

    private String normalizeResolveSummary(String resolveSummary) {
        return StringUtils.isNotEmpty(StringUtils.trim(resolveSummary)) ? StringUtils.trim(resolveSummary) : "";
    }

    private void notifyAlarmWebhook(CostAlarmRecord alarmRecord) {
        if (alarmRecord == null || !isAlarmWebhookEnabled()) {
            return;
        }
        String webhookUrl = StringUtils.trim(configService.selectConfigByKey(ALARM_WEBHOOK_URL_KEY));
        if (StringUtils.isEmpty(webhookUrl)) {
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));

            Map<String, Object> extraHeaders = parseOptionalJsonMap(configService.selectConfigByKey(ALARM_WEBHOOK_HEADERS_KEY));
            extraHeaders.forEach((key, value) -> {
                if (StringUtils.isNotEmpty(key) && value != null) {
                    headers.set(String.valueOf(key), String.valueOf(value));
                }
            });

            String secret = StringUtils.trim(configService.selectConfigByKey(ALARM_WEBHOOK_SECRET_KEY));
            if (StringUtils.isNotEmpty(secret)) {
                headers.set("X-Cost-Alarm-Secret", secret);
            }

            HttpEntity<Map<String, Object>> requestEntity =
                    new HttpEntity<>(buildAlarmWebhookPayload(alarmRecord), headers);
            ResponseEntity<String> response =
                    costAccessRestTemplate.postForEntity(webhookUrl, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("成本告警 Webhook 返回非成功状态: alarmId={}, status={}",
                        alarmRecord.getAlarmId(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("成本告警 Webhook 通知失败: alarmId={}, message={}",
                    alarmRecord.getAlarmId(), e.getMessage());
        }
    }

    private Map<String, Object> buildAlarmWebhookPayload(CostAlarmRecord alarmRecord) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", alarmRecord.getAlarmId());
        payload.put("sceneId", alarmRecord.getSceneId());
        payload.put("sceneCode", alarmRecord.getSceneCode());
        payload.put("sceneName", alarmRecord.getSceneName());
        payload.put("versionId", alarmRecord.getVersionId());
        payload.put("taskId", alarmRecord.getTaskId());
        payload.put("detailId", alarmRecord.getDetailId());
        payload.put("billMonth", alarmRecord.getBillMonth());
        payload.put("alarmType", alarmRecord.getAlarmType());
        payload.put("alarmLevel", alarmRecord.getAlarmLevel());
        payload.put("alarmStatus", alarmRecord.getAlarmStatus());
        payload.put("alarmTitle", alarmRecord.getAlarmTitle());
        payload.put("alarmContent", alarmRecord.getAlarmContent());
        payload.put("sourceKey", alarmRecord.getSourceKey());
        payload.put("triggerTime", alarmRecord.getTriggerTime());
        payload.put("operator", alarmRecord.getCreateBy());
        payload.put("platform", "cost_platform");
        return payload;
    }

    private Map<String, Object> buildNotificationSummary() {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        boolean enabled = isAlarmWebhookEnabled();
        String webhookUrl = StringUtils.trim(configService.selectConfigByKey(ALARM_WEBHOOK_URL_KEY));
        boolean headersConfigured = !parseOptionalJsonMap(configService.selectConfigByKey(ALARM_WEBHOOK_HEADERS_KEY)).isEmpty();
        boolean secretConfigured = StringUtils.isNotEmpty(StringUtils.trim(configService.selectConfigByKey(ALARM_WEBHOOK_SECRET_KEY)));

        summary.put("enabled", enabled);
        summary.put("channelType", "WEBHOOK");
        summary.put("configured", StringUtils.isNotEmpty(webhookUrl));
        summary.put("target", maskWebhookUrl(webhookUrl));
        summary.put("headersConfigured", headersConfigured);
        summary.put("secretConfigured", secretConfigured);
        summary.put("description", enabled
                ? (StringUtils.isNotEmpty(webhookUrl)
                   ? "当前已启用 Webhook 外部通知，可转发到企业值守平台或消息网关。"
                   : "当前已打开 Webhook 开关，但尚未配置通知地址。")
                : "当前未启用外部通知，告警仅保留在平台台账中。");
        return summary;
    }

    private boolean isAlarmWebhookEnabled() {
        return "true".equalsIgnoreCase(StringUtils.trim(configService.selectConfigByKey(ALARM_WEBHOOK_ENABLED_KEY)));
    }

    private Map<String, Object> parseOptionalJsonMap(String json) {
        if (StringUtils.isEmpty(StringUtils.trim(json))) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("解析告警 Webhook 头配置失败: {}", e.getMessage());
        }
        return Map.of();
    }

    private String maskWebhookUrl(String webhookUrl) {
        if (StringUtils.isEmpty(webhookUrl)) {
            return "";
        }
        int separatorIndex = webhookUrl.indexOf("://");
        int startIndex = separatorIndex >= 0 ? separatorIndex + 3 : 0;
        int previewEnd = Math.min(startIndex + 8, webhookUrl.length());
        int tailStart = Math.max(startIndex, webhookUrl.length() - 12);
        return webhookUrl.substring(0, previewEnd)
                + (tailStart > previewEnd ? "..." + webhookUrl.substring(tailStart) : "");
    }

    /**
     * 构建最近 N 天告警趋势。
     */
    private List<Map<String, Object>> buildRecentTrend(List<CostAlarmRecord> alarms, int recentDays) {
        ZoneId zoneId = ZoneId.systemDefault();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<LocalDate, List<CostAlarmRecord>> dayGroups = alarms.stream()
                .filter(item -> item.getTriggerTime() != null)
                .collect(Collectors.groupingBy(item -> item.getTriggerTime().toInstant().atZone(zoneId).toLocalDate()));

        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusDays(Math.max(recentDays - 1L, 0L));
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<CostAlarmRecord> dayAlarms = dayGroups.getOrDefault(date, List.of());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.format(formatter));
            row.put("count", dayAlarms.stream().mapToLong(this::resolveOccurrenceCount).sum());
            row.put("openCount", dayAlarms.stream()
                    .filter(item -> ALARM_STATUS_OPEN.equals(item.getAlarmStatus()))
                    .mapToLong(this::resolveOccurrenceCount)
                    .sum());
            row.put("errorCount", dayAlarms.stream()
                    .filter(item -> "ERROR".equals(item.getAlarmLevel()))
                    .mapToLong(this::resolveOccurrenceCount)
                    .sum());
            trend.add(row);
        }
        return trend;
    }

    /**
     * 构建高频告警类型排行。
     */
    private List<Map<String, Object>> buildTopAlarmTypes(List<CostAlarmRecord> alarms, int limit) {
        return alarms.stream()
                .collect(Collectors.groupingBy(item -> firstNonBlank(item.getAlarmType(), "UNKNOWN"), Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<CostAlarmRecord> grouped = entry.getValue();
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("alarmType", entry.getKey());
                    row.put("count", grouped.stream().mapToLong(this::resolveOccurrenceCount).sum());
                    row.put("openCount", grouped.stream()
                            .filter(item -> ALARM_STATUS_OPEN.equals(item.getAlarmStatus()))
                            .mapToLong(this::resolveOccurrenceCount)
                            .sum());
                    row.put("latestTime", grouped.stream()
                            .map(item -> item.getLatestTriggerTime() == null ? item.getTriggerTime() : item.getLatestTriggerTime())
                            .filter(Objects::nonNull)
                            .max(Date::compareTo)
                            .orElse(null));
                    return row;
                })
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(item -> ((Number) item.get("count")).intValue()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 构建任务热点排行，便于定位异常集中任务。
     */
    private List<Map<String, Object>> buildTopTasks(List<CostAlarmRecord> alarms, int limit) {
        return alarms.stream()
                .filter(item -> item.getTaskId() != null)
                .collect(Collectors.groupingBy(CostAlarmRecord::getTaskId, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<CostAlarmRecord> grouped = entry.getValue();
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("taskId", entry.getKey());
                    row.put("sceneName", grouped.stream()
                            .map(CostAlarmRecord::getSceneName)
                            .filter(StringUtils::isNotEmpty)
                            .findFirst()
                            .orElse("-"));
                    row.put("billMonth", grouped.stream()
                            .map(CostAlarmRecord::getBillMonth)
                            .filter(StringUtils::isNotEmpty)
                            .findFirst()
                            .orElse("-"));
                    row.put("count", grouped.stream().mapToLong(this::resolveOccurrenceCount).sum());
                    row.put("openCount", grouped.stream()
                            .filter(item -> ALARM_STATUS_OPEN.equals(item.getAlarmStatus()))
                            .mapToLong(this::resolveOccurrenceCount)
                            .sum());
                    row.put("latestTitle", grouped.stream()
                            .map(CostAlarmRecord::getAlarmTitle)
                            .filter(StringUtils::isNotEmpty)
                            .findFirst()
                            .orElse("-"));
                    return row;
                })
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(item -> ((Number) item.get("count")).intValue()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 构建告警等级分布。
     */
    private List<Map<String, Object>> buildLevelDistribution(List<CostAlarmRecord> alarms) {
        return alarms.stream()
                .collect(Collectors.groupingBy(item -> firstNonBlank(item.getAlarmLevel(), "WARN"),
                        Collectors.summingLong(this::resolveOccurrenceCount)))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("alarmLevel", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList());
    }
}
