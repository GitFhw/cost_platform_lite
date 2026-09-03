package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostAlarmRecord;
import com.ruoyi.system.domain.cost.CostAuditLog;
import com.ruoyi.system.domain.cost.CostBillPeriod;
import com.ruoyi.system.domain.cost.CostRecalcOrder;
import com.ruoyi.system.domain.cost.bo.CostBillPeriodSaveBo;
import com.ruoyi.system.domain.cost.bo.CostRecalcApplyBo;
import com.ruoyi.system.domain.cost.bo.CostRecalcApproveBo;

import java.util.List;
import java.util.Map;

/**
 * 线程六治理增强服务接口
 *
 * @author HwFan
 */
public interface ICostGovernanceService {
    Map<String, Object> selectPeriodStats(Long sceneId);

    List<CostBillPeriod> selectPeriodList(CostBillPeriod query);

    Map<String, Object> selectPeriodDetail(Long periodId);

    int createPeriod(CostBillPeriodSaveBo bo);

    int sealPeriod(Long periodId);

    List<CostRecalcOrder> selectRecalcList(CostRecalcOrder query);

    Map<String, Object> selectRecalcDetail(Long recalcId);

    Map<String, Object> selectRecalcImpact(Long recalcId);

    int applyRecalc(CostRecalcApplyBo bo);

    int approveRecalc(Long recalcId, CostRecalcApproveBo bo);

    int executeRecalc(Long recalcId);

    List<CostAuditLog> selectAuditList(CostAuditLog query);

    Map<String, Object> selectAuditStats(CostAuditLog query);

    List<CostAlarmRecord> selectAlarmList(CostAlarmRecord query);

    Map<String, Object> selectAlarmStats(CostAlarmRecord query);

    /**
     * 查询告警中心运营概览。
     *
     * @param query 告警过滤条件
     *
     * @return 趋势、热点与类型聚合结果
     */
    Map<String, Object> selectAlarmOverview(CostAlarmRecord query);

    int ackAlarm(Long alarmId);

    int resolveAlarm(Long alarmId);

    Map<String, Object> selectRuntimeCacheStats(Long sceneId, Long versionId);

    Map<String, Object> refreshRuntimeCache(Long sceneId, Long versionId);
}
