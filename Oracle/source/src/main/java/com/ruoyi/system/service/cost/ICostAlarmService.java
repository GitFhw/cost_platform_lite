package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostAlarmRecord;

import java.util.List;
import java.util.Map;

/**
 * 运行告警服务接口。
 */
public interface ICostAlarmService {
    void createAlarm(CostAlarmRecord alarmRecord);

    List<CostAlarmRecord> selectAlarmList(CostAlarmRecord query);

    Map<String, Object> selectAlarmStats(CostAlarmRecord query);

    /**
     * 查询告警运营概览。
     *
     * <p>用于告警中心展示近 7 天趋势、高频类型、任务热点和通知摘要。</p>
     *
     * @param query 查询条件
     *
     * @return 告警运营概览
     */
    Map<String, Object> selectAlarmOverview(CostAlarmRecord query);

    int ackAlarm(Long alarmId);

    int resolveAlarm(Long alarmId);

    int autoResolveBySourceKey(String sourceKey, String resolveSummary);

    int autoResolveByTask(Long taskId, String resolveSummary);
}
