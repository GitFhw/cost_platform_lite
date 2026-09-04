package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.*;
import com.ruoyi.system.domain.cost.bo.CostBillPeriodSaveBo;
import com.ruoyi.system.domain.cost.bo.CostCalcTaskSubmitBo;
import com.ruoyi.system.domain.cost.bo.CostRecalcApplyBo;
import com.ruoyi.system.domain.cost.bo.CostRecalcApproveBo;
import com.ruoyi.system.mapper.cost.*;
import com.ruoyi.system.service.cost.ICostAlarmService;
import com.ruoyi.system.service.cost.ICostAuditService;
import com.ruoyi.system.service.cost.ICostGovernanceService;
import com.ruoyi.system.service.cost.ICostRunService;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 线程六治理增强服务实现
 *
 * @author HwFan
 */
@Service
public class CostGovernanceServiceImpl implements ICostGovernanceService {
    private static final String RUNTIME_CACHE_PREFIX = "cost:runtime:snapshot:";
    private static final int CACHE_KEY_SAMPLE_LIMIT = 50;
    private static final String CACHE_OBJECT_TYPE = "CACHE";
    private static final String CACHE_ACTION_REFRESH = "REFRESH";
    private static final String CACHE_ALARM_TYPE_REFRESH_FAILED = "CACHE_REFRESH_FAILED";
    private static final String ALARM_STATUS_OPEN = "OPEN";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CostBillPeriodMapper billPeriodMapper;
    @Autowired
    private CostRecalcOrderMapper recalcOrderMapper;
    @Autowired
    private CostSceneMapper sceneMapper;
    @Autowired
    private CostPublishVersionMapper publishVersionMapper;
    @Autowired
    private CostCalcTaskMapper calcTaskMapper;
    @Autowired
    private CostCalcTaskDetailMapper calcTaskDetailMapper;
    @Autowired
    private CostResultLedgerMapper resultLedgerMapper;
    @Autowired
    private ICostRunService runService;
    @Autowired
    private ICostAuditService auditService;
    @Autowired
    private ICostAlarmService alarmService;
    @Autowired
    private RedisCache redisCache;

    @Autowired
    private CostDistributedLockSupport distributedLockSupport;

    @Override
    public Map<String, Object> selectPeriodStats(Long sceneId) {
        List<CostBillPeriod> periods = selectPeriodList(buildPeriodQuery(sceneId));
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("periodCount", periods.size());
        stats.put("sealedCount", periods.stream().filter(item -> PERIOD_STATUS_SEALED.equals(item.getPeriodStatus())).count());
        stats.put("runningCount", periods.stream().filter(item -> PERIOD_STATUS_IN_PROGRESS.equals(item.getPeriodStatus())).count());
        stats.put("recalcCount", recalcOrderMapper.selectCount(Wrappers.<CostRecalcOrder>lambdaQuery()
                .eq(sceneId != null, CostRecalcOrder::getSceneId, sceneId)));
        return stats;
    }

    @Override
    public List<CostBillPeriod> selectPeriodList(CostBillPeriod query) {
        List<CostBillPeriod> periods = billPeriodMapper.selectList(Wrappers.<CostBillPeriod>lambdaQuery()
                .eq(query.getSceneId() != null, CostBillPeriod::getSceneId, query.getSceneId())
                .eq(StringUtils.isNotEmpty(query.getPeriodStatus()), CostBillPeriod::getPeriodStatus, query.getPeriodStatus())
                .like(StringUtils.isNotEmpty(query.getBillMonth()), CostBillPeriod::getBillMonth, query.getBillMonth())
                .orderByDesc(CostBillPeriod::getBillMonth)
                .orderByDesc(CostBillPeriod::getPeriodId));
        enrichPeriods(periods);
        return periods;
    }

    @Override
    public Map<String, Object> selectPeriodDetail(Long periodId) {
        CostBillPeriod period = requirePeriod(periodId);
        enrichPeriods(Collections.singletonList(period));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("recalcOrders", selectRecalcList(buildRecalcQuery(period.getSceneId(), period.getBillMonth())));
        result.put("resultStats", buildResultStats(period.getSceneId(), period.getBillMonth(), period.getLastTaskId()));
        return result;
    }

    @Override
    @Transactional(transactionManager = "costLiteTransactionManager", rollbackFor = Exception.class)
    public int createPeriod(CostBillPeriodSaveBo bo) {
        CostScene scene = requireScene(bo.getSceneId());
        ensureBillMonth(bo.getBillMonth());
        if (billPeriodMapper.selectCount(Wrappers.<CostBillPeriod>lambdaQuery()
                .eq(CostBillPeriod::getSceneId, bo.getSceneId())
                .eq(CostBillPeriod::getBillMonth, bo.getBillMonth())) > 0) {
            throw new ServiceException("当前场景账期已存在，请勿重复创建");
        }
        if (bo.getActiveVersionId() != null) {
            requireVersion(bo.getActiveVersionId());
        }
        CostBillPeriod period = new CostBillPeriod();
        period.setSceneId(scene.getSceneId());
        period.setBillMonth(bo.getBillMonth());
        period.setPeriodStatus(PERIOD_STATUS_NOT_STARTED);
        period.setActiveVersionId(bo.getActiveVersionId());
        period.setResultCount(0L);
        period.setAmountTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        period.setRemark(firstNonBlank(bo.getRemark(), ""));
        period.setCreateBy(firstNonBlank(SecurityUtils.getUsername(), "system"));
        period.setCreateTime(DateUtils.getNowDate());
        period.setUpdateBy(period.getCreateBy());
        period.setUpdateTime(period.getCreateTime());
        billPeriodMapper.insert(period);
        auditService.recordAudit(scene.getSceneId(), "BILL_PERIOD", scene.getSceneCode() + ":" + bo.getBillMonth(),
                "CREATE", "新建账期", null, period, "");
        return 1;
    }

    @Override
    @Transactional(transactionManager = "costLiteTransactionManager", rollbackFor = Exception.class)
    public int sealPeriod(Long periodId) {
        CostBillPeriod before = requirePeriod(periodId);
        if (PERIOD_STATUS_SEALED.equals(before.getPeriodStatus())) {
            return 0;
        }
        Date now = DateUtils.getNowDate();
        int rows = billPeriodMapper.update(null, Wrappers.<CostBillPeriod>lambdaUpdate()
                .eq(CostBillPeriod::getPeriodId, periodId)
                .set(CostBillPeriod::getPeriodStatus, PERIOD_STATUS_SEALED)
                .set(CostBillPeriod::getSealedBy, firstNonBlank(SecurityUtils.getUsername(), "system"))
                .set(CostBillPeriod::getSealedTime, now)
                .set(CostBillPeriod::getUpdateBy, firstNonBlank(SecurityUtils.getUsername(), "system"))
                .set(CostBillPeriod::getUpdateTime, now));
        auditService.recordAudit(before.getSceneId(), "BILL_PERIOD",
                before.getSceneId() + ":" + before.getBillMonth(), "SEAL", "封存账期", before,
                billPeriodMapper.selectById(periodId), "");
        return rows;
    }

    @Override
    public List<CostRecalcOrder> selectRecalcList(CostRecalcOrder query) {
        List<CostRecalcOrder> orders = recalcOrderMapper.selectList(Wrappers.<CostRecalcOrder>lambdaQuery()
                .eq(query.getSceneId() != null, CostRecalcOrder::getSceneId, query.getSceneId())
                .eq(StringUtils.isNotEmpty(query.getBillMonth()), CostRecalcOrder::getBillMonth, query.getBillMonth())
                .eq(query.getVersionId() != null, CostRecalcOrder::getVersionId, query.getVersionId())
                .eq(StringUtils.isNotEmpty(query.getRecalcStatus()), CostRecalcOrder::getRecalcStatus, query.getRecalcStatus())
                .orderByDesc(CostRecalcOrder::getCreateTime)
                .orderByDesc(CostRecalcOrder::getRecalcId));
        enrichRecalcOrders(orders);
        return orders;
    }

    @Override
    public Map<String, Object> selectRecalcDetail(Long recalcId) {
        CostRecalcOrder order = requireRecalc(recalcId);
        enrichRecalcOrders(Collections.singletonList(order));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("diffSummary", parseJson(order.getDiffSummaryJson()));
        return result;
    }

    @Override
    public Map<String, Object> selectRecalcImpact(Long recalcId) {
        CostRecalcOrder order = requireRecalc(recalcId);
        enrichRecalcOrders(Collections.singletonList(order));
        CostBillPeriod period = order.getPeriodId() == null ? requirePeriod(order.getSceneId(), order.getBillMonth()) : requirePeriod(order.getPeriodId());
        CostCalcTask baselineTask = requireTask(order.getBaselineTaskId());
        CostCalcTask latestTask = period.getLastTaskId() == null ? null : calcTaskMapper.selectById(period.getLastTaskId());
        List<CostCalcTaskDetail> sampleDetails = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .eq(CostCalcTaskDetail::getTaskId, baselineTask.getTaskId())
                .orderByAsc(CostCalcTaskDetail::getPartitionNo)
                .last("limit 1000"));
        long inputCount = calcTaskDetailMapper.selectCount(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .eq(CostCalcTaskDetail::getTaskId, baselineTask.getTaskId()));
        long bizCount = inputCount;
        List<String> sampleBizNos = sampleDetails.stream().map(CostCalcTaskDetail::getBizNo).filter(StringUtils::isNotEmpty)
                .distinct().limit(5).collect(Collectors.toList());
        long baselineResultCount = countTaskResults(order.getBaselineTaskId());
        BigDecimal baselineAmount = sumTaskAmount(order.getBaselineTaskId());
        long latestResultCount = countTaskResults(period.getLastTaskId());
        BigDecimal latestAmount = sumTaskAmount(period.getLastTaskId());
        Long ledgerCount = resultLedgerMapper.countBySceneAndBillMonth(order.getSceneId(), order.getBillMonth());
        BigDecimal ledgerAmount = resultLedgerMapper.sumAmountBySceneAndBillMonth(order.getSceneId(), order.getBillMonth());

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("status", order.getRecalcStatus());
        result.put("sceneId", order.getSceneId());
        result.put("sceneName", order.getSceneName());
        result.put("sceneCode", order.getSceneCode());
        result.put("billMonth", order.getBillMonth());
        result.put("versionId", order.getVersionId());
        result.put("versionNo", order.getVersionNo());
        result.put("periodStatus", period.getPeriodStatus());
        result.put("baselineTaskId", baselineTask.getTaskId());
        result.put("baselineTaskNo", baselineTask.getTaskNo());
        result.put("latestTaskId", latestTask == null ? null : latestTask.getTaskId());
        result.put("latestTaskNo", latestTask == null ? "" : latestTask.getTaskNo());
        result.put("inputCount", inputCount);
        result.put("bizCount", bizCount);
        result.put("sampleBizNos", sampleBizNos);
        result.put("baselineResultCount", baselineResultCount);
        result.put("baselineAmount", baselineAmount);
        result.put("latestResultCount", latestResultCount);
        result.put("latestAmount", latestAmount);
        result.put("periodLedgerCount", ledgerCount == null ? 0L : ledgerCount);
        result.put("periodLedgerAmount", defaultZero(ledgerAmount).setScale(2, RoundingMode.HALF_UP));
        result.put("targetTaskType", inputCount == 1 ? TASK_TYPE_FORMAL_SINGLE : TASK_TYPE_FORMAL_BATCH);
        result.put("willAppendTask", true);
        result.put("willAppendResultLedger", true);
        result.put("willOverwritePeriodSummary", true);
        result.put("willDeleteExistingLedger", false);
        result.put("impactItems", buildRecalcImpactItems(order, period, baselineTask, latestTask,
                inputCount, bizCount, baselineResultCount, latestResultCount,
                ledgerCount == null ? 0L : ledgerCount, baselineAmount, latestAmount));
        return result;
    }

    @Override
    @Transactional(transactionManager = "costLiteTransactionManager", rollbackFor = Exception.class)
    public int applyRecalc(CostRecalcApplyBo bo) {
        CostScene scene = requireScene(bo.getSceneId());
        CostBillPeriod period = requirePeriod(scene.getSceneId(), bo.getBillMonth());
        CostPublishVersion version = requireVersion(bo.getVersionId());
        CostCalcTask baselineTask = requireTask(bo.getBaselineTaskId());
        if (!Objects.equals(scene.getSceneId(), baselineTask.getSceneId()) || !StringUtils.equals(bo.getBillMonth(), baselineTask.getBillMonth())) {
            throw new ServiceException("基准任务必须属于当前场景与账期");
        }
        CostRecalcOrder order = new CostRecalcOrder();
        order.setSceneId(scene.getSceneId());
        order.setBillMonth(bo.getBillMonth());
        order.setVersionId(version.getVersionId());
        order.setPeriodId(period.getPeriodId());
        order.setBaselineTaskId(baselineTask.getTaskId());
        order.setBaselineTaskNo(baselineTask.getTaskNo());
        order.setRecalcStatus(RECALC_STATUS_PENDING);
        order.setApplyReason(bo.getApplyReason());
        order.setRequestNo(firstNonBlank(bo.getRequestNo(), ""));
        order.setRemark(firstNonBlank(bo.getRemark(), ""));
        order.setDiffAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setCreateBy(firstNonBlank(SecurityUtils.getUsername(), "system"));
        order.setCreateTime(DateUtils.getNowDate());
        order.setUpdateBy(order.getCreateBy());
        order.setUpdateTime(order.getCreateTime());
        recalcOrderMapper.insert(order);
        auditService.recordAudit(scene.getSceneId(), "RECALC", String.valueOf(order.getRecalcId()),
                "APPLY", "发起重算申请", null, order, bo.getRequestNo());
        return 1;
    }

    @Override
    @Transactional(transactionManager = "costLiteTransactionManager", rollbackFor = Exception.class)
    public int approveRecalc(Long recalcId, CostRecalcApproveBo bo) {
        CostRecalcOrder before = requireRecalc(recalcId);
        boolean approved = Boolean.TRUE.equals(bo.getApproved());
        String status = approved ? RECALC_STATUS_APPROVED : RECALC_STATUS_REJECTED;
        Date now = DateUtils.getNowDate();
        int rows = recalcOrderMapper.update(null, Wrappers.<CostRecalcOrder>lambdaUpdate()
                .eq(CostRecalcOrder::getRecalcId, recalcId)
                .set(CostRecalcOrder::getRecalcStatus, status)
                .set(CostRecalcOrder::getApproveOpinion, firstNonBlank(bo.getApproveOpinion(), ""))
                .set(CostRecalcOrder::getApproveBy, firstNonBlank(SecurityUtils.getUsername(), "system"))
                .set(CostRecalcOrder::getApproveTime, now)
                .set(CostRecalcOrder::getUpdateBy, firstNonBlank(SecurityUtils.getUsername(), "system"))
                .set(CostRecalcOrder::getUpdateTime, now));
        auditService.recordAudit(before.getSceneId(), "RECALC", String.valueOf(before.getRecalcId()),
                approved ? "APPROVE" : "REJECT", approved ? "审核通过重算" : "驳回重算", before,
                recalcOrderMapper.selectById(recalcId), "");
        return rows;
    }

    @Override
    @Transactional(transactionManager = "costLiteTransactionManager", rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public int executeRecalc(Long recalcId) {
        CostRecalcOrder before = requireRecalc(recalcId);
        if (!RECALC_STATUS_APPROVED.equals(before.getRecalcStatus())) {
            throw new ServiceException("只有审核通过的重算申请才能执行");
        }
        CostCalcTask baselineTask = requireTask(before.getBaselineTaskId());
        List<CostCalcTaskDetail> details = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .eq(CostCalcTaskDetail::getTaskId, baselineTask.getTaskId())
                .orderByAsc(CostCalcTaskDetail::getPartitionNo));
        if (details.isEmpty()) {
            throw new ServiceException("基准任务没有可重算的输入明细");
        }
        List<String> inputs = details.stream().map(CostCalcTaskDetail::getInputJson).collect(Collectors.toList());
        CostCalcTaskSubmitBo submitBo = new CostCalcTaskSubmitBo();
        submitBo.setSceneId(before.getSceneId());
        submitBo.setVersionId(before.getVersionId());
        submitBo.setBillMonth(before.getBillMonth());
        submitBo.setTaskType(details.size() == 1 ? TASK_TYPE_FORMAL_SINGLE : TASK_TYPE_FORMAL_BATCH);
        submitBo.setRequestNo(firstNonBlank(before.getRequestNo(), "RECALC-" + before.getRecalcId()));
        submitBo.setRemark("重算申请#" + before.getRecalcId());
        submitBo.setInputJson(details.size() == 1 ? inputs.get(0) : "[" + String.join(",", inputs) + "]");
        Map<String, Object> taskResult = runService.submitTask(submitBo);
        Map<String, Object> taskMap = taskResult == null ? Collections.emptyMap() : (Map<String, Object>) taskResult.get("task");
        Date now = DateUtils.getNowDate();
        int rows = recalcOrderMapper.update(null, Wrappers.<CostRecalcOrder>lambdaUpdate()
                .eq(CostRecalcOrder::getRecalcId, recalcId)
                .set(CostRecalcOrder::getRecalcStatus, RECALC_STATUS_RUNNING)
                .set(CostRecalcOrder::getTargetTaskId, longValue(taskMap.get("taskId")))
                .set(CostRecalcOrder::getTargetTaskNo, stringValue(taskMap.get("taskNo")))
                .set(CostRecalcOrder::getExecuteBy, firstNonBlank(SecurityUtils.getUsername(), "system"))
                .set(CostRecalcOrder::getExecuteTime, now)
                .set(CostRecalcOrder::getUpdateBy, firstNonBlank(SecurityUtils.getUsername(), "system"))
                .set(CostRecalcOrder::getUpdateTime, now));
        auditService.recordAudit(before.getSceneId(), "RECALC", String.valueOf(before.getRecalcId()),
                "EXECUTE", "执行重算", before, recalcOrderMapper.selectById(recalcId), submitBo.getRequestNo());
        return rows;
    }

    @Override
    public List<CostAuditLog> selectAuditList(CostAuditLog query) {
        return auditService.selectAuditList(query);
    }

    @Override
    public Map<String, Object> selectAuditStats(CostAuditLog query) {
        return auditService.selectAuditStats(query);
    }

    @Override
    public List<CostAlarmRecord> selectAlarmList(CostAlarmRecord query) {
        return alarmService.selectAlarmList(query);
    }

    @Override
    public Map<String, Object> selectAlarmStats(CostAlarmRecord query) {
        return alarmService.selectAlarmStats(query);
    }

    @Override
    public Map<String, Object> selectAlarmOverview(CostAlarmRecord query) {
        return alarmService.selectAlarmOverview(query);
    }

    @Override
    public int ackAlarm(Long alarmId) {
        return alarmService.ackAlarm(alarmId);
    }

    @Override
    public int resolveAlarm(Long alarmId) {
        return alarmService.resolveAlarm(alarmId);
    }

    @Override
    public Map<String, Object> selectRuntimeCacheStats(Long sceneId, Long versionId) {
        Collection<String> keys = selectRuntimeCacheKeys();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("cacheCount", keys == null ? 0 : keys.size());
        result.put("sceneId", sceneId);
        result.put("versionId", versionId);
        if (versionId != null) {
            String cacheKey = RUNTIME_CACHE_PREFIX + versionId;
            result.put("cacheKey", cacheKey);
            result.put("exists", hasRedisKey(cacheKey));
            result.put("expireSeconds", getRedisExpire(cacheKey));
        }
        List<String> keyList = keys == null ? Collections.emptyList() : new ArrayList<>(keys);
        result.put("cacheKeys", keyList.stream().limit(CACHE_KEY_SAMPLE_LIMIT).collect(Collectors.toList()));
        result.put("cacheKeySampleLimit", CACHE_KEY_SAMPLE_LIMIT);
        result.put("cacheKeyOverflow", Math.max(0, keyList.size() - CACHE_KEY_SAMPLE_LIMIT));
        String lockKey = distributedLockSupport.buildRuntimeCacheLockKey(sceneId, versionId);
        result.put("refreshLockKey", lockKey);
        result.put("refreshLocked", hasRedisKey(lockKey));
        result.put("redisStatus", buildRedisRuntimeStatus(keys));
        result.put("lastRefreshAudit", buildLastCacheRefreshAudit(sceneId, versionId));
        List<CostAlarmRecord> openCacheAlarms = selectOpenCacheAlarms(sceneId, versionId);
        result.put("openCacheAlarmCount", openCacheAlarms.size());
        result.put("latestCacheAlarm", buildLatestCacheAlarm(openCacheAlarms));
        return result;
    }

    @Override
    public Map<String, Object> refreshRuntimeCache(Long sceneId, Long versionId) {
        return distributedLockSupport.executeRuntimeCacheLock(sceneId, versionId,
                "当前正在刷新运行缓存，请稍后重试", () ->
                {
                    try {
                        List<String> keys = new ArrayList<>();
                        if (versionId != null) {
                            keys.add(RUNTIME_CACHE_PREFIX + versionId);
                        } else if (sceneId != null) {
                            List<CostPublishVersion> versions = publishVersionMapper.selectList(Wrappers.<CostPublishVersion>lambdaQuery()
                                    .eq(CostPublishVersion::getSceneId, sceneId));
                            keys.addAll(versions.stream().map(item -> RUNTIME_CACHE_PREFIX + item.getVersionId()).collect(Collectors.toList()));
                        } else {
                            Collection<String> allKeys = redisCache.keys(RUNTIME_CACHE_PREFIX + "*");
                            if (allKeys != null) {
                                keys.addAll(allKeys);
                            }
                        }
                        if (!keys.isEmpty()) {
                            redisCache.deleteObject(keys);
                        }
                        List<CostAlarmRecord> openAlarmsBeforeRefresh = selectOpenCacheAlarms(sceneId, versionId);
                        String sourceKey = buildCacheSourceKey(sceneId, versionId);
                        int resolvedAlarmCount = 0;
                        if (versionId != null || sceneId != null) {
                            resolvedAlarmCount = alarmService.autoResolveBySourceKey(sourceKey, "缓存刷新成功，自动关闭历史缓存告警");
                        }
                        LinkedHashMap<String, Object> refreshResult = new LinkedHashMap<>();
                        refreshResult.put("sceneId", sceneId);
                        refreshResult.put("versionId", versionId);
                        refreshResult.put("deletedCount", keys.size());
                        refreshResult.put("deletedKeys", keys);
                        refreshResult.put("resolvedAlarmCount", resolvedAlarmCount);
                        refreshResult.put("openAlarmCountBeforeRefresh", openAlarmsBeforeRefresh.size());
                        refreshResult.put("refreshedAt", DateUtils.getNowDate());
                        refreshResult.put("redisStatus", buildRedisRuntimeStatus(selectRuntimeCacheKeys()));
                        auditService.recordAudit(sceneId, CACHE_OBJECT_TYPE, versionId == null ? "RUNTIME" : String.valueOf(versionId),
                                CACHE_ACTION_REFRESH, "刷新运行快照缓存", null, refreshResult, "");
                        if (versionId != null || sceneId != null) {
                            refreshResult.put("lastRefreshAudit", buildLastCacheRefreshAudit(sceneId, versionId));
                        }
                        refreshResult.put("statsAfterRefresh", selectRuntimeCacheStats(sceneId, versionId));
                        return refreshResult;
                    } catch (Exception e) {
                        CostAlarmRecord alarm = new CostAlarmRecord();
                        alarm.setSceneId(sceneId);
                        alarm.setVersionId(versionId);
                        alarm.setAlarmType(CACHE_ALARM_TYPE_REFRESH_FAILED);
                        alarm.setAlarmLevel("ERROR");
                        alarm.setAlarmTitle("运行快照缓存刷新失败");
                        alarm.setAlarmContent(limitLength(e.getMessage(), 1000));
                        alarm.setSourceKey(buildCacheSourceKey(sceneId, versionId));
                        alarmService.createAlarm(alarm);
                        throw e;
                    }
                });
    }

    private Collection<String> selectRuntimeCacheKeys() {
        try {
            Collection<String> keys = redisCache.keys(RUNTIME_CACHE_PREFIX + "*");
            return keys == null ? Collections.emptyList() : keys;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private boolean hasRedisKey(String key) {
        if (StringUtils.isEmpty(key)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisCache.hasKey(key));
        } catch (Exception ignored) {
            return false;
        }
    }

    private Long getRedisExpire(String key) {
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        try {
            return redisCache.getExpire(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildRedisRuntimeStatus(Collection<String> runtimeKeys) {
        LinkedHashMap<String, Object> status = new LinkedHashMap<>();
        status.put("connected", false);
        status.put("runtimeCacheKeyCount", runtimeKeys == null ? 0 : runtimeKeys.size());
        try {
            if (redisCache == null || redisCache.redisTemplate == null) {
                status.put("state", "UNAVAILABLE");
                status.put("message", "Redis 未配置，当前使用进程内缓存");
                return status;
            }
            RedisConnectionFactory connectionFactory = redisCache.redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                status.put("state", "UNAVAILABLE");
                status.put("message", "Redis connection factory is unavailable");
                return status;
            }
            try (RedisConnection connection = connectionFactory.getConnection()) {
                Properties info = connection.serverCommands().info();
                status.put("connected", true);
                status.put("state", "UP");
                status.put("ping", connection.ping());
                status.put("dbSize", connection.serverCommands().dbSize());
                putRedisInfo(status, info, "redis_version", "redisVersion");
                putRedisInfo(status, info, "redis_mode", "redisMode");
                putRedisInfo(status, info, "connected_clients", "connectedClients");
                putRedisInfo(status, info, "used_memory_human", "usedMemoryHuman");
                putRedisInfo(status, info, "maxmemory_human", "maxMemoryHuman");
                putRedisInfo(status, info, "instantaneous_ops_per_sec", "opsPerSecond");
                putRedisInfo(status, info, "rdb_last_bgsave_status", "lastSaveStatus");
            }
        } catch (Exception e) {
            status.put("state", "DOWN");
            status.put("message", limitLength(e.getMessage(), 300));
        }
        return status;
    }

    private void putRedisInfo(Map<String, Object> status, Properties info, String sourceKey, String targetKey) {
        if (info != null && info.getProperty(sourceKey) != null) {
            status.put(targetKey, info.getProperty(sourceKey));
        }
    }

    private Map<String, Object> buildLastCacheRefreshAudit(Long sceneId, Long versionId) {
        try {
            CostAuditLog query = new CostAuditLog();
            query.setSceneId(sceneId);
            query.setObjectType(CACHE_OBJECT_TYPE);
            query.setActionType(CACHE_ACTION_REFRESH);
            if (versionId != null) {
                query.setObjectCode(String.valueOf(versionId));
            }
            List<CostAuditLog> audits = auditService.selectAuditList(query);
            Optional<CostAuditLog> latest = audits.stream()
                    .filter(item -> versionId == null || String.valueOf(versionId).equals(item.getObjectCode()))
                    .findFirst();
            if (latest.isEmpty()) {
                return Collections.emptyMap();
            }
            CostAuditLog audit = latest.get();
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("auditId", audit.getAuditId());
            result.put("sceneId", audit.getSceneId());
            result.put("objectCode", audit.getObjectCode());
            result.put("operatorCode", audit.getOperatorCode());
            result.put("operatorName", audit.getOperatorName());
            result.put("operateTime", audit.getOperateTime());
            result.put("actionSummary", audit.getActionSummary());
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private List<CostAlarmRecord> selectOpenCacheAlarms(Long sceneId, Long versionId) {
        try {
            CostAlarmRecord query = new CostAlarmRecord();
            query.setSceneId(sceneId);
            query.setAlarmType(CACHE_ALARM_TYPE_REFRESH_FAILED);
            query.setAlarmStatus(ALARM_STATUS_OPEN);
            List<CostAlarmRecord> alarms = alarmService.selectAlarmList(query);
            if (versionId == null) {
                return alarms;
            }
            String sourceKey = buildCacheSourceKey(sceneId, versionId);
            return alarms.stream()
                    .filter(item -> Objects.equals(versionId, item.getVersionId()) || sourceKey.equals(item.getSourceKey()))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildLatestCacheAlarm(List<CostAlarmRecord> alarms) {
        if (alarms == null || alarms.isEmpty()) {
            return Collections.emptyMap();
        }
        CostAlarmRecord latest = alarms.get(0);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("alarmId", latest.getAlarmId());
        result.put("alarmTitle", latest.getAlarmTitle());
        result.put("alarmContent", latest.getAlarmContent());
        result.put("alarmLevel", latest.getAlarmLevel());
        result.put("latestTriggerTime", latest.getLatestTriggerTime());
        result.put("occurrenceCount", latest.getOccurrenceCount());
        return result;
    }

    private String buildCacheSourceKey(Long sceneId, Long versionId) {
        return "CACHE:" + (versionId == null ? String.valueOf(sceneId) : versionId);
    }

    private CostBillPeriod buildPeriodQuery(Long sceneId) {
        CostBillPeriod query = new CostBillPeriod();
        query.setSceneId(sceneId);
        return query;
    }

    private CostRecalcOrder buildRecalcQuery(Long sceneId, String billMonth) {
        CostRecalcOrder query = new CostRecalcOrder();
        query.setSceneId(sceneId);
        query.setBillMonth(billMonth);
        return query;
    }

    private void enrichPeriods(List<CostBillPeriod> periods) {
        Set<Long> sceneIds = periods.stream().map(CostBillPeriod::getSceneId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> versionIds = periods.stream().map(CostBillPeriod::getActiveVersionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CostScene> sceneMap = sceneIds.isEmpty() ? Collections.emptyMap()
                : sceneMapper.selectBatchIds(sceneIds).stream().collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        Map<Long, CostPublishVersion> versionMap = versionIds.isEmpty() ? Collections.emptyMap()
                : publishVersionMapper.selectBatchIds(versionIds).stream().collect(Collectors.toMap(CostPublishVersion::getVersionId, item -> item));
        periods.forEach(item -> {
            CostScene scene = sceneMap.get(item.getSceneId());
            if (scene != null) {
                item.setSceneCode(scene.getSceneCode());
                item.setSceneName(scene.getSceneName());
            }
            CostPublishVersion version = versionMap.get(item.getActiveVersionId());
            if (version != null) {
                item.setVersionNo(version.getVersionNo());
            }
        });
    }

    private void enrichRecalcOrders(List<CostRecalcOrder> orders) {
        Set<Long> sceneIds = orders.stream().map(CostRecalcOrder::getSceneId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> versionIds = orders.stream().map(CostRecalcOrder::getVersionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CostScene> sceneMap = sceneIds.isEmpty() ? Collections.emptyMap()
                : sceneMapper.selectBatchIds(sceneIds).stream().collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        Map<Long, CostPublishVersion> versionMap = versionIds.isEmpty() ? Collections.emptyMap()
                : publishVersionMapper.selectBatchIds(versionIds).stream().collect(Collectors.toMap(CostPublishVersion::getVersionId, item -> item));
        orders.forEach(item -> {
            CostScene scene = sceneMap.get(item.getSceneId());
            if (scene != null) {
                item.setSceneCode(scene.getSceneCode());
                item.setSceneName(scene.getSceneName());
            }
            CostPublishVersion version = versionMap.get(item.getVersionId());
            if (version != null) {
                item.setVersionNo(version.getVersionNo());
            }
        });
    }

    private Map<String, Object> buildResultStats(Long sceneId, String billMonth, Long taskId) {
        List<CostResultLedger> ledgers = resultLedgerMapper.selectList(Wrappers.<CostResultLedger>lambdaQuery()
                .eq(sceneId != null, CostResultLedger::getSceneId, sceneId)
                .eq(StringUtils.isNotEmpty(billMonth), CostResultLedger::getBillMonth, billMonth)
                .eq(taskId != null, CostResultLedger::getTaskId, taskId));
        BigDecimal amountTotal = ledgers.stream().map(CostResultLedger::getAmountValue).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("resultCount", ledgers.size());
        stats.put("amountTotal", amountTotal);
        stats.put("feeCount", ledgers.stream().map(CostResultLedger::getFeeCode).filter(StringUtils::isNotEmpty).distinct().count());
        return stats;
    }

    private List<Map<String, Object>> buildRecalcImpactItems(CostRecalcOrder order, CostBillPeriod period,
                                                             CostCalcTask baselineTask, CostCalcTask latestTask,
                                                             long inputCount, long bizCount,
                                                             long baselineResultCount, long latestResultCount,
                                                             long periodLedgerCount,
                                                             BigDecimal baselineAmount, BigDecimal latestAmount) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildImpactItem("ADD_TASK", "追加重算任务",
                "将基于基准任务 " + baselineTask.getTaskNo() + " 的输入明细创建新的正式核算任务，原任务和原结果保留用于追溯。",
                1L, "INFO"));
        items.add(buildImpactItem("ADD_RESULT_LEDGER", "追加目标结果",
                "目标任务完成后会新增结果台账记录；预计重放 " + inputCount + " 条输入、覆盖 " + bizCount
                        + " 个业务单号，实际结果条数以目标版本规则命中为准。",
                inputCount, "INFO"));
        items.add(buildImpactItem("UPDATE_PERIOD_SUMMARY", "切换账期最新摘要",
                "账期 " + order.getBillMonth() + " 的最新任务、默认版本、结果条数和金额摘要会切换到本次重算任务；当前最新任务为 "
                        + firstNonBlank(latestTask == null ? "" : latestTask.getTaskNo(), "-") + "，当前最新结果 "
                        + latestResultCount + " 条、金额 " + defaultZero(latestAmount).setScale(2, RoundingMode.HALF_UP) + "。",
                1L, "WARN"));
        items.add(buildImpactItem("KEEP_HISTORY_LEDGER", "保留历史结果",
                "不会删除当前账期已有结果台账；当前账期累计已有 " + periodLedgerCount + " 条结果，基准任务 "
                        + baselineTask.getTaskNo() + " 有 " + baselineResultCount + " 条结果、金额 "
                        + defaultZero(baselineAmount).setScale(2, RoundingMode.HALF_UP) + "。",
                periodLedgerCount, "INFO"));
        if (PERIOD_STATUS_SEALED.equals(period.getPeriodStatus())) {
            items.add(buildImpactItem("SEALED_PERIOD", "封存账期提示",
                    "该账期已封存，重算将通过已审核申请进入治理链路，不等同于直接提交普通正式核算。",
                    1L, "WARN"));
        }
        return items;
    }

    private Map<String, Object> buildImpactItem(String impactType, String title, String message, long count, String level) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("impactType", impactType);
        item.put("title", title);
        item.put("message", message);
        item.put("count", count);
        item.put("level", level);
        return item;
    }

    private BigDecimal sumTaskAmount(Long taskId) {
        if (taskId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return defaultZero(resultLedgerMapper.sumAmountByTaskId(taskId)).setScale(2, RoundingMode.HALF_UP);
    }

    private long countTaskResults(Long taskId) {
        if (taskId == null) {
            return 0L;
        }
        Long count = resultLedgerMapper.selectCount(Wrappers.<CostResultLedger>lambdaQuery()
                .eq(CostResultLedger::getTaskId, taskId));
        return count == null ? 0L : count;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<String, Object> parseJson(String text) {
        if (StringUtils.isEmpty(text)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private CostScene requireScene(Long sceneId) {
        CostScene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new ServiceException("所属场景不存在，请刷新后重试");
        }
        return scene;
    }

    private CostPublishVersion requireVersion(Long versionId) {
        CostPublishVersion version = publishVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException("目标版本不存在，请刷新后重试");
        }
        return version;
    }

    private CostCalcTask requireTask(Long taskId) {
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("基准任务不存在，请刷新后重试");
        }
        return task;
    }

    private CostBillPeriod requirePeriod(Long periodId) {
        CostBillPeriod period = billPeriodMapper.selectById(periodId);
        if (period == null) {
            throw new ServiceException("账期记录不存在，请刷新后重试");
        }
        return period;
    }

    private CostBillPeriod requirePeriod(Long sceneId, String billMonth) {
        CostBillPeriod period = billPeriodMapper.selectOne(Wrappers.<CostBillPeriod>lambdaQuery()
                .eq(CostBillPeriod::getSceneId, sceneId)
                .eq(CostBillPeriod::getBillMonth, billMonth)
                .last("limit 1"));
        if (period == null) {
            throw new ServiceException("当前场景账期不存在，请先创建账期");
        }
        return period;
    }

    private CostRecalcOrder requireRecalc(Long recalcId) {
        CostRecalcOrder order = recalcOrderMapper.selectById(recalcId);
        if (order == null) {
            throw new ServiceException("重算申请不存在，请刷新后重试");
        }
        return order;
    }

    private void ensureBillMonth(String billMonth) {
        if (StringUtils.isEmpty(billMonth) || !billMonth.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException("账期格式必须为 yyyy-MM");
        }
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    private String limitLength(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
