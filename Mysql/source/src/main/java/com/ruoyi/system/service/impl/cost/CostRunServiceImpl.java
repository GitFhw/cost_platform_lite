package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.cost.CostDispatchProperties;
import com.ruoyi.system.domain.cost.*;
import com.ruoyi.system.domain.cost.bo.*;
import com.ruoyi.system.mapper.cost.*;
import com.ruoyi.system.service.cost.ICostAlarmService;
import com.ruoyi.system.service.cost.ICostAuditService;
import com.ruoyi.system.service.cost.ICostExpressionService;
import com.ruoyi.system.service.cost.ICostRunService;
import com.ruoyi.system.service.cost.execution.CostNodeExecutor;
import com.ruoyi.system.service.cost.execution.model.ExecutionResult;
import com.ruoyi.system.service.cost.execution.model.FeeExecutionResult;
import com.ruoyi.system.service.cost.execution.view.FeeExecutionViewAssembler;
import com.ruoyi.system.service.cost.remote.AccessProfileInputMappingService;
import com.ruoyi.system.service.cost.remote.AccessProfileInputMappingService.InputBuildContext;
import com.ruoyi.system.service.cost.variable.runtime.RuntimeRemoteVariableValueService;
import com.ruoyi.system.service.cost.variable.runtime.RuntimeVariableComputeService;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;
import static com.ruoyi.system.service.cost.execution.CostExecutionConstants.RULE_TYPE_FORMULA;

/**
 * 成本核算主链服务实现
 *
 * <p>该服务围绕“发布快照 -> 试算/正式任务 -> 结果台账 -> 追溯解释”的主链路展开。</p>
 *
 * @author HwFan
 */
@Service
public class CostRunServiceImpl implements ICostRunService {
    private static final Logger log = LoggerFactory.getLogger(CostRunServiceImpl.class);
    private static final String PARTITION_PERSIST_MODE_BATCH = "BATCH";
    private static final String PARTITION_PERSIST_MODE_SINGLE = "SINGLE_FALLBACK";
    private static final String PARTITION_STAGE_EXECUTION = "EXECUTION";
    private static final String PARTITION_STAGE_BATCH_PERSIST = "BATCH_PERSIST";
    private static final String PARTITION_STAGE_SINGLE_PERSIST = "SINGLE_PERSIST";
    private static final String OP_EXPR = "EXPR";
    private static final String INTERVAL_LCRO = "LEFT_CLOSED_RIGHT_OPEN";
    private static final String SNAPSHOT_MODE_ACTIVE = "ACTIVE";
    private static final String SNAPSHOT_MODE_DRAFT = "DRAFT";
    private static final String RUNTIME_CACHE_PREFIX = "cost:runtime:snapshot:";
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DecimalFormat PARTITION_FORMAT = new DecimalFormat("000");
    private static final int DEFAULT_TASK_PARTITION_SIZE = 500;
    private static final int DEFAULT_TASK_DETAIL_INSERT_SIZE = 200;
    private static final int DEFAULT_TASK_PARTITION_INSERT_SIZE = 200;
    private static final int DEFAULT_TASK_PARALLELISM = 8;
    private static final int TASK_DISPATCH_SCAN_LIMIT = 5;
    private static final int TASK_DISPATCH_MAX_ROUNDS_PER_SCAN = 3;
    private static final long DEFAULT_TASK_DISPATCH_INTERVAL_SECONDS = 15L;
    private static final long DEFAULT_TASK_STALE_TIMEOUT_SECONDS = 600L;
    private static final long TASK_HEARTBEAT_INTERVAL_MILLIS = 30_000L;
    private static final int DEFAULT_INPUT_BATCH_INSERT_SIZE = 200;
    private static final int RESULT_EXPORT_MAX_ROWS = 100_000;
    private static final int RESULT_COMPARE_MAX_ROWS = 100_000;
    private static final String DRAFT_VERSION_LABEL = "草稿版本";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean taskDispatchCoordinatorStarted = new AtomicBoolean(false);
    private final Set<Long> activeTaskPartitionAssistIds = ConcurrentHashMap.newKeySet();
    @Autowired
    private CostSimulationRecordMapper simulationRecordMapper;
    @Autowired
    private CostCalcTaskMapper calcTaskMapper;
    @Autowired
    private CostCalcInputBatchMapper calcInputBatchMapper;
    @Autowired
    private CostCalcInputBatchItemMapper calcInputBatchItemMapper;
    @Autowired
    private CostCalcTaskDetailMapper calcTaskDetailMapper;
    @Autowired
    private CostCalcTaskPartitionMapper calcTaskPartitionMapper;
    @Autowired
    private CostResultLedgerMapper resultLedgerMapper;
    @Autowired
    private CostResultTraceMapper resultTraceMapper;
    @Autowired
    private CostSceneMapper sceneMapper;
    @Autowired
    private CostPublishVersionMapper publishVersionMapper;
    @Autowired
    private CostBillPeriodMapper billPeriodMapper;
    @Autowired
    private CostRecalcOrderMapper recalcOrderMapper;
    @Autowired
    private CostFeeMapper feeMapper;
    @Autowired
    private CostVariableMapper variableMapper;
    @Autowired
    private CostFormulaMapper formulaMapper;
    @Autowired
    private CostRuleMapper ruleMapper;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private ScheduledExecutorService scheduledExecutorService;
    @Autowired
    private RedisCache redisCache;
    @Autowired
    private ICostAuditService auditService;
    @Autowired
    private ICostAlarmService alarmService;
    @Autowired
    private ICostExpressionService expressionService;
    @Autowired
    private RuntimeVariableComputeService runtimeVariableComputeService;

    @Autowired
    private CostNodeExecutor costNodeExecutor;
    @Autowired
    private FeeExecutionViewAssembler feeExecutionViewAssembler;
    @Autowired
    private RuntimeRemoteVariableValueService runtimeRemoteVariableValueService;
    @Autowired
    private CostDistributedLockSupport distributedLockSupport;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AccessProfileInputMappingService accessProfileInputMappingService;
    @Autowired
    private Environment environment;
    @Autowired
    private CostDispatchProperties costDispatchProperties;

    @PostConstruct
    public void startTaskDispatchCoordinator() {
        if (costDispatchProperties != null && !costDispatchProperties.isEnabled()) {
            log.info("成本任务后台调度扫描已关闭，保留显式任务提交和同步计费能力");
            return;
        }
        if (!taskDispatchCoordinatorStarted.compareAndSet(false, true)) {
            return;
        }
        long dispatchIntervalSeconds = resolveTaskDispatchIntervalSeconds();
        scheduledExecutorService.scheduleWithFixedDelay(this::dispatchRecoverableTasksSafely,
                dispatchIntervalSeconds, dispatchIntervalSeconds, TimeUnit.SECONDS);
    }

    private void dispatchRecoverableTasksSafely() {
        try {
            boolean dispatched = distributedLockSupport.executeTaskDispatchCoordinatorLockOrSkip(
                    this::dispatchRecoverableTasksOnce);
            if (!dispatched && log.isDebugEnabled()) {
                log.debug("成本任务调度扫描已由其他节点或线程执行，本轮跳过重复恢复扫描");
            }
        } catch (Exception ex) {
            log.warn("成本任务分布式调度扫描失败", ex);
        }
        dispatchRunnableTaskPartitionsSafely();
    }

    private void dispatchRunnableTaskPartitionsSafely() {
        try {
            dispatchRunnableTaskPartitions();
        } catch (Exception ex) {
            log.warn("成本任务跨节点分片协同扫描失败", ex);
        }
    }

    private void dispatchRunnableTaskPartitions() {
        String currentNode = resolveExecuteNode();
        Date cutoff = new Date(System.currentTimeMillis() - resolveTaskStaleTimeoutMillis());
        List<CostCalcTask> runningTasks = calcTaskMapper.selectList(Wrappers.<CostCalcTask>lambdaQuery()
                .eq(CostCalcTask::getTaskStatus, TASK_STATUS_RUNNING)
                .ne(StringUtils.isNotEmpty(currentNode), CostCalcTask::getExecuteNode, currentNode)
                .ge(CostCalcTask::getUpdateTime, cutoff)
                .orderByAsc(CostCalcTask::getUpdateTime)
                .last("limit " + TASK_DISPATCH_SCAN_LIMIT));
        for (CostCalcTask task : runningTasks) {
            if (task == null || task.getTaskId() == null || !hasRunnableInitPartition(task.getTaskId())) {
                continue;
            }
            scheduleTaskPartitionAssist(task.getTaskId());
        }
    }

    private boolean hasRunnableInitPartition(Long taskId) {
        if (taskId == null) {
            return false;
        }
        return calcTaskPartitionMapper.selectCount(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT)) > 0;
    }

    private void scheduleTaskPartitionAssist(Long taskId) {
        if (taskId == null || !activeTaskPartitionAssistIds.add(taskId)) {
            return;
        }
        threadPoolTaskExecutor.execute(() ->
        {
            try {
                assistRunningTaskPartitions(taskId);
            } finally {
                activeTaskPartitionAssistIds.remove(taskId);
            }
        });
    }

    void dispatchRecoverableTasksOnce() {
        Set<Long> dispatchedInitTaskIds = new LinkedHashSet<>();
        Set<Long> dispatchedStaleTaskIds = new LinkedHashSet<>();
        for (int round = 0; round < TASK_DISPATCH_MAX_ROUNDS_PER_SCAN; round++) {
            int initDispatched = dispatchPendingInitTasks(dispatchedInitTaskIds);
            int staleDispatched = dispatchStaleRunningTasks(dispatchedStaleTaskIds);
            if (initDispatched < TASK_DISPATCH_SCAN_LIMIT && staleDispatched < TASK_DISPATCH_SCAN_LIMIT) {
                break;
            }
        }
    }

    @Override
    public Map<String, Object> selectSimulationStats(CostSimulationRecord query) {
        List<CostSimulationRecord> records = simulationRecordMapper.selectList(Wrappers.<CostSimulationRecord>lambdaQuery()
                .eq(query.getSceneId() != null, CostSimulationRecord::getSceneId, query.getSceneId())
                .eq(query.getVersionId() != null, CostSimulationRecord::getVersionId, query.getVersionId())
                .eq(StringUtils.isNotEmpty(query.getBillMonth()), CostSimulationRecord::getBillMonth, query.getBillMonth())
                .eq(StringUtils.isNotEmpty(query.getStatus()), CostSimulationRecord::getStatus, query.getStatus()));
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("simulationCount", records.size());
        stats.put("successCount", records.stream().filter(item -> SIMULATION_STATUS_SUCCESS.equals(item.getStatus())).count());
        stats.put("failedCount", records.stream().filter(item -> SIMULATION_STATUS_FAILED.equals(item.getStatus())).count());
        stats.put("sceneCount", records.stream().map(CostSimulationRecord::getSceneId).filter(Objects::nonNull).distinct().count());
        return stats;
    }

    @Override
    public List<CostSimulationRecord> selectSimulationList(CostSimulationRecord query) {
        List<CostSimulationRecord> records = simulationRecordMapper.selectList(Wrappers.<CostSimulationRecord>lambdaQuery()
                .eq(query.getSceneId() != null, CostSimulationRecord::getSceneId, query.getSceneId())
                .eq(query.getVersionId() != null, CostSimulationRecord::getVersionId, query.getVersionId())
                .eq(StringUtils.isNotEmpty(query.getBillMonth()), CostSimulationRecord::getBillMonth, query.getBillMonth())
                .eq(StringUtils.isNotEmpty(query.getStatus()), CostSimulationRecord::getStatus, query.getStatus())
                .orderByDesc(CostSimulationRecord::getSimulationId));
        enrichSimulationRecords(records);
        return records;
    }

    private void assistRunningTaskPartitions(Long taskId) {
        if (taskId == null) {
            return;
        }
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null || !TASK_STATUS_RUNNING.equals(task.getTaskStatus()) || isTaskStale(task)) {
            return;
        }
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(task.getSceneId(), task.getVersionId(), true);
        int dispatchedCount = 0;
        while (dispatchedCount < TASK_DISPATCH_SCAN_LIMIT) {
            Integer partitionNo = selectNextRunnablePartitionNo(taskId);
            if (partitionNo == null) {
                break;
            }
            if (assistSingleRunnablePartition(taskId, snapshot, partitionNo)) {
                dispatchedCount++;
            }
        }
    }

    private Integer selectNextRunnablePartitionNo(Long taskId) {
        CostCalcTaskPartition partition = calcTaskPartitionMapper.selectOne(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT)
                .orderByAsc(CostCalcTaskPartition::getPartitionNo)
                .last("limit 1"));
        return partition == null ? null : partition.getPartitionNo();
    }

    private boolean assistSingleRunnablePartition(Long taskId, RuntimeSnapshot snapshot, Integer partitionNo) {
        if (taskId == null || partitionNo == null || snapshot == null) {
            return false;
        }
        List<CostCalcTaskDetail> partitionDetails = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .eq(CostCalcTaskDetail::getTaskId, taskId)
                .eq(CostCalcTaskDetail::getPartitionNo, partitionNo)
                .in(CostCalcTaskDetail::getDetailStatus, DETAIL_STATUS_INIT, DETAIL_STATUS_FAILED)
                .orderByAsc(CostCalcTaskDetail::getDetailId));
        if (partitionDetails.isEmpty()) {
            return false;
        }
        PartitionClaimToken claimToken = tryMarkPartitionRunning(taskId, partitionDetails);
        if (claimToken == null) {
            return false;
        }
        try {
            PartitionExecutionResult result = executeTaskPartition(taskId, snapshot, partitionDetails, claimToken);
            finishPartition(taskId, partitionDetails, claimToken, result, null);
        } catch (Exception ex) {
            Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
            PartitionExecutionResult fallbackResult = markPartitionFailed(taskId, partitionDetails, claimToken, cause);
            finishPartition(taskId, partitionDetails, claimToken, fallbackResult, cause);
        }
        refreshTaskProgress(taskId);
        tryFinalizeTaskIfReady(taskId);
        return true;
    }

    private PartitionClaimToken tryMarkPartitionRunning(Long taskId, List<CostCalcTaskDetail> partition) {
        try {
            return markPartitionRunning(taskId, partition);
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private void tryFinalizeTaskIfReady(Long taskId) {
        if (taskId == null) {
            return;
        }
        long pendingPartitions = calcTaskPartitionMapper.selectCount(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .in(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT, TASK_STATUS_RUNNING));
        if (pendingPartitions > 0) {
            return;
        }
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null || TASK_STATUS_CANCELLED.equals(task.getTaskStatus())) {
            return;
        }
        Date startedTime = task.getStartedTime() == null ? DateUtils.getNowDate() : task.getStartedTime();
        finishTask(taskId, startedTime);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeSimulation(CostSimulationExecuteBo bo) {
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(bo.getSceneId(), bo.getVersionId(), false, true);
        List<RuntimeFee> targetFees = resolveTargetRuntimeFees(snapshot, bo.getFeeIds(), bo.getFeeId(), bo.getFeeCode());
        List<RuntimeFee> executionFees = resolveFeeExecutionChains(snapshot, targetFees);
        boolean includeExplain = Boolean.TRUE.equals(bo.getIncludeExplain());
        String billMonth = StringUtils.isEmpty(bo.getBillMonth()) ? "" : bo.getBillMonth();
        if (StringUtils.isNotEmpty(billMonth)) {
            validateBillMonth(billMonth);
        }
        Map<String, Object> input = parseObjectJson(bo.getInputJson(), "试算输入必须是 JSON 对象");
        CostSimulationRecord record = executeAndPersistSimulation(snapshot, input, "", billMonth,
                targetFees, executionFees, includeExplain);
        return selectSimulationDetail(record.getSimulationId());
    }

    @Override
    public Map<String, Object> executeSimulationBatch(CostSimulationExecuteBo bo) {
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(bo.getSceneId(), bo.getVersionId(), false, true);
        List<RuntimeFee> targetFees = resolveTargetRuntimeFees(snapshot, bo.getFeeIds(), bo.getFeeId(), bo.getFeeCode());
        List<RuntimeFee> executionFees = resolveFeeExecutionChains(snapshot, targetFees);
        boolean includeExplain = Boolean.TRUE.equals(bo.getIncludeExplain());
        String billMonth = StringUtils.isEmpty(bo.getBillMonth()) ? "" : bo.getBillMonth();
        if (StringUtils.isNotEmpty(billMonth)) {
            validateBillMonth(billMonth);
        }
        List<Map<String, Object>> inputs = parseArrayJson(bo.getInputJson(), "批量试算输入必须是 JSON 数组");
        if (inputs.isEmpty()) {
            throw new ServiceException("批量试算输入不能为空数组");
        }
        validateDuplicateBizNo(inputs);
        List<CostSimulationRecord> records = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            Map<String, Object> input = inputs.get(i);
            String bizNo = resolveBizNo(input, i + 1);
            records.add(executeAndPersistSimulation(snapshot, input, bizNo, billMonth,
                    targetFees, executionFees, includeExplain));
        }

        enrichSimulationRecords(records);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneId", snapshot.sceneId);
        result.put("sceneCode", snapshot.sceneCode);
        result.put("sceneName", snapshot.sceneName);
        result.put("versionId", snapshot.versionId);
        result.put("versionNo", snapshot.versionNo);
        result.put("snapshotSource", snapshot.snapshotSource);
        result.put("billMonth", billMonth);
        result.put("totalCount", records.size());
        result.put("successCount", records.stream().filter(item -> SIMULATION_STATUS_SUCCESS.equals(item.getStatus())).count());
        result.put("failedCount", records.stream().filter(item -> SIMULATION_STATUS_FAILED.equals(item.getStatus())).count());
        result.put("records", records.stream().map(this::buildSimulationBatchItem).collect(Collectors.toList()));
        return result;
    }

    private CostSimulationRecord executeAndPersistSimulation(RuntimeSnapshot snapshot, Map<String, Object> input,
                                                             String bizNo, String billMonth,
                                                             List<RuntimeFee> targetFees,
                                                             List<RuntimeFee> executionFees,
                                                             boolean includeExplain) {
        Date now = DateUtils.getNowDate();
        String operator = resolveOperator();
        CostSimulationRecord record = new CostSimulationRecord();
        record.setSceneId(snapshot.sceneId);
        record.setVersionId(snapshot.versionId);
        record.setVersionNo(snapshot.versionNo);
        record.setBillMonth(billMonth);
        record.setSimulationNo(buildRunNo("SIM"));
        record.setInputJson(writeJson(input));
        record.setCreateBy(operator);
        record.setCreateTime(now);
        try {
            ExecutionResult executionResult = executeSingle(snapshot, "SIMULATION", billMonth, input,
                    executionFees, includeExplain);
            appendSimulationScope(executionResult, snapshot, targetFees, executionFees);
            record.setVariableJson(writeJson(executionResult.variableView));
            record.setExplainJson(writeJson(executionResult.explainView));
            record.setResultJson(writeJson(executionResult.resultView));
            record.setStatus(SIMULATION_STATUS_SUCCESS);
            record.setErrorMessage("");
            simulationRecordMapper.insert(record);
            return record;
        } catch (Exception e) {
            record.setVariableJson(writeJson(Collections.emptyMap()));
            record.setExplainJson(writeJson(Collections.singletonMap("error", e.getMessage())));
            record.setResultJson(writeJson(Collections.emptyMap()));
            record.setStatus(SIMULATION_STATUS_FAILED);
            record.setErrorMessage(limitLength(e.getMessage(), 1000));
            simulationRecordMapper.insert(record);
            if (StringUtils.isEmpty(bizNo)) {
                throw e instanceof ServiceException ? (ServiceException) e : new ServiceException("试算执行失败：" + e.getMessage());
            }
            return record;
        }
    }

    /**
     * 将试算目标与依赖执行范围写入结果，避免指定费目包含依赖费目时产生歧义。
     */
    private void appendSimulationScope(ExecutionResult executionResult, RuntimeSnapshot snapshot,
                                       List<RuntimeFee> targetFees, List<RuntimeFee> executionFees) {
        if (executionResult == null || executionResult.resultView == null) {
            return;
        }
        List<RuntimeFee> normalizedTargets = targetFees == null ? Collections.emptyList() : targetFees;
        List<RuntimeFee> normalizedExecution = executionFees == null ? Collections.emptyList() : executionFees;
        Set<String> targetCodes = normalizedTargets.stream()
                .filter(Objects::nonNull)
                .map(item -> item.feeCode)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> executionCodes = normalizedExecution.stream()
                .filter(Objects::nonNull)
                .map(item -> item.feeCode)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
        executionResult.resultView.put("feeScope", isAllFeeScope(snapshot, normalizedTargets) ? "ALL" : "SELECTED");
        executionResult.resultView.put("targetFeeCodes", new ArrayList<>(targetCodes));
        executionResult.resultView.put("executionFeeCodes", executionCodes);
        executionResult.resultView.put("dependentFeeCodes", executionCodes.stream()
                .filter(code -> !targetCodes.contains(code))
                .collect(Collectors.toList()));
    }

    private Map<String, Object> buildSimulationBatchItem(CostSimulationRecord record) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        Map<String, Object> input = parseObjectJson(record.getInputJson(), "试算输入必须是 JSON 对象");
        item.put("simulationId", record.getSimulationId());
        item.put("simulationNo", record.getSimulationNo());
        item.put("billMonth", firstNonBlank(record.getBillMonth(), ""));
        item.put("status", record.getStatus());
        item.put("bizNo", resolveString(input, "bizNo", "biz_no"));
        item.put("errorMessage", record.getErrorMessage());
        item.put("simulationTime", record.getCreateTime());
        // 批量项沿用单条试算详情的四个结果节点，调用方可直接读取 amountTotal 和 feeResults。
        item.put("input", input);
        item.put("variables", parseJsonToObject(record.getVariableJson()));
        item.put("explain", parseJsonToObject(record.getExplainJson()));
        item.put("result", parseJsonToObject(record.getResultJson()));
        return item;
    }

    private List<CostSimulationChargeExportRow> buildSimulationChargeExportRows(CostSimulationRecord record) {
        Map<String, Object> input = parseJsonObjectForExport(record.getInputJson());
        Map<String, Object> result = parseJsonObjectForExport(record.getResultJson());
        Map<String, Object> explain = parseJsonObjectForExport(record.getExplainJson());
        List<Map<String, Object>> feeResults = castMapList(result.get("feeResults"));
        Map<String, Map<String, Object>> matchedFeeMap = buildMatchedFeeExplainMap(castMapList(explain.get("matchedFees")));
        String bizNo = resolveBizNo(input, 1);
        String objectCode = firstNonBlank(resolveString(input, "objectCode", "object_code"), bizNo);
        String objectName = resolveString(input, "objectName", "object_name", "name");

        List<CostSimulationChargeExportRow> rows = new ArrayList<>();
        for (Map<String, Object> fee : feeResults) {
            Map<String, Object> feeExplain = matchedFeeMap.get(buildFeeExplainKey(fee));
            Map<String, Object> pricing = feeExplain == null ? Collections.emptyMap() : safeCastMap(feeExplain.get("pricing"));
            CostSimulationChargeExportRow row = new CostSimulationChargeExportRow();
            row.setSimulationNo(record.getSimulationNo());
            row.setSceneCode(record.getSceneCode());
            row.setSceneName(record.getSceneName());
            row.setVersionNo(record.getVersionNo());
            row.setBillMonth(record.getBillMonth());
            row.setBizNo(bizNo);
            row.setObjectCode(objectCode);
            row.setObjectName(objectName);
            row.setFeeCode(stringValue(fee.get("feeCode")));
            row.setFeeName(stringValue(fee.get("feeName")));
            row.setUnitCode(firstNonBlank(stringValue(fee.get("unitCode")), stringValue(pricing.get("unitCode"))));
            row.setRuleCode(stringValue(fee.get("ruleCode")));
            row.setRuleName(stringValue(fee.get("ruleName")));
            row.setQuantityValue(toBigDecimal(firstNotNull(fee.get("quantityValue"), pricing.get("quantityValue"))));
            row.setUnitPrice(toBigDecimal(firstNotNull(fee.get("unitPrice"), pricing.get("unitPrice"))));
            row.setAmountValue(toBigDecimal(firstNotNull(fee.get("amountValue"), pricing.get("amountValue"))));
            row.setPricingSource(resolvePricingSourceForExport(stringValue(pricing.get("pricingSource"))));
            row.setQuantitySource(resolveQuantitySourceForExport(pricing, row.getQuantityValue()));
            row.setUnitPriceSource(resolveUnitPriceSourceForExport(pricing, row.getUnitPrice()));
            row.setPricingSummary(resolvePricingSummaryForExport(pricing, row));
            rows.add(row);
        }
        return rows;
    }

    private CostSimulationBatchExportRow buildSimulationBatchExportRow(CostSimulationRecord record) {
        Map<String, Object> input = parseJsonObjectForExport(record.getInputJson());
        Map<String, Object> result = parseJsonObjectForExport(record.getResultJson());
        List<Map<String, Object>> feeResults = castMapList(result.get("feeResults"));
        CostSimulationBatchExportRow row = new CostSimulationBatchExportRow();
        row.setBizNo(resolveBizNo(input, 1));
        row.setSimulationNo(record.getSimulationNo());
        row.setSceneCode(record.getSceneCode());
        row.setSceneName(record.getSceneName());
        row.setVersionNo(record.getVersionNo());
        row.setBillMonth(record.getBillMonth());
        row.setStatus(record.getStatus());
        row.setAmountTotal(toBigDecimal(result.get("amountTotal")));
        row.setChargeLineCount(feeResults.size());
        row.setErrorMessage(record.getErrorMessage());
        row.setSimulationTime(record.getCreateTime());
        return row;
    }

    private List<Long> parseSimulationExportIds(String simulationIds) {
        if (StringUtils.isEmpty(StringUtils.trim(simulationIds))) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (String item : simulationIds.split(",")) {
            String normalized = StringUtils.trim(item);
            if (StringUtils.isEmpty(normalized)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(normalized));
            } catch (NumberFormatException e) {
                throw new ServiceException("试算记录ID格式不正确：" + normalized);
            }
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private Map<String, Map<String, Object>> buildMatchedFeeExplainMap(List<Map<String, Object>> matchedFees) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> fee : matchedFees) {
            result.put(buildFeeExplainKey(fee), fee);
        }
        return result;
    }

    private String buildFeeExplainKey(Map<String, Object> fee) {
        return stringValue(fee.get("feeCode")) + "|" + stringValue(fee.get("ruleCode"));
    }

    private String resolveQuantitySourceForExport(Map<String, Object> pricing, BigDecimal quantityValue) {
        String quantityVariableCode = stringValue(pricing.get("quantityVariableCode"));
        if (StringUtils.isNotEmpty(quantityVariableCode)) {
            return "取变量 " + quantityVariableCode;
        }
        if ("FIXED_AMOUNT".equals(stringValue(pricing.get("pricingSource")))) {
            return "固定金额不取数量";
        }
        if (quantityValue != null) {
            return "数量 " + quantityValue.stripTrailingZeros().toPlainString();
        }
        return "未返回";
    }

    private String resolveUnitPriceSourceForExport(Map<String, Object> pricing, BigDecimal unitPrice) {
        String pricingSource = stringValue(pricing.get("pricingSource"));
        if ("FORMULA".equals(pricingSource)) {
            String formulaCode = stringValue(pricing.get("formulaCode"));
            return StringUtils.isNotEmpty(formulaCode) ? "公式 " + formulaCode : "公式取价";
        }
        if ("TIER_RATE".equals(pricingSource)) {
            String tierRange = stringValue(pricing.get("tierRange"));
            return StringUtils.isNotEmpty(tierRange) ? "阶梯 " + tierRange : "阶梯费率";
        }
        if (pricing.get("matchedGroupNo") != null) {
            return "组合组 " + pricing.get("matchedGroupNo");
        }
        if (unitPrice != null) {
            return "单价 " + unitPrice.stripTrailingZeros().toPlainString();
        }
        return "未返回";
    }

    private String resolvePricingSummaryForExport(Map<String, Object> pricing, CostSimulationChargeExportRow row) {
        List<String> parts = new ArrayList<>();
        parts.add(firstNonBlank(row.getPricingSource(), "未返回定价来源"));
        if (StringUtils.isNotEmpty(row.getQuantitySource()) && !"未返回".equals(row.getQuantitySource())) {
            parts.add(row.getQuantitySource());
        }
        if (StringUtils.isNotEmpty(row.getUnitPriceSource()) && !"未返回".equals(row.getUnitPriceSource())) {
            parts.add(row.getUnitPriceSource());
        }
        if (pricing.get("amountValue") != null) {
            parts.add("金额 " + pricing.get("amountValue"));
        }
        return String.join(" / ", parts);
    }

    private String resolvePricingSourceForExport(String pricingSource) {
        if ("FIXED_RATE".equals(pricingSource)) {
            return "固定费率";
        }
        if ("FIXED_AMOUNT".equals(pricingSource)) {
            return "固定金额";
        }
        if ("FORMULA".equals(pricingSource)) {
            return "公式取价";
        }
        if ("TIER_RATE".equals(pricingSource)) {
            return "阶梯费率";
        }
        if ("GROUPED".equals(pricingSource)) {
            return "分组费率";
        }
        return StringUtils.isEmpty(pricingSource) ? "未返回" : pricingSource;
    }

    private Object firstNotNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private Map<String, Object> parseJsonObjectForExport(String json) {
        return safeCastMap(parseJsonToObject(json));
    }

    private Map<String, Object> safeCastMap(Object value) {
        if (value instanceof Map) {
            return castMap(value);
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add(castMap(item));
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> selectSimulationDetail(Long simulationId) {
        CostSimulationRecord record = simulationRecordMapper.selectById(simulationId);
        if (record == null) {
            throw new ServiceException("试算记录不存在，请刷新后重试");
        }
        enrichSimulationRecords(Collections.singletonList(record));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("input", parseJsonToObject(record.getInputJson()));
        result.put("variables", parseJsonToObject(record.getVariableJson()));
        result.put("explain", parseJsonToObject(record.getExplainJson()));
        result.put("result", parseJsonToObject(record.getResultJson()));
        return result;
    }

    @Override
    public List<CostSimulationChargeExportRow> selectSimulationChargeExportRows(Long simulationId) {
        if (simulationId == null) {
            throw new ServiceException("请选择要导出的试算记录");
        }
        CostSimulationRecord record = simulationRecordMapper.selectById(simulationId);
        if (record == null) {
            throw new ServiceException("试算记录不存在，请刷新后重试");
        }
        enrichSimulationRecords(Collections.singletonList(record));
        return buildSimulationChargeExportRows(record);
    }

    @Override
    public List<CostSimulationBatchExportRow> selectSimulationBatchExportRows(String simulationIds, Boolean failedOnly) {
        List<Long> ids = parseSimulationExportIds(simulationIds);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<CostSimulationRecord> records = simulationRecordMapper.selectBatchIds(ids);
        enrichSimulationRecords(records);
        Map<Long, CostSimulationRecord> recordMap = records.stream()
                .filter(item -> item.getSimulationId() != null)
                .collect(Collectors.toMap(CostSimulationRecord::getSimulationId, item -> item, (left, right) -> left));
        List<CostSimulationBatchExportRow> rows = new ArrayList<>();
        for (Long id : ids) {
            CostSimulationRecord record = recordMap.get(id);
            if (record == null) {
                continue;
            }
            if (Boolean.TRUE.equals(failedOnly)
                    && !SIMULATION_STATUS_FAILED.equals(record.getStatus())
                    && StringUtils.isEmpty(record.getErrorMessage())) {
                continue;
            }
            rows.add(buildSimulationBatchExportRow(record));
        }
        return rows;
    }

    @Override
    public Map<String, Object> selectTaskStats(CostCalcTask query) {
        List<CostCalcTask> tasks = selectTaskListInternal(query);
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("taskCount", tasks.size());
        stats.put("runningCount", tasks.stream().filter(item -> TASK_STATUS_RUNNING.equals(item.getTaskStatus())).count());
        stats.put("successCount", tasks.stream().filter(item -> TASK_STATUS_SUCCESS.equals(item.getTaskStatus())).count());
        stats.put("failedCount", tasks.stream().filter(item -> TASK_STATUS_FAILED.equals(item.getTaskStatus()) || TASK_STATUS_PART_SUCCESS.equals(item.getTaskStatus())).count());
        return stats;
    }

    @Override
    public Map<String, Object> selectTaskOverview(CostCalcTask query) {
        List<CostCalcTask> tasks = selectTaskListInternal(query);
        enrichTasks(tasks);
        List<CostCalcTaskPartition> partitions = selectTaskPartitions(tasks);
        LinkedHashMap<String, Object> overview = new LinkedHashMap<>();
        overview.put("recentTaskTrend", buildTaskTrend(tasks, 7));
        overview.put("recentPartitionTrend", buildPartitionTrend(partitions, 7));
        overview.put("topRiskTasks", buildTopRiskTasks(tasks, partitions, 5));
        overview.put("taskStatusDistribution", buildTaskStatusDistribution(tasks));
        overview.put("inputSourceDistribution", buildInputSourceDistribution(tasks));
        overview.put("ownerSummary", buildPartitionOwnerSummary(partitions));
        overview.put("partitionOwnerDistribution", buildPartitionOwnerDistribution(partitions, 5));
        overview.put("topOwnerRiskTasks", buildTopOwnerRiskTasks(tasks, partitions, 5));
        return overview;
    }

    @Override
    public List<CostCalcTask> selectTaskList(CostCalcTask query) {
        List<CostCalcTask> tasks = selectTaskListInternal(query);
        enrichTasks(tasks);
        return tasks;
    }

    @Override
    public Map<String, Object> precheckTask(CostCalcTaskSubmitBo bo) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> blockingItems = new ArrayList<>();
        List<Map<String, Object>> warningItems = new ArrayList<>();
        RuntimeSnapshot snapshot = null;
        List<Map<String, Object>> inputs = Collections.emptyList();

        if (bo == null) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "REQUEST_EMPTY", "请求为空",
                    "请先填写正式核算任务参数。"));
        } else {
            if (bo.getSceneId() == null) {
                blockingItems.add(buildTaskPrecheckItem("BLOCKING", "SCENE_EMPTY", "缺少运行场景",
                        "请选择正式核算要执行的成本场景。"));
            }
            if (StringUtils.isEmpty(bo.getTaskType())) {
                blockingItems.add(buildTaskPrecheckItem("BLOCKING", "TASK_TYPE_EMPTY", "缺少任务类型",
                        "请选择单笔正式核算或批量正式核算。"));
            }
            if (StringUtils.isEmpty(bo.getBillMonth())) {
                blockingItems.add(buildTaskPrecheckItem("BLOCKING", "BILL_MONTH_EMPTY", "缺少账期",
                        "请选择本次正式核算的账期。"));
            } else {
                try {
                    validateBillMonth(bo.getBillMonth());
                } catch (ServiceException ex) {
                    blockingItems.add(buildTaskPrecheckItem("BLOCKING", "BILL_MONTH_INVALID", "账期格式错误",
                            ex.getMessage()));
                }
            }

            if (bo.getSceneId() != null) {
                try {
                    snapshot = loadRuntimeSnapshot(bo.getSceneId(), bo.getVersionId(), true);
                    result.put("sceneId", snapshot.sceneId);
                    result.put("sceneCode", snapshot.sceneCode);
                    result.put("sceneName", snapshot.sceneName);
                    result.put("versionId", snapshot.versionId);
                    result.put("versionNo", snapshot.versionNo);
                    result.put("snapshotSource", snapshot.snapshotSource);
                    if (snapshot.fees.isEmpty()) {
                        blockingItems.add(buildTaskPrecheckItem("BLOCKING", "FEE_EMPTY", "发布版本缺少费用",
                                "当前执行版本没有可运行费用，请先发布包含费用、规则和变量的版本。"));
                    }
                } catch (ServiceException ex) {
                    blockingItems.add(buildTaskPrecheckItem("BLOCKING", "VERSION_INVALID", "执行版本不可用",
                            ex.getMessage()));
                }
            }

            if (snapshot != null && StringUtils.isNotEmpty(bo.getBillMonth())) {
                appendBillPeriodPrecheck(snapshot.sceneId, snapshot.versionId, bo.getBillMonth(), blockingItems, warningItems);
            }
            if (INPUT_SOURCE_BATCH.equals(resolveInputSourceType(bo))) {
                appendInputBatchPrecheck(bo, snapshot, blockingItems, warningItems);
            } else if (StringUtils.isEmpty(bo.getInputJson())) {
                blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_JSON_EMPTY", "缺少输入 JSON",
                        "JSON 直传模式下必须填写正式核算输入。"));
            }
            if (canParseTaskInputForPrecheck(bo, blockingItems)) {
                try {
                    inputs = parseTaskInput(bo);
                    if (inputs.isEmpty()) {
                        blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_EMPTY", "输入数据为空",
                                "正式核算至少需要 1 条输入数据。"));
                    }
                } catch (ServiceException ex) {
                    blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_INVALID", "输入数据不可用",
                            ex.getMessage()));
                }
            }
            if (snapshot != null && !inputs.isEmpty()) {
                appendVariableCompletenessPrecheck(snapshot, inputs, blockingItems);
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(blockingItems);
        items.addAll(warningItems);
        result.put("passed", blockingItems.isEmpty());
        result.put("blockingCount", blockingItems.size());
        result.put("warningCount", warningItems.size());
        result.put("blockingItems", blockingItems);
        result.put("warningItems", warningItems);
        result.put("items", items);
        result.put("inputCount", inputs.size());
        result.put("inputSourceType", bo == null ? "" : resolveInputSourceType(bo));
        result.put("billMonth", bo == null ? "" : bo.getBillMonth());
        result.put("message", blockingItems.isEmpty()
                ? (warningItems.isEmpty() ? "任务创建前预检通过" : "任务创建前预检通过，但存在提醒项")
                : "任务创建前预检存在阻断项");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitTask(CostCalcTaskSubmitBo bo) {
        return distributedLockSupport.executeTaskSubmitLock(bo.getSceneId(), bo.getVersionId(), bo.getBillMonth(),
                bo.getTaskType(), bo.getRequestNo(), bo.getSourceBatchNo(),
                "当前账期的核算任务正在提交处理中，请稍后重试", () ->
                {
                    RuntimeSnapshot snapshot = loadRuntimeSnapshot(bo.getSceneId(), bo.getVersionId(), true);
                    validateInputBatchMatchesSnapshot(bo, snapshot);
                    List<Map<String, Object>> inputs = parseTaskInput(bo);
                    validateBillMonth(bo.getBillMonth());
                    CostBillPeriod period = ensureBillPeriodAvailable(snapshot.sceneId, bo.getBillMonth(), snapshot.versionId);
                    String requestNo = firstNonBlank(bo.getRequestNo(), "");
                    if (StringUtils.isNotEmpty(requestNo)) {
                        CostCalcTask existing = selectExistingTaskByRequestNo(snapshot.sceneId, snapshot.versionId,
                                bo.getBillMonth(), requestNo);
                        if (existing != null) {
                            return selectTaskDetail(existing.getTaskId(), 1, 10);
                        }
                    }

                    Date now = DateUtils.getNowDate();
                    String operator = resolveOperator();
                    CostCalcTask task = new CostCalcTask();
                    task.setTaskNo(buildRunNo("TASK"));
                    task.setSceneId(snapshot.sceneId);
                    task.setVersionId(snapshot.versionId);
                    task.setTaskType(bo.getTaskType());
                    task.setBillMonth(bo.getBillMonth());
                    task.setSourceCount(inputs.size());
                    task.setSuccessCount(0);
                    task.setFailCount(0);
                    task.setTaskStatus(TASK_STATUS_INIT);
                    task.setProgressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    task.setRequestNo(requestNo);
                    task.setExecuteNode(resolveExecuteNode());
                    task.setInputSourceType(resolveInputSourceType(bo));
                    task.setSourceBatchNo(firstNonBlank(bo.getSourceBatchNo(), ""));
                    task.setErrorMessage("");
                    task.setRemark(bo.getRemark());
                    task.setCreateBy(operator);
                    task.setCreateTime(now);
                    task.setUpdateBy(operator);
                    task.setUpdateTime(now);
                    try {
                        calcTaskMapper.insert(task);
                    } catch (DuplicateKeyException ex) {
                        if (StringUtils.isNotEmpty(requestNo)) {
                            CostCalcTask existing = selectExistingTaskByRequestNo(snapshot.sceneId, snapshot.versionId,
                                    bo.getBillMonth(), requestNo);
                            if (existing != null) {
                                return selectTaskDetail(existing.getTaskId(), 1, 10);
                            }
                        }
                        throw ex;
                    }
                    markPeriodInProgress(period, task);

                    List<CostCalcTaskDetail> details = buildTaskDetails(task, inputs);
                    if (!details.isEmpty()) {
                        insertTaskDetailsInChunks(details);
                        insertTaskPartitionsInChunks(buildTaskPartitions(task, details));
                        markInputBatchSubmitted(task.getSourceBatchNo(), operator);
                    }
                    auditService.recordAudit(snapshot.sceneId, "CALC_TASK", task.getTaskNo(),
                            "SUBMIT", "提交正式核算任务", null, task, task.getRequestNo());
                    dispatchTaskAfterCommit(task.getTaskId());
                    return selectTaskDetail(task.getTaskId(), 1, 10);
                });
    }

    private CostCalcTask selectExistingTaskByRequestNo(Long sceneId, Long versionId, String billMonth, String requestNo) {
        if (sceneId == null || versionId == null || StringUtils.isEmpty(billMonth) || StringUtils.isEmpty(requestNo)) {
            return null;
        }
        return calcTaskMapper.selectOne(Wrappers.<CostCalcTask>lambdaQuery()
                .eq(CostCalcTask::getSceneId, sceneId)
                .eq(CostCalcTask::getVersionId, versionId)
                .eq(CostCalcTask::getBillMonth, billMonth)
                .eq(CostCalcTask::getRequestNo, requestNo)
                .last("limit 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createInputBatch(CostCalcInputBatchCreateBo bo) {
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(bo.getSceneId(), bo.getVersionId(), true);
        validateBillMonth(bo.getBillMonth());
        Date now = DateUtils.getNowDate();
        String operator = resolveOperator();

        CostCalcInputBatch batch = new CostCalcInputBatch();
        batch.setBatchNo(buildRunNo("INPUT"));
        batch.setSceneId(snapshot.sceneId);
        batch.setVersionId(snapshot.versionId);
        batch.setBillMonth(bo.getBillMonth());
        batch.setSourceType("JSON_IMPORT");
        batch.setBatchStatus(INPUT_BATCH_STATUS_READY);
        batch.setTotalCount(0);
        batch.setValidCount(0);
        batch.setErrorCount(0);
        batch.setRemark(bo.getRemark());
        batch.setErrorMessage("");
        batch.setCreateBy(operator);
        batch.setCreateTime(now);
        batch.setUpdateBy(operator);
        batch.setUpdateTime(now);
        calcInputBatchMapper.insert(batch);
        int totalCount = insertInputBatchItems(batch, bo.getInputJson());
        batch.setTotalCount(totalCount);
        batch.setValidCount(totalCount);
        batch.setUpdateBy(operator);
        batch.setUpdateTime(DateUtils.getNowDate());
        calcInputBatchMapper.updateById(batch);
        return selectInputBatchDetail(batch.getBatchId(), 1, 10);
    }

    @Override
    public List<CostCalcInputBatch> selectInputBatchList(CostCalcInputBatch query) {
        List<CostCalcInputBatch> batches = calcInputBatchMapper.selectList(Wrappers.<CostCalcInputBatch>lambdaQuery()
                .eq(query.getSceneId() != null, CostCalcInputBatch::getSceneId, query.getSceneId())
                .eq(query.getVersionId() != null, CostCalcInputBatch::getVersionId, query.getVersionId())
                .eq(StringUtils.isNotEmpty(query.getBillMonth()), CostCalcInputBatch::getBillMonth, query.getBillMonth())
                .eq(StringUtils.isNotEmpty(query.getBatchStatus()), CostCalcInputBatch::getBatchStatus, query.getBatchStatus())
                .eq(StringUtils.isNotEmpty(query.getSourceType()), CostCalcInputBatch::getSourceType, query.getSourceType())
                .like(StringUtils.isNotEmpty(query.getBatchNo()), CostCalcInputBatch::getBatchNo, query.getBatchNo())
                .orderByDesc(CostCalcInputBatch::getBatchId));
        enrichInputBatches(batches);
        return batches;
    }

    @Override
    public Map<String, Object> selectInputBatchDetail(Long batchId, Integer pageNum, Integer pageSize) {
        CostCalcInputBatch batch = calcInputBatchMapper.selectById(batchId);
        enrichInputBatches(Collections.singletonList(batch));
        if (batch == null) {
            throw new ServiceException("输入批次不存在，请刷新后重试");
        }
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 200);
        int itemTotal = Math.toIntExact(calcInputBatchItemMapper.selectCount(Wrappers.<CostCalcInputBatchItem>lambdaQuery()
                .eq(CostCalcInputBatchItem::getBatchId, batchId)));
        int offset = Math.max((normalizedPageNum - 1) * normalizedPageSize, 0);
        List<CostCalcInputBatchItem> items = calcInputBatchItemMapper.selectList(Wrappers.<CostCalcInputBatchItem>lambdaQuery()
                .eq(CostCalcInputBatchItem::getBatchId, batchId)
                .orderByAsc(CostCalcInputBatchItem::getItemNo)
                .orderByAsc(CostCalcInputBatchItem::getItemId)
                .last("limit " + offset + "," + normalizedPageSize));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("items", items);
        result.put("itemTotal", itemTotal);
        result.put("pageNum", normalizedPageNum);
        result.put("pageSize", normalizedPageSize);
        result.put("loadingGuide", buildInputBatchLoadingGuide(itemTotal));
        result.put("checkpoint", parseOptionalJsonMap(batch.getCheckpointJson()));
        result.put("resumable", INPUT_BATCH_STATUS_PARTIAL.equals(batch.getBatchStatus()));
        return result;
    }

    @Override
    public Map<String, Object> selectTaskDetail(Long taskId, Integer pageNum, Integer pageSize) {
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("核算任务不存在，请刷新后重试");
        }
        enrichTasks(Collections.singletonList(task));
        List<CostCalcTaskPartition> partitions = calcTaskPartitionMapper.selectList(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .orderByAsc(CostCalcTaskPartition::getPartitionNo)
                .orderByAsc(CostCalcTaskPartition::getPartitionId));
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        long detailTotal = NumberUtils.toLong(String.valueOf(task.getSourceCount()), 0L);
        int detailOffset = (safePageNum - 1) * safePageSize;
        List<CostCalcTaskDetail> details = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .select(CostCalcTaskDetail::getDetailId,
                        CostCalcTaskDetail::getTaskId,
                        CostCalcTaskDetail::getBizNo,
                        CostCalcTaskDetail::getPartitionNo,
                        CostCalcTaskDetail::getDetailStatus,
                        CostCalcTaskDetail::getRetryCount,
                        CostCalcTaskDetail::getResultSummary,
                        CostCalcTaskDetail::getErrorMessage,
                        CostCalcTaskDetail::getCreateTime,
                        CostCalcTaskDetail::getUpdateTime)
                .eq(CostCalcTaskDetail::getTaskId, taskId)
                .orderByAsc(CostCalcTaskDetail::getPartitionNo)
                .orderByAsc(CostCalcTaskDetail::getDetailId)
                .last("limit " + detailOffset + "," + safePageSize));
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("sourceCount", task.getSourceCount());
        summary.put("successCount", task.getSuccessCount());
        summary.put("failCount", task.getFailCount());
        summary.put("progressPercent", task.getProgressPercent());
        summary.put("detailCount", detailTotal);
        summary.put("partitionCount", partitions.size());
        summary.put("failedPartitionCount", partitions.stream().filter(item -> NumberUtils.toInt(String.valueOf(item.getFailCount()), 0) > 0).count());
        summary.putAll(buildPartitionOwnerSummary(partitions));
        summary.put("retryableCount", NumberUtils.toLong(String.valueOf(task.getFailCount()), 0L));
        summary.put("topErrors", calcTaskDetailMapper.selectTopErrors(taskId, 5).stream()
                .map(item ->
                {
                    LinkedHashMap<String, Object> error = new LinkedHashMap<>();
                    error.put("message", limitLength(String.valueOf(item.get("message")), 120));
                    error.put("count", item.get("count"));
                    return error;
                })
                .collect(Collectors.toList()));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("summary", summary);
        result.put("partitions", partitions);
        result.put("details", details);
        LinkedHashMap<String, Object> detailPage = new LinkedHashMap<>();
        detailPage.put("pageNum", safePageNum);
        detailPage.put("pageSize", safePageSize);
        detailPage.put("total", detailTotal);
        detailPage.put("hasMore", (long) safePageNum * safePageSize < detailTotal);
        result.put("detailPage", detailPage);
        if (StringUtils.isNotEmpty(task.getSourceBatchNo())) {
            CostCalcInputBatch inputBatch = calcInputBatchMapper.selectOne(Wrappers.<CostCalcInputBatch>lambdaQuery()
                    .eq(CostCalcInputBatch::getBatchNo, task.getSourceBatchNo())
                    .last("limit 1"));
            if (inputBatch != null) {
                LinkedHashMap<String, Object> inputBatchDetail = new LinkedHashMap<>();
                inputBatchDetail.putAll(selectInputBatchDetail(inputBatch.getBatchId(), 1, 10));
                result.put("inputBatch", inputBatchDetail);
            }
        }
        return result;
    }

    @Override
    public int retryTaskDetail(Long detailId) {
        CostCalcTaskDetail detail = calcTaskDetailMapper.selectById(detailId);
        if (detail == null) {
            throw new ServiceException("任务明细不存在，请刷新后重试");
        }
        CostCalcTask task = calcTaskMapper.selectById(detail.getTaskId());
        if (task == null) {
            throw new ServiceException("所属核算任务不存在，请刷新后重试");
        }
        int nextRetryCount = (detail.getRetryCount() == null ? 0 : detail.getRetryCount()) + 1;
        calcTaskDetailMapper.update(null, Wrappers.<CostCalcTaskDetail>lambdaUpdate()
                .eq(CostCalcTaskDetail::getDetailId, detailId)
                .set(CostCalcTaskDetail::getDetailStatus, DETAIL_STATUS_INIT)
                .set(CostCalcTaskDetail::getRetryCount, nextRetryCount)
                .set(CostCalcTaskDetail::getErrorMessage, ""));
        auditService.recordAudit(task.getSceneId(), "CALC_TASK_DETAIL", detail.getBizNo(),
                "RETRY", "重试正式核算明细", detail, calcTaskDetailMapper.selectById(detailId), task.getRequestNo());
        if (nextRetryCount >= 3) {
            createTaskAlarm(task, detail, "TASK_DETAIL_RETRY_LIMIT", "WARN",
                    "任务明细重试次数达到阈值", "业务单号 " + detail.getBizNo() + " 的重试次数已达到 " + nextRetryCount + " 次");
        }
        dispatchTaskAfterCommit(task.getTaskId());
        return 1;
    }

    @Override
    public int retryTaskPartition(Long partitionId) {
        CostCalcTaskPartition partition = calcTaskPartitionMapper.selectById(partitionId);
        if (partition == null) {
            throw new ServiceException("任务分片不存在，请刷新后重试");
        }
        CostCalcTask task = calcTaskMapper.selectById(partition.getTaskId());
        if (task == null) {
            throw new ServiceException("所属核算任务不存在，请刷新后重试");
        }
        List<CostCalcTaskDetail> failedDetails = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .eq(CostCalcTaskDetail::getTaskId, partition.getTaskId())
                .eq(CostCalcTaskDetail::getPartitionNo, partition.getPartitionNo())
                .eq(CostCalcTaskDetail::getDetailStatus, DETAIL_STATUS_FAILED)
                .orderByAsc(CostCalcTaskDetail::getDetailId));
        if (failedDetails.isEmpty()) {
            return 0;
        }
        for (CostCalcTaskDetail detail : failedDetails) {
            int nextRetryCount = (detail.getRetryCount() == null ? 0 : detail.getRetryCount()) + 1;
            calcTaskDetailMapper.update(null, Wrappers.<CostCalcTaskDetail>lambdaUpdate()
                    .eq(CostCalcTaskDetail::getDetailId, detail.getDetailId())
                    .set(CostCalcTaskDetail::getDetailStatus, DETAIL_STATUS_INIT)
                    .set(CostCalcTaskDetail::getRetryCount, nextRetryCount)
                    .set(CostCalcTaskDetail::getErrorMessage, "")
                    .set(CostCalcTaskDetail::getResultSummary, ""));
        }
        calcTaskPartitionMapper.update(null, Wrappers.<CostCalcTaskPartition>lambdaUpdate()
                .eq(CostCalcTaskPartition::getPartitionId, partitionId)
                .set(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT)
                .set(CostCalcTaskPartition::getProcessedCount, 0)
                .set(CostCalcTaskPartition::getSuccessCount, 0)
                .set(CostCalcTaskPartition::getFailCount, 0)
                .set(CostCalcTaskPartition::getExecuteNode, null)
                .set(CostCalcTaskPartition::getClaimTime, null)
                .set(CostCalcTaskPartition::getStartedTime, null)
                .set(CostCalcTaskPartition::getFinishedTime, null)
                .set(CostCalcTaskPartition::getDurationMs, 0)
                .set(CostCalcTaskPartition::getLastError, ""));
        auditService.recordAudit(task.getSceneId(), "CALC_TASK_PARTITION", task.getTaskNo() + "#" + partition.getPartitionNo(),
                "RETRY", "重试正式核算分片", partition, calcTaskPartitionMapper.selectById(partitionId), task.getRequestNo());
        dispatchTaskAfterCommit(task.getTaskId());
        return 1;
    }

    @Override
    public int cancelTask(Long taskId) {
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("核算任务不存在，请刷新后重试");
        }
        if (!TASK_STATUS_INIT.equals(task.getTaskStatus()) && !TASK_STATUS_RUNNING.equals(task.getTaskStatus())) {
            return 0;
        }
        int rows = calcTaskMapper.update(null, Wrappers.<CostCalcTask>lambdaUpdate()
                .eq(CostCalcTask::getTaskId, taskId)
                .set(CostCalcTask::getTaskStatus, TASK_STATUS_CANCELLED)
                .set(CostCalcTask::getErrorMessage, "任务已手工终止")
                .set(CostCalcTask::getUpdateBy, resolveOperator())
                .set(CostCalcTask::getUpdateTime, DateUtils.getNowDate()));
        calcTaskPartitionMapper.update(null, Wrappers.<CostCalcTaskPartition>lambdaUpdate()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .in(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT, TASK_STATUS_RUNNING)
                .set(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_CANCELLED)
                .set(CostCalcTaskPartition::getExecuteNode, null)
                .set(CostCalcTaskPartition::getClaimTime, null)
                .set(CostCalcTaskPartition::getLastError, "任务已手工终止")
                .set(CostCalcTaskPartition::getFinishedTime, DateUtils.getNowDate())
                .set(CostCalcTaskPartition::getUpdateTime, DateUtils.getNowDate()));
        refreshBillPeriod(task.getSceneId(), task.getBillMonth(), task);
        syncRecalcByTask(task, TASK_STATUS_CANCELLED);
        auditService.recordAudit(task.getSceneId(), "CALC_TASK", task.getTaskNo(),
                "CANCEL", "取消正式核算任务", task, calcTaskMapper.selectById(taskId), task.getRequestNo());
        return rows;
    }

    @Override
    public Map<String, Object> selectResultStats(CostResultLedger query) {
        validateResultQueryScope(query);
        List<Long> requestTaskIds = resolveResultRequestTaskIds(query.getRequestNo());
        if (StringUtils.isNotEmpty(query.getRequestNo()) && requestTaskIds.isEmpty()) {
            LinkedHashMap<String, Object> empty = new LinkedHashMap<>();
            empty.put("resultCount", 0L);
            empty.put("taskCount", 0L);
            empty.put("traceCount", 0L);
            empty.put("abnormalCount", 0L);
            empty.put("amountTotal", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.put("feeDistribution", Collections.emptyList());
            empty.put("objectDistribution", Collections.emptyList());
            return empty;
        }
        Map<String, Object> rawStats = resultLedgerMapper.selectStats(query, requestTaskIds);
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("resultCount", toLong(rawStats.get("resultCount")));
        stats.put("taskCount", toLong(rawStats.get("taskCount")));
        stats.put("traceCount", toLong(rawStats.get("traceCount")));
        stats.put("abnormalCount", toLong(rawStats.get("abnormalCount")));
        BigDecimal amountTotal = toBigDecimal(rawStats.get("amountTotal"));
        stats.put("amountTotal", (amountTotal == null ? BigDecimal.ZERO : amountTotal).setScale(2, RoundingMode.HALF_UP));
        stats.put("feeDistribution", normalizeAmountRows(resultLedgerMapper.selectFeeDistribution(query, requestTaskIds), "amountTotal"));
        stats.put("objectDistribution", normalizeAmountRows(resultLedgerMapper.selectObjectDistribution(query, requestTaskIds), "amountTotal"));
        return stats;
    }

    @Override
    public Map<String, Object> selectResultCompare(CostResultCompareBo query) {
        if (query == null) {
            throw new ServiceException("请补充对比条件");
        }
        String dimension = StringUtils.isNotEmpty(query.getCompareDimension())
                ? query.getCompareDimension().toUpperCase(Locale.ROOT) : "FEE";
        CostResultCompareSource left = buildResultCompareSource(query, true);
        CostResultCompareSource right = buildResultCompareSource(query, false);
        List<Map<String, Object>> rows;
        switch (dimension) {
            case "OBJECT":
                rows = buildResultCompareRowsByObject(left, right);
                break;
            case "RULE":
                rows = buildResultCompareRowsByRule(left, right);
                break;
            default:
                rows = buildResultCompareRows(left, right);
                break;
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("compareDimension", dimension);
        result.put("leftSource", buildResultCompareSourceMap(left));
        result.put("rightSource", buildResultCompareSourceMap(right));
        result.put("summary", buildResultCompareSummary(left, right, rows));
        result.put("rows", rows);
        return result;
    }

    @Override
    public List<CostResultLedger> selectResultList(CostResultLedger query) {
        validateResultQueryScope(query);
        List<CostResultLedger> results = selectResultListInternal(query);
        enrichResults(results);
        return results;
    }

    @Override
    public List<CostResultLedger> selectResultExportList(CostResultLedger query) {
        validateResultQueryScope(query);
        CostResultLedger exportQuery = query == null ? new CostResultLedger() : query;
        List<CostResultLedger> results = selectResultListInternal(exportQuery, RESULT_EXPORT_MAX_ROWS + 1);
        if (results.size() > RESULT_EXPORT_MAX_ROWS) {
            throw new ServiceException("结果导出最多支持 " + RESULT_EXPORT_MAX_ROWS + " 条，请缩小筛选范围后重试");
        }
        enrichResults(results);
        return results;
    }

    @Override
    public Map<String, Object> selectResultDetail(Long resultId) {
        CostResultLedger ledger = resultLedgerMapper.selectById(resultId);
        if (ledger == null) {
            throw new ServiceException("结果台账不存在，请刷新后重试");
        }
        enrichResults(Collections.singletonList(ledger));
        CostResultTrace trace = ledger.getTraceId() == null ? null : resultTraceMapper.selectById(ledger.getTraceId());
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("ledger", ledger);
        result.put("trace", trace == null ? null : buildTraceView(trace));
        return result;
    }

    @Override
    public Map<String, Object> selectTraceDetail(Long traceId) {
        CostResultTrace trace = resultTraceMapper.selectById(traceId);
        if (trace == null) {
            throw new ServiceException("追溯记录不存在，请刷新后重试");
        }
        return buildTraceView(trace);
    }

    @Override
    public List<Map<String, Object>> selectVersionOptions(Long sceneId) {
        List<CostPublishVersion> versions = publishVersionMapper.selectList(Wrappers.<CostPublishVersion>lambdaQuery()
                .eq(CostPublishVersion::getSceneId, sceneId)
                .orderByDesc(CostPublishVersion::getPublishedTime)
                .orderByDesc(CostPublishVersion::getVersionId));
        return versions.stream().map(version -> {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("versionId", version.getVersionId());
            item.put("versionNo", version.getVersionNo());
            item.put("versionStatus", version.getVersionStatus());
            item.put("publishedTime", version.getPublishedTime());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> selectRuntimeFeeOptions(Long sceneId, Long versionId, String snapshotMode) {
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(sceneId, versionId, false,
            preferDraftSnapshotWhenVersionMissing(snapshotMode));
        return snapshot.fees.stream().map(fee -> {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("sceneId", snapshot.sceneId);
            item.put("sceneCode", snapshot.sceneCode);
            item.put("sceneName", snapshot.sceneName);
            item.put("versionId", snapshot.versionId);
            item.put("versionNo", snapshot.versionNo);
            item.put("snapshotSource", snapshot.snapshotSource);
            item.put("feeId", fee.feeId);
            item.put("feeCode", fee.feeCode);
            item.put("feeName", fee.feeName);
            item.put("unitCode", fee.unitCode);
            item.put("objectDimension", fee.objectDimension);
            item.put("sortNo", fee.sortNo);
            item.put("ruleCount", snapshot.rulesByFeeCode.getOrDefault(fee.feeCode, Collections.emptyList()).size());
            item.put("executionVariableCount", snapshot.executionVariablesByFeeCode
                .getOrDefault(fee.feeCode, Collections.emptyList()).size());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> buildInputTemplate(Long sceneId, Long versionId, String taskType) {
        String normalizedTaskType = normalizeTemplateTaskType(taskType);
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(sceneId, versionId, false, isSimulationTaskType(normalizedTaskType));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneId", snapshot.sceneId);
        result.put("sceneCode", snapshot.sceneCode);
        result.put("sceneName", snapshot.sceneName);
        result.put("versionId", snapshot.versionId);
        result.put("versionNo", snapshot.versionNo);
        result.put("snapshotSource", snapshot.snapshotSource);
        result.put("taskType", normalizedTaskType);
        result.put("message", buildInputTemplateMessage(snapshot));
        result.put("fields", buildTemplateFieldItems(snapshot));
        result.put("inputJson", buildTemplateInputJson(snapshot.variables, normalizedTaskType, snapshot.defaultObjectDimension));
        return result;
    }

    @Override
    public Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, Long feeId, String feeCode, String taskType) {
        return buildFeeInputTemplate(sceneId, versionId, Collections.emptyList(), feeId, feeCode, taskType, null);
    }

    @Override
    public Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, Long feeId, String feeCode,
                                                     String taskType, String snapshotMode) {
        return buildFeeInputTemplate(sceneId, versionId, Collections.emptyList(), feeId, feeCode, taskType,
            snapshotMode);
    }

    @Override
    public Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, List<Long> feeIds, Long feeId,
                                                     String feeCode, String taskType) {
        return buildFeeInputTemplate(sceneId, versionId, feeIds, feeId, feeCode, taskType, null);
    }

    @Override
    public Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, List<Long> feeIds, Long feeId,
                                                     String feeCode, String taskType, String snapshotMode) {
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(sceneId, versionId, false,
            preferDraftSnapshotWhenVersionMissing(snapshotMode));
        List<RuntimeFee> targetFees = resolveTargetRuntimeFees(snapshot, feeIds, feeId, feeCode);
        boolean allFeeScope = isAllFeeScope(snapshot, targetFees);
        List<RuntimeFee> executionFees = allFeeScope ? snapshot.fees : resolveFeeExecutionChains(snapshot, targetFees);
        List<RuntimeRule> rules = targetFees.stream()
                .flatMap(item -> snapshot.rulesByFeeCode.getOrDefault(item.feeCode, Collections.emptyList()).stream())
                .collect(Collectors.toList());
        List<RuntimeRule> executionRules = executionFees.stream()
                .flatMap(item -> snapshot.rulesByFeeCode.getOrDefault(item.feeCode, Collections.emptyList()).stream())
                .collect(Collectors.toList());
        FeeTemplateContext templateContext = buildFeeTemplateContext(snapshot, executionRules);
        List<RuntimeVariable> inputVariables = templateContext.variables.values().stream()
                .filter(item -> item.includedInTemplate)
                .map(item -> item.variable)
                .sorted(Comparator.comparingInt(item -> item.sortNo == null ? 9999 : item.sortNo))
                .collect(Collectors.toList());
        String normalizedTaskType = normalizeTemplateTaskType(taskType);

        Map<String, Object> feeView = buildTargetFeeView(snapshot, targetFees, allFeeScope);
        String objectDimension = firstNonBlank(stringValue(feeView.get("objectDimension")), snapshot.defaultObjectDimension);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneId", snapshot.sceneId);
        result.put("sceneCode", snapshot.sceneCode);
        result.put("sceneName", snapshot.sceneName);
        result.put("versionId", snapshot.versionId);
        result.put("versionNo", snapshot.versionNo);
        result.put("snapshotSource", snapshot.snapshotSource);
        result.put("taskType", normalizedTaskType);
        result.put("fee", feeView);
        result.put("targetFeeCount", targetFees.size());
        result.put("targetFeeCodes", targetFees.stream().map(item -> item.feeCode).collect(Collectors.toList()));
        result.put("allFeeScope", allFeeScope);
        result.put("ruleCount", rules.size());
        result.put("executionFeeCount", executionFees.size());
        result.put("executionFeeCodes", executionFees.stream().map(item -> item.feeCode).collect(Collectors.toList()));
        Set<String> targetFeeCodes = targetFees.stream().map(item -> item.feeCode).collect(Collectors.toCollection(LinkedHashSet::new));
        result.put("dependentFeeCodes", executionFees.stream()
                .map(item -> item.feeCode)
                .filter(code -> !targetFeeCodes.contains(code))
                .collect(Collectors.toList()));
        result.put("fieldCount", templateContext.variables.size());
        result.put("inputFieldCount", inputVariables.size());
        result.put("message", buildFeeTemplateMessage(snapshot, rules.isEmpty()));
        result.put("fields", buildFeeTemplateFieldItems(templateContext));
        result.put("ruleSummary", templateContext.ruleSummaries);
        result.put("inputJson", buildTemplateInputJson(inputVariables, normalizedTaskType, objectDimension));
        return result;
    }

    @Override
    public Map<String, Object> previewBuiltInput(CostInputBuildPreviewBo bo) {
        return buildInputPreviewResult(bo.getSceneId(), bo.getVersionId(), bo.getFeeIds(), bo.getFeeId(), bo.getFeeCode(),
                bo.getTaskType(), bo.getRawJson(), bo.getMappingJson());
    }

    @Override
    public Map<String, Object> calculateFee(CostFeeCalculateBo bo) {
        RuntimeSnapshot snapshot = loadRuntimeSnapshot(bo.getSceneId(), bo.getVersionId(), false,
            preferDraftSnapshotWhenVersionMissing(bo.getSnapshotMode()));
        List<RuntimeFee> targetFees = resolveTargetRuntimeFees(snapshot, bo.getFeeIds(), bo.getFeeId(), bo.getFeeCode());
        boolean allFeeScope = isAllFeeScope(snapshot, targetFees);
        List<RuntimeFee> executionFees = allFeeScope ? snapshot.fees : resolveFeeExecutionChains(snapshot, targetFees);
        List<Map<String, Object>> inputs = parseInlineCalculationInputs(bo.getInputJson());
        String billMonth = StringUtils.isEmpty(bo.getBillMonth()) ? "" : bo.getBillMonth();
        boolean includeExplain = Boolean.TRUE.equals(bo.getIncludeExplain());
        if (StringUtils.isNotEmpty(billMonth)) {
            validateBillMonth(billMonth);
        }

        List<Map<String, Object>> records = new ArrayList<>();
        int successCount = 0;
        int noMatchCount = 0;
        int failedCount = 0;
        long startedAt = System.currentTimeMillis();
        for (int i = 0; i < inputs.size(); i++) {
            Map<String, Object> input = inputs.get(i);
            long recordStartedAt = System.currentTimeMillis();
            try {
                ExecutionResult executionResult = executeSingle(snapshot, "FEE_CALC", billMonth, input,
                        executionFees, includeExplain);
                long durationMs = System.currentTimeMillis() - recordStartedAt;
                for (RuntimeFee targetFee : targetFees) {
                    records.add(buildFeeCalculationRecord(input, targetFee, executionResult, i + 1, includeExplain, durationMs));
                    FeeExecutionResult feeResult = findFeeExecutionResult(executionResult, targetFee.feeCode);
                    if (feeResult == null) {
                        noMatchCount++;
                    } else {
                        successCount++;
                    }
                }
            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - recordStartedAt;
                for (RuntimeFee targetFee : targetFees) {
                    records.add(buildFeeCalculationFailureRecord(input, targetFee, i + 1, e, durationMs, includeExplain));
                    failedCount++;
                }
            }
        }

        Map<String, Object> feeView = buildTargetFeeView(snapshot, targetFees, allFeeScope);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneId", snapshot.sceneId);
        result.put("sceneCode", snapshot.sceneCode);
        result.put("sceneName", snapshot.sceneName);
        result.put("versionId", snapshot.versionId);
        result.put("versionNo", snapshot.versionNo);
        result.put("snapshotSource", snapshot.snapshotSource);
        result.put("billMonth", billMonth);
        result.put("fee", feeView);
        result.put("targetFeeCount", targetFees.size());
        result.put("targetFeeCodes", targetFees.stream().map(item -> item.feeCode).collect(Collectors.toList()));
        result.put("allFeeScope", allFeeScope);
        result.put("executionFeeCount", executionFees.size());
        result.put("executionFeeCodes", executionFees.stream().map(item -> item.feeCode).collect(Collectors.toList()));
        Set<String> targetFeeCodes = targetFees.stream().map(item -> item.feeCode).collect(Collectors.toCollection(LinkedHashSet::new));
        result.put("dependentFeeCodes", executionFees.stream()
                .map(item -> item.feeCode)
                .filter(code -> !targetFeeCodes.contains(code))
                .collect(Collectors.toList()));
        result.put("includeExplain", includeExplain);
        result.put("inputCount", inputs.size());
        result.put("recordCount", records.size());
        result.put("successCount", successCount);
        result.put("noMatchCount", noMatchCount);
        result.put("failedCount", failedCount);
        result.put("durationMs", System.currentTimeMillis() - startedAt);
        result.put("records", records);
        return result;
    }

    /**
     * 任务异步执行主链路。
     * <p>当前阶段先使用线程池异步执行，后续再在此基础上增强分片并发、Redis 锁与分布式协调。</p>
     */
    private void runTaskAsync(Long taskId) {
        if (taskId == null) {
            return;
        }
        boolean dispatched = distributedLockSupport.executeTaskDispatchLockOrSkip(taskId, () -> doRunTaskAsync(taskId));
        if (!dispatched) {
            log.debug("成本任务已被其他节点或线程接管，跳过重复派发，taskId={}", taskId);
        }
    }

    private void doRunTaskAsync(Long taskId) {
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null || TASK_STATUS_CANCELLED.equals(task.getTaskStatus())) {
            return;
        }
        Date startedTime = DateUtils.getNowDate();
        TaskClaimResult claimResult = tryClaimTaskExecution(task, startedTime);
        if (claimResult == null) {
            return;
        }

        try {
            RuntimeSnapshot snapshot = loadRuntimeSnapshot(task.getSceneId(), task.getVersionId(), true);
            startedTime = claimResult.startedTime;

            List<CostCalcTaskDetail> details = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                    .eq(CostCalcTaskDetail::getTaskId, taskId)
                    .in(CostCalcTaskDetail::getDetailStatus, DETAIL_STATUS_INIT, DETAIL_STATUS_FAILED)
                    .orderByAsc(CostCalcTaskDetail::getPartitionNo)
                    .orderByAsc(CostCalcTaskDetail::getDetailId));
            if (details.isEmpty()) {
                finishTask(taskId, startedTime);
                return;
            }
            List<List<CostCalcTaskDetail>> partitions = splitTaskPartitions(details);
            ExecutorCompletionService<PartitionExecutionResult> completionService =
                    new ExecutorCompletionService<>(threadPoolTaskExecutor.getThreadPoolExecutor());
            Map<Future<PartitionExecutionResult>, PartitionDispatchContext> futurePartitions = new LinkedHashMap<>();
            int nextPartitionIndex = 0;
            int completedCount = 0;
            int maxParallelism = resolveTaskParallelism(partitions.size());
            nextPartitionIndex = dispatchLocalRunnablePartitions(taskId, snapshot, partitions, nextPartitionIndex,
                    maxParallelism, completionService, futurePartitions);
            while (completedCount < partitions.size()) {
                if (futurePartitions.isEmpty()) {
                    break;
                }
                Future<PartitionExecutionResult> future = completionService.take();
                PartitionDispatchContext dispatchContext = futurePartitions.remove(future);
                List<CostCalcTaskDetail> partition = dispatchContext == null ? List.of() : dispatchContext.partitionDetails;
                PartitionClaimToken claimToken = dispatchContext == null ? null : dispatchContext.claimToken;
                completedCount++;
                try {
                    PartitionExecutionResult partitionResult = future.get();
                    finishPartition(taskId, partition, claimToken, partitionResult, null);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    PartitionExecutionResult fallbackResult = markPartitionFailed(taskId, partition, claimToken, cause);
                    finishPartition(taskId, partition, claimToken, fallbackResult, cause);
                }
                refreshTaskProgress(taskId);
                CostCalcTask latestTask = calcTaskMapper.selectById(taskId);
                if (latestTask == null || TASK_STATUS_CANCELLED.equals(latestTask.getTaskStatus())) {
                    break;
                }
                nextPartitionIndex = dispatchLocalRunnablePartitions(taskId, snapshot, partitions, nextPartitionIndex,
                        maxParallelism, completionService, futurePartitions);
            }
            tryFinalizeTaskIfReady(taskId);
        } catch (Exception e) {
            calcTaskMapper.update(null, Wrappers.<CostCalcTask>lambdaUpdate()
                    .eq(CostCalcTask::getTaskId, taskId)
                    .set(CostCalcTask::getTaskStatus, TASK_STATUS_FAILED)
                    .set(CostCalcTask::getErrorMessage, limitLength(e.getMessage(), 1000))
                    .set(CostCalcTask::getFinishedTime, DateUtils.getNowDate())
                    .set(CostCalcTask::getDurationMs, DateUtils.getNowDate().getTime() - startedTime.getTime())
                    .set(CostCalcTask::getUpdateTime, DateUtils.getNowDate()));
            CostCalcTask latest = calcTaskMapper.selectById(taskId);
            if (latest != null) {
                refreshBillPeriod(latest.getSceneId(), latest.getBillMonth(), latest);
                syncRecalcByTask(latest, TASK_STATUS_FAILED);
                createTaskAlarm(latest, null, "TASK_FAILED", "ERROR",
                        "正式核算任务执行失败", limitLength(e.getMessage(), 500));
            }
        }
    }

    /**
     * 在事务提交后尝试认领任务执行权，避免新建任务尚未落库时工作线程读取不到数据。
     */
    private TaskClaimResult tryClaimTaskExecution(CostCalcTask task, Date startedTime) {
        if (task == null || task.getTaskId() == null) {
            return null;
        }
        if (TASK_STATUS_INIT.equals(task.getTaskStatus())) {
            return tryClaimInitTaskExecution(task.getTaskId(), startedTime);
        }
        if (TASK_STATUS_RUNNING.equals(task.getTaskStatus()) && isTaskStale(task)) {
            boolean recovered = recoverStaleTaskForRedispatch(task, startedTime);
            if (recovered) {
                return tryClaimInitTaskExecution(task.getTaskId(), DateUtils.getNowDate());
            }
        }
        return null;
    }

    private TaskClaimResult tryClaimInitTaskExecution(Long taskId, Date startedTime) {
        Date claimTime = startedTime == null ? DateUtils.getNowDate() : startedTime;
        int updated = calcTaskMapper.update(null, Wrappers.<CostCalcTask>lambdaUpdate()
                .eq(CostCalcTask::getTaskId, taskId)
                .eq(CostCalcTask::getTaskStatus, TASK_STATUS_INIT)
                .set(CostCalcTask::getTaskStatus, TASK_STATUS_RUNNING)
                .set(CostCalcTask::getStartedTime, claimTime)
                .set(CostCalcTask::getFinishedTime, null)
                .set(CostCalcTask::getDurationMs, 0L)
                .set(CostCalcTask::getExecuteNode, resolveExecuteNode())
                .set(CostCalcTask::getErrorMessage, "")
                .set(CostCalcTask::getUpdateTime, claimTime));
        return updated > 0 ? new TaskClaimResult(claimTime) : null;
    }

    private boolean recoverStaleTaskForRedispatch(CostCalcTask task, Date now) {
        Date recoverTime = now == null ? DateUtils.getNowDate() : now;
        Date staleUpdateTime = task.getUpdateTime();
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
        {
            List<Integer> runningPartitionNos = calcTaskPartitionMapper.selectList(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                            .eq(CostCalcTaskPartition::getTaskId, task.getTaskId())
                            .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_RUNNING))
                    .stream()
                    .map(CostCalcTaskPartition::getPartitionNo)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            int resetTask = calcTaskMapper.update(null, Wrappers.<CostCalcTask>lambdaUpdate()
                    .eq(CostCalcTask::getTaskId, task.getTaskId())
                    .eq(CostCalcTask::getTaskStatus, TASK_STATUS_RUNNING)
                    .eq(staleUpdateTime != null, CostCalcTask::getUpdateTime, staleUpdateTime)
                    .set(CostCalcTask::getTaskStatus, TASK_STATUS_INIT)
                    .set(CostCalcTask::getSuccessCount, 0)
                    .set(CostCalcTask::getFailCount, 0)
                    .set(CostCalcTask::getProgressPercent, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .set(CostCalcTask::getFinishedTime, null)
                    .set(CostCalcTask::getDurationMs, 0L)
                    .set(CostCalcTask::getErrorMessage, "检测到节点心跳超时，已转入待恢复调度")
                    .set(CostCalcTask::getUpdateTime, recoverTime));
            if (resetTask <= 0) {
                return false;
            }
            calcTaskPartitionMapper.update(null, Wrappers.<CostCalcTaskPartition>lambdaUpdate()
                    .eq(CostCalcTaskPartition::getTaskId, task.getTaskId())
                    .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_RUNNING)
                    .set(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT)
                    .set(CostCalcTaskPartition::getProcessedCount, 0)
                    .set(CostCalcTaskPartition::getSuccessCount, 0)
                    .set(CostCalcTaskPartition::getFailCount, 0)
                    .set(CostCalcTaskPartition::getExecuteNode, null)
                    .set(CostCalcTaskPartition::getClaimTime, null)
                    .set(CostCalcTaskPartition::getAmountTotal, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .set(CostCalcTaskPartition::getStartedTime, null)
                    .set(CostCalcTaskPartition::getFinishedTime, null)
                    .set(CostCalcTaskPartition::getDurationMs, 0L)
                    .set(CostCalcTaskPartition::getRecoveryHint, "检测到节点心跳超时，已自动回收并重新调度")
                    .set(CostCalcTaskPartition::getLastErrorStage, PARTITION_STAGE_EXECUTION)
                    .set(CostCalcTaskPartition::getLastError, "节点执行心跳超时")
                    .set(CostCalcTaskPartition::getUpdateTime, recoverTime));
            if (!runningPartitionNos.isEmpty()) {
                calcTaskDetailMapper.update(null, Wrappers.<CostCalcTaskDetail>lambdaUpdate()
                        .eq(CostCalcTaskDetail::getTaskId, task.getTaskId())
                        .in(CostCalcTaskDetail::getPartitionNo, runningPartitionNos)
                        .set(CostCalcTaskDetail::getDetailStatus, DETAIL_STATUS_INIT)
                        .set(CostCalcTaskDetail::getErrorMessage, "所在分片节点心跳超时，已整体回卷重跑")
                        .set(CostCalcTaskDetail::getResultSummary, ""));
            }
            return true;
        }));
    }

    private boolean isTaskStale(CostCalcTask task) {
        if (task == null || !TASK_STATUS_RUNNING.equals(task.getTaskStatus()) || task.getFinishedTime() != null) {
            return false;
        }
        Date heartbeatTime = task.getUpdateTime() != null ? task.getUpdateTime() : task.getStartedTime();
        if (heartbeatTime == null) {
            return true;
        }
        return heartbeatTime.getTime() <= System.currentTimeMillis() - resolveTaskStaleTimeoutMillis();
    }

    private void dispatchTaskAfterCommit(Long taskId) {
        Runnable runnable = () -> scheduleTaskExecution(taskId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new AfterCommitTaskSynchronization(runnable));
            return;
        }
        runnable.run();
    }

    private void scheduleTaskExecution(Long taskId) {
        if (taskId == null) {
            return;
        }
        threadPoolTaskExecutor.execute(() -> runTaskAsync(taskId));
    }

    private int dispatchLocalRunnablePartitions(Long taskId, RuntimeSnapshot snapshot,
                                                List<List<CostCalcTaskDetail>> partitions, int nextPartitionIndex, int maxParallelism,
                                                ExecutorCompletionService<PartitionExecutionResult> completionService,
                                                Map<Future<PartitionExecutionResult>, PartitionDispatchContext> futurePartitions) {
        int partitionIndex = nextPartitionIndex;
        while (partitionIndex < partitions.size() && futurePartitions.size() < maxParallelism) {
            List<CostCalcTaskDetail> partition = partitions.get(partitionIndex++);
            PartitionClaimToken claimToken = tryMarkPartitionRunning(taskId, partition);
            if (claimToken == null) {
                continue;
            }
            Future<PartitionExecutionResult> future =
                    completionService.submit(() -> executeTaskPartition(taskId, snapshot, partition, claimToken));
            futurePartitions.put(future, new PartitionDispatchContext(partition, claimToken));
        }
        return partitionIndex;
    }

    private int dispatchPendingInitTasks(Set<Long> excludedTaskIds) {
        List<CostCalcTask> initTasks = calcTaskMapper.selectList(Wrappers.<CostCalcTask>lambdaQuery()
                .eq(CostCalcTask::getTaskStatus, TASK_STATUS_INIT)
                .notIn(excludedTaskIds != null && !excludedTaskIds.isEmpty(), CostCalcTask::getTaskId, excludedTaskIds)
                .orderByAsc(CostCalcTask::getTaskId)
                .last("limit " + TASK_DISPATCH_SCAN_LIMIT));
        List<Long> taskIds = initTasks.stream()
                .map(CostCalcTask::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (excludedTaskIds != null) {
            excludedTaskIds.addAll(taskIds);
        }
        taskIds.forEach(this::scheduleTaskExecution);
        return taskIds.size();
    }

    private int dispatchStaleRunningTasks(Set<Long> excludedTaskIds) {
        Date cutoff = new Date(System.currentTimeMillis() - resolveTaskStaleTimeoutMillis());
        List<CostCalcTask> staleTasks = calcTaskMapper.selectList(Wrappers.<CostCalcTask>lambdaQuery()
                .eq(CostCalcTask::getTaskStatus, TASK_STATUS_RUNNING)
                .lt(CostCalcTask::getUpdateTime, cutoff)
                .notIn(excludedTaskIds != null && !excludedTaskIds.isEmpty(), CostCalcTask::getTaskId, excludedTaskIds)
                .orderByAsc(CostCalcTask::getUpdateTime)
                .last("limit " + TASK_DISPATCH_SCAN_LIMIT));
        List<Long> taskIds = staleTasks.stream()
                .map(CostCalcTask::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (excludedTaskIds != null) {
            excludedTaskIds.addAll(taskIds);
        }
        taskIds.forEach(this::scheduleTaskExecution);
        return taskIds.size();
    }

    /**
     * 处理单条任务明细，并落结果台账与追溯解释。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void processTaskDetail(CostCalcTask task, CostCalcTaskDetail detail, RuntimeSnapshot snapshot) {
        Map<String, Object> input = parseObjectJson(detail.getInputJson(), "任务明细输入必须是 JSON 对象");
        purgeExistingTaskResults(task.getTaskId(), detail.getBizNo());
        ExecutionResult executionResult = executeSingle(snapshot, task.getTaskNo(), task.getBillMonth(), input);

        List<CostResultLedger> ledgers = new ArrayList<>();
        for (FeeExecutionResult feeResult : executionResult.feeResults) {
            CostResultTrace trace = new CostResultTrace();
            trace.setSceneId(snapshot.sceneId);
            trace.setVersionId(snapshot.versionId);
            trace.setRuleId(feeResult.ruleId);
            trace.setTierId(feeResult.tierId);
            trace.setVariableJson(writeJson(feeResult.variableExplain));
            trace.setConditionJson(writeJson(feeResult.conditionExplain));
            trace.setPricingJson(writeJson(feeResult.pricingExplain));
            trace.setTimelineJson(writeJson(feeResult.timelineSteps));
            resultTraceMapper.insert(trace);

            CostResultLedger ledger = new CostResultLedger();
            ledger.setTaskId(task.getTaskId());
            ledger.setTaskNo(task.getTaskNo());
            ledger.setSceneId(snapshot.sceneId);
            ledger.setVersionId(snapshot.versionId);
            ledger.setFeeId(feeResult.feeId);
            ledger.setFeeCode(feeResult.feeCode);
            ledger.setFeeName(feeResult.feeName);
            ledger.setBizNo(detail.getBizNo());
            ledger.setBillMonth(task.getBillMonth());
            ledger.setObjectDimension(firstNonBlank(feeResult.objectDimension, resolveString(input, "objectDimension", "object_dimension")));
            ledger.setObjectCode(firstNonBlank(resolveString(input, "objectCode", "object_code"), detail.getBizNo()));
            ledger.setObjectName(resolveString(input, "objectName", "object_name", "name"));
            ledger.setQuantityValue(feeResult.quantityValue);
            ledger.setUnitPrice(feeResult.unitPrice);
            ledger.setAmountValue(feeResult.amountValue);
            ledger.setCurrencyCode("CNY");
            ledger.setResultStatus(RESULT_STATUS_SUCCESS);
            ledger.setTraceId(trace.getTraceId());
            resultLedgerMapper.insert(ledger);
            ledgers.add(ledger);
        }

        calcTaskDetailMapper.update(null, Wrappers.<CostCalcTaskDetail>lambdaUpdate()
                .eq(CostCalcTaskDetail::getDetailId, detail.getDetailId())
                .set(CostCalcTaskDetail::getDetailStatus, DETAIL_STATUS_SUCCESS)
                .set(CostCalcTaskDetail::getResultSummary, buildDetailSummary(ledgers))
                .set(CostCalcTaskDetail::getErrorMessage, ""));
    }

    private void purgeExistingTaskResults(Long taskId, String bizNo) {
        List<CostResultLedger> existing = resultLedgerMapper.selectList(Wrappers.<CostResultLedger>lambdaQuery()
                .eq(CostResultLedger::getTaskId, taskId)
                .eq(CostResultLedger::getBizNo, bizNo));
        if (existing.isEmpty()) {
            return;
        }
        List<Long> traceIds = existing.stream().map(CostResultLedger::getTraceId).filter(Objects::nonNull).collect(Collectors.toList());
        resultLedgerMapper.deleteBatchIds(existing.stream().map(CostResultLedger::getResultId).collect(Collectors.toList()));
        if (!traceIds.isEmpty()) {
            resultTraceMapper.deleteBatchIds(traceIds);
        }
    }

    private void refreshTaskProgress(Long taskId) {
        TaskExecutionSummary summary = summarizeTaskDetails(taskId);
        BigDecimal progress = summary.totalCount <= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(summary.processedCount * 100.0 / summary.totalCount).setScale(2, RoundingMode.HALF_UP);
        calcTaskMapper.update(null, Wrappers.<CostCalcTask>lambdaUpdate()
                .eq(CostCalcTask::getTaskId, taskId)
                .set(CostCalcTask::getSuccessCount, summary.successCount)
                .set(CostCalcTask::getFailCount, summary.failedCount)
                .set(CostCalcTask::getProgressPercent, progress)
                .set(CostCalcTask::getUpdateTime, DateUtils.getNowDate()));
    }

    private void finishTask(Long taskId, Date startedTime) {
        CostCalcTask latestTask = calcTaskMapper.selectById(taskId);
        if (latestTask != null && TASK_STATUS_CANCELLED.equals(latestTask.getTaskStatus())) {
            return;
        }
        long pendingPartitions = calcTaskPartitionMapper.selectCount(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .in(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT, TASK_STATUS_RUNNING));
        if (pendingPartitions > 0) {
            return;
        }
        TaskExecutionSummary summary = summarizeTaskDetails(taskId);
        String status = summary.failedCount <= 0
                ? TASK_STATUS_SUCCESS
                : (summary.successCount > 0 ? TASK_STATUS_PART_SUCCESS : TASK_STATUS_FAILED);
        Date finishedTime = DateUtils.getNowDate();
        calcTaskMapper.update(null, Wrappers.<CostCalcTask>lambdaUpdate()
                .eq(CostCalcTask::getTaskId, taskId)
                .set(CostCalcTask::getTaskStatus, status)
                .set(CostCalcTask::getSuccessCount, summary.successCount)
                .set(CostCalcTask::getFailCount, summary.failedCount)
                .set(CostCalcTask::getProgressPercent, BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP))
                .set(CostCalcTask::getFinishedTime, finishedTime)
                .set(CostCalcTask::getDurationMs, finishedTime.getTime() - startedTime.getTime())
                .set(CostCalcTask::getUpdateTime, finishedTime));
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task != null) {
            runTaskFinishSideEffect(task, "billPeriodRefresh",
                    () -> refreshBillPeriod(task.getSceneId(), task.getBillMonth(), task));
            runTaskFinishSideEffect(task, "recalcSync",
                    () -> syncRecalcByTask(task, status));
            runTaskFinishSideEffect(task, "taskAudit",
                    () -> auditService.recordAudit(task.getSceneId(), "CALC_TASK", task.getTaskNo(),
                            "FINISH", "正式核算任务完成", null, task, task.getRequestNo()));
            if (TASK_STATUS_SUCCESS.equals(status)) {
                runTaskFinishSideEffect(task, "alarmAutoResolve",
                        () -> alarmService.autoResolveByTask(task.getTaskId(), "任务执行成功，自动关闭历史任务告警"));
            }
            if (TASK_STATUS_FAILED.equals(status) || TASK_STATUS_PART_SUCCESS.equals(status)) {
                runTaskFinishSideEffect(task, "finishAlarm",
                        () -> createTaskAlarm(task, null, "TASK_FINISHED_WITH_ERROR",
                                TASK_STATUS_FAILED.equals(status) ? "ERROR" : "WARN",
                                TASK_STATUS_FAILED.equals(status) ? "正式核算任务失败" : "正式核算任务部分成功",
                                "任务 " + task.getTaskNo() + " 完成状态为 " + status + "，成功 " + summary.successCount + " 条，失败 " + summary.failedCount + " 条。"));
            }
        }
    }

    /**
     * 执行单条业务对象的统一核算内核。
     * <p>该方法同时服务试算和正式核算。</p>
     * 1. 基于发布快照构建只读运行视图。
     * 2. 逐费用匹配规则、阶梯并生成结果与追溯信息。
     */
    private ExecutionResult executeSingle(RuntimeSnapshot snapshot, String taskNo, String billMonth, Map<String, Object> input) {
        return executeSingle(snapshot, taskNo, billMonth, input, snapshot.fees, false);
    }

    private ExecutionResult executeSingle(RuntimeSnapshot snapshot, String taskNo, String billMonth,
                                          Map<String, Object> input, List<RuntimeFee> targetFees) {
        return executeSingle(snapshot, taskNo, billMonth, input, targetFees, false);
    }

    private ExecutionResult executeSingle(RuntimeSnapshot snapshot, String taskNo, String billMonth,
                                          Map<String, Object> input, List<RuntimeFee> targetFees, boolean includeExplain) {
        List<RuntimeFee> feesToExecute = targetFees == null || targetFees.isEmpty() ? snapshot.fees : targetFees;
        LinkedHashMap<String, Object> baseContext = new LinkedHashMap<>(input);
        baseContext.put("billMonth", billMonth);
        baseContext.put("sceneCode", snapshot.sceneCode);
        baseContext.put("versionNo", snapshot.versionNo);
        LinkedHashMap<String, Object> variableValues = runtimeVariableComputeService.compute(snapshot, baseContext,
                resolveExecutionVariables(snapshot, feesToExecute));
        return costNodeExecutor.execute(snapshot, taskNo, billMonth, input, resolveBizNo(input, 1),
                feesToExecute, includeExplain, baseContext, variableValues);
    }


    private Map<String, Object> buildTraceView(CostResultTrace trace) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", trace.getTraceId());
        result.put("sceneId", trace.getSceneId());
        result.put("versionId", trace.getVersionId());
        result.put("ruleId", trace.getRuleId());
        result.put("tierId", trace.getTierId());
        result.put("variables", parseJsonToObject(trace.getVariableJson()));
        result.put("conditions", parseJsonToObject(trace.getConditionJson()));
        result.put("pricing", parseJsonToObject(trace.getPricingJson()));
        result.put("timeline", parseJsonToObject(trace.getTimelineJson()));
        result.put("createTime", trace.getCreateTime());
        return result;
    }

    private List<Map<String, Object>> buildTemplateFieldItems(RuntimeSnapshot snapshot) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (RuntimeVariable variable : snapshot.variables) {
            LinkedHashMap<String, Object> field = new LinkedHashMap<>();
            String path = resolveTemplatePath(variable);
            boolean formulaDerived = SOURCE_TYPE_FORMULA.equals(variable.sourceType);
            field.put("variableCode", variable.variableCode);
            field.put("variableName", variable.variableName);
            field.put("sourceType", variable.sourceType);
            field.put("dataType", variable.dataType);
            field.put("sourceSystem", variable.sourceSystem);
            field.put("path", path);
            field.put("includedInTemplate", !formulaDerived && StringUtils.isNotEmpty(path));
            field.put("templateRole", formulaDerived ? "FORMULA_DERIVED"
                    : (SOURCE_TYPE_REMOTE.equalsIgnoreCase(variable.sourceType) ? "REMOTE_REQUIRED" : "INPUT_REQUIRED"));
            field.put("exampleValue", buildTemplateValue(variable, 1));
            fields.add(field);
        }
        return fields;
    }

    private List<Map<String, Object>> buildFeeTemplateFieldItems(FeeTemplateContext templateContext) {
        return templateContext.variables.values().stream()
                .sorted(Comparator.comparing((FeeTemplateVariable item) -> !item.includedInTemplate)
                        .thenComparingInt(item -> item.variable.sortNo == null ? 9999 : item.variable.sortNo)
                        .thenComparing(item -> firstNonBlank(item.variable.variableCode, "")))
                .map(item -> {
                    RuntimeVariable variable = item.variable;
                    LinkedHashMap<String, Object> field = new LinkedHashMap<>();
                    String path = resolveTemplatePath(variable);
                    field.put("variableCode", variable.variableCode);
                    field.put("variableName", variable.variableName);
                    field.put("sourceType", variable.sourceType);
                    field.put("dataType", variable.dataType);
                    field.put("sourceSystem", variable.sourceSystem);
                    field.put("path", path);
                    field.put("includedInTemplate", item.includedInTemplate && StringUtils.isNotEmpty(path));
                    field.put("templateRoles", new ArrayList<>(item.templateRoles));
                    field.put("sourceRuleCodes", new ArrayList<>(item.sourceRuleCodes));
                    field.put("dependsOn", new ArrayList<>(item.dependsOn));
                    field.put("defaultValue", variable.defaultValue);
                    field.put("exampleValue", item.includedInTemplate && StringUtils.isNotEmpty(path)
                            ? buildTemplateValue(variable, 1) : null);
                    return field;
                })
                .collect(Collectors.toList());
    }

    private FeeTemplateContext buildFeeTemplateContext(RuntimeSnapshot snapshot, List<RuntimeRule> rules) {
        FeeTemplateContext context = new FeeTemplateContext();
        if (rules == null || rules.isEmpty()) {
            return context;
        }
        Map<String, RuntimeVariable> variableMap = snapshot.variablesByCode == null
                ? Collections.emptyMap() : snapshot.variablesByCode;
        for (RuntimeRule rule : rules) {
            LinkedHashSet<String> conditionVariableCodes = new LinkedHashSet<>();
            LinkedHashSet<String> expressionVariableCodes = new LinkedHashSet<>();
            LinkedHashSet<String> formulaVariableCodes = new LinkedHashSet<>();
            if (StringUtils.isNotEmpty(rule.quantityVariableCode)) {
                collectTemplateVariable(context, snapshot, variableMap, rule.quantityVariableCode,
                        "QUANTITY_BASIS", rule.ruleCode, new LinkedHashSet<>());
            }
            for (RuntimeCondition condition : rule.conditions) {
                if (StringUtils.isNotEmpty(condition.variableCode)) {
                    conditionVariableCodes.add(condition.variableCode);
                    collectTemplateVariable(context, snapshot, variableMap, condition.variableCode,
                            "CONDITION", rule.ruleCode, new LinkedHashSet<>());
                }
                if (OP_EXPR.equalsIgnoreCase(condition.operatorCode)) {
                    Set<String> referencedVariables = extractExpressionVariableCodes(condition.compareValue, variableMap);
                    expressionVariableCodes.addAll(referencedVariables);
                    for (String variableCode : referencedVariables) {
                        collectTemplateVariable(context, snapshot, variableMap, variableCode,
                                "EXPRESSION_INPUT", rule.ruleCode, new LinkedHashSet<>());
                    }
                }
            }
            String ruleExpression = resolveRuleExpression(snapshot, rule);
            if (StringUtils.isNotEmpty(ruleExpression)) {
                formulaVariableCodes.addAll(extractExpressionVariableCodes(ruleExpression, variableMap));
                for (String variableCode : formulaVariableCodes) {
                    collectTemplateVariable(context, snapshot, variableMap, variableCode,
                            "FORMULA_INPUT", rule.ruleCode, new LinkedHashSet<>());
                }
            }
            context.ruleSummaries.add(buildFeeRuleSummary(rule, conditionVariableCodes, expressionVariableCodes, formulaVariableCodes));
        }
        return context;
    }

    private void collectTemplateVariable(FeeTemplateContext context, RuntimeSnapshot snapshot, Map<String, RuntimeVariable> variableMap,
                                         String variableCode, String templateRole, String sourceRuleCode, Set<String> dependencyStack) {
        if (StringUtils.isEmpty(variableCode) || variableMap == null) {
            return;
        }
        RuntimeVariable variable = variableMap.get(variableCode);
        if (variable == null) {
            return;
        }
        FeeTemplateVariable templateVariable = context.variables.computeIfAbsent(variableCode,
                key -> new FeeTemplateVariable(variable));
        if (StringUtils.isNotEmpty(templateRole)) {
            templateVariable.templateRoles.add(templateRole);
        }
        if (StringUtils.isNotEmpty(sourceRuleCode)) {
            templateVariable.sourceRuleCodes.add(sourceRuleCode);
        }
        if (!SOURCE_TYPE_FORMULA.equalsIgnoreCase(variable.sourceType)) {
            templateVariable.includedInTemplate = true;
            if (SOURCE_TYPE_REMOTE.equalsIgnoreCase(variable.sourceType)) {
                templateVariable.templateRoles.add("REMOTE_REQUIRED");
            }
            return;
        }
        templateVariable.templateRoles.add("FORMULA_DERIVED");
        if (!dependencyStack.add(variableCode)) {
            return;
        }
        try {
            String formulaExpression = resolveVariableFormula(snapshot, variable);
            for (String dependencyCode : extractExpressionVariableCodes(formulaExpression, variableMap)) {
                if (StringUtils.equals(variableCode, dependencyCode)) {
                    continue;
                }
                templateVariable.dependsOn.add(dependencyCode);
                collectTemplateVariable(context, snapshot, variableMap, dependencyCode,
                        "FORMULA_INPUT", sourceRuleCode, dependencyStack);
            }
        } finally {
            dependencyStack.remove(variableCode);
        }
    }

    private Map<String, Object> buildFeeRuleSummary(RuntimeRule rule, Set<String> conditionVariableCodes,
                                                    Set<String> expressionVariableCodes, Set<String> formulaVariableCodes) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("ruleCode", rule.ruleCode);
        item.put("ruleName", rule.ruleName);
        item.put("ruleType", rule.ruleType);
        item.put("pricingMode", rule.pricingMode);
        item.put("conditionLogic", rule.conditionLogic);
        item.put("priority", rule.priority);
        item.put("quantityVariableCode", rule.quantityVariableCode);
        item.put("conditionVariableCodes", new ArrayList<>(conditionVariableCodes));
        item.put("expressionVariableCodes", new ArrayList<>(expressionVariableCodes));
        item.put("formulaVariableCodes", new ArrayList<>(formulaVariableCodes));
        item.put("tierCount", rule.tiers == null ? 0 : rule.tiers.size());
        return item;
    }

    private List<RuntimeFee> resolveFeeExecutionChain(RuntimeSnapshot snapshot, RuntimeFee targetFee) {
        if (snapshot == null || targetFee == null) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, RuntimeFee> orderedFees = new LinkedHashMap<>();
        collectFeeExecutionDependency(snapshot, targetFee.feeCode, orderedFees, new LinkedHashSet<>());
        return new ArrayList<>(orderedFees.values());
    }

    private List<RuntimeFee> resolveFeeExecutionChains(RuntimeSnapshot snapshot, List<RuntimeFee> targetFees) {
        if (snapshot == null) {
            return Collections.emptyList();
        }
        if (targetFees == null || targetFees.isEmpty() || isAllFeeScope(snapshot, targetFees)) {
            return snapshot.fees == null ? Collections.emptyList() : snapshot.fees;
        }
        LinkedHashMap<String, RuntimeFee> orderedFees = new LinkedHashMap<>();
        for (RuntimeFee targetFee : targetFees) {
            if (targetFee != null) {
                collectFeeExecutionDependency(snapshot, targetFee.feeCode, orderedFees, new LinkedHashSet<>());
            }
        }
        return new ArrayList<>(orderedFees.values());
    }

    private List<RuntimeFee> resolveTargetRuntimeFees(RuntimeSnapshot snapshot, List<Long> feeIds, Long feeId, String feeCode) {
        if (snapshot == null || snapshot.fees == null || snapshot.fees.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> targetIds = new LinkedHashSet<>();
        if (feeIds != null) {
            for (Long id : feeIds) {
                if (id != null) {
                    targetIds.add(id);
                }
            }
        }
        if (feeId != null) {
            targetIds.add(feeId);
        }

        LinkedHashMap<String, RuntimeFee> result = new LinkedHashMap<>();
        if (!targetIds.isEmpty()) {
            Set<Long> missingIds = new LinkedHashSet<>(targetIds);
            for (RuntimeFee fee : snapshot.fees) {
                if (targetIds.contains(fee.feeId)) {
                    result.put(fee.feeCode, fee);
                    missingIds.remove(fee.feeId);
                }
            }
            if (!missingIds.isEmpty()) {
                throw new ServiceException("指定费用在当前发布版本快照中不存在：" + missingIds);
            }
        }
        if (StringUtils.isNotEmpty(feeCode)) {
            RuntimeFee fee = resolveRuntimeFee(snapshot, null, feeCode);
            result.put(fee.feeCode, fee);
        }
        if (result.isEmpty()) {
            return snapshot.fees;
        }
        return new ArrayList<>(result.values());
    }

    private boolean isAllFeeScope(RuntimeSnapshot snapshot, List<RuntimeFee> targetFees) {
        return snapshot != null
                && snapshot.fees != null
                && targetFees != null
                && !targetFees.isEmpty()
                && targetFees.size() == snapshot.fees.size();
    }

    private Map<String, Object> buildTargetFeeView(RuntimeSnapshot snapshot, List<RuntimeFee> targetFees, boolean allFeeScope) {
        LinkedHashMap<String, Object> feeView = new LinkedHashMap<>();
        List<RuntimeFee> normalizedTargetFees = targetFees == null ? Collections.emptyList() : targetFees;
        if (normalizedTargetFees.size() == 1) {
            RuntimeFee fee = normalizedTargetFees.get(0);
            feeView.put("mode", "SINGLE");
            feeView.put("feeId", fee.feeId);
            feeView.put("feeCode", fee.feeCode);
            feeView.put("feeName", fee.feeName);
            feeView.put("unitCode", fee.unitCode);
            feeView.put("objectDimension", fee.objectDimension);
            feeView.put("feeCount", 1);
            feeView.put("feeCodes", Collections.singletonList(fee.feeCode));
            return feeView;
        }
        feeView.put("mode", allFeeScope ? "ALL" : "MULTI");
        feeView.put("feeId", null);
        feeView.put("feeCode", allFeeScope ? "ALL_FEES" : "MULTI_FEES");
        feeView.put("feeName", allFeeScope ? "全部费用" : "已选 " + normalizedTargetFees.size() + " 项费用");
        feeView.put("unitCode", "");
        feeView.put("objectDimension", resolveCommonObjectDimension(snapshot, normalizedTargetFees));
        feeView.put("feeCount", normalizedTargetFees.size());
        feeView.put("feeCodes", normalizedTargetFees.stream().map(item -> item.feeCode).collect(Collectors.toList()));
        feeView.put("feeNames", normalizedTargetFees.stream().map(item -> item.feeName).collect(Collectors.toList()));
        return feeView;
    }

    private String resolveCommonObjectDimension(RuntimeSnapshot snapshot, List<RuntimeFee> targetFees) {
        LinkedHashSet<String> dimensions = new LinkedHashSet<>();
        if (targetFees != null) {
            for (RuntimeFee fee : targetFees) {
                if (fee != null && StringUtils.isNotEmpty(fee.objectDimension)) {
                    dimensions.add(fee.objectDimension);
                }
            }
        }
        if (dimensions.size() == 1) {
            return dimensions.iterator().next();
        }
        return snapshot == null ? "" : firstNonBlank(snapshot.defaultObjectDimension, "");
    }

    private void collectFeeExecutionDependency(RuntimeSnapshot snapshot, String feeCode,
                                               Map<String, RuntimeFee> orderedFees, Set<String> dependencyStack) {
        if (StringUtils.isEmpty(feeCode) || snapshot == null || snapshot.feesByCode == null) {
            return;
        }
        if (orderedFees.containsKey(feeCode)) {
            return;
        }
        RuntimeFee currentFee = snapshot.feesByCode.get(feeCode);
        if (currentFee == null) {
            return;
        }
        if (!dependencyStack.add(feeCode)) {
            throw new ServiceException("费用公式存在循环依赖：" + String.join(" -> ", dependencyStack) + " -> " + feeCode);
        }
        try {
            for (RuntimeRule rule : snapshot.rulesByFeeCode.getOrDefault(feeCode, Collections.emptyList())) {
                for (String dependencyFeeCode : extractExpressionFeeCodes(resolveRuleExpression(snapshot, rule))) {
                    if (!StringUtils.equals(feeCode, dependencyFeeCode)) {
                        collectFeeExecutionDependency(snapshot, dependencyFeeCode, orderedFees, dependencyStack);
                    }
                }
            }
            orderedFees.put(feeCode, currentFee);
        } finally {
            dependencyStack.remove(feeCode);
        }
    }

    private Set<String> extractExpressionFeeCodes(String expression) {
        return expressionService.extractFeeReferences(expression);
    }

    private List<RuntimeVariable> buildExecutionVariables(RuntimeSnapshot snapshot, List<RuntimeRule> rules) {
        if (snapshot == null || rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        FeeTemplateContext context = buildFeeTemplateContext(snapshot, rules);
        if (context.variables.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> variableCodes = context.variables.keySet();
        return snapshot.variables.stream()
                .filter(item -> variableCodes.contains(item.variableCode))
                .collect(Collectors.toList());
    }

    private List<RuntimeVariable> resolveExecutionVariables(RuntimeSnapshot snapshot, List<RuntimeFee> feesToExecute) {
        if (snapshot == null || feesToExecute == null || feesToExecute.isEmpty() || feesToExecute == snapshot.fees) {
            return snapshot == null ? Collections.emptyList() : snapshot.variables;
        }
        if (feesToExecute.size() == 1) {
            RuntimeFee fee = feesToExecute.get(0);
            return fee == null || snapshot.executionVariablesByFeeCode == null
                    ? Collections.emptyList()
                    : snapshot.executionVariablesByFeeCode.getOrDefault(fee.feeCode, Collections.emptyList());
        }
        LinkedHashSet<String> variableCodes = new LinkedHashSet<>();
        for (RuntimeFee fee : feesToExecute) {
            if (fee == null || snapshot.executionVariablesByFeeCode == null) {
                continue;
            }
            for (RuntimeVariable variable : snapshot.executionVariablesByFeeCode
                    .getOrDefault(fee.feeCode, Collections.emptyList())) {
                variableCodes.add(variable.variableCode);
            }
        }
        if (variableCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return snapshot.variables.stream()
                .filter(item -> variableCodes.contains(item.variableCode))
                .collect(Collectors.toList());
    }

    private RuntimeFee resolveRuntimeFee(RuntimeSnapshot snapshot, Long feeId, String feeCode) {
        if (feeId == null && StringUtils.isEmpty(feeCode)) {
            throw new ServiceException("请提供费用ID或feeCode");
        }
        if (feeId != null) {
            for (RuntimeFee fee : snapshot.fees) {
                if (Objects.equals(fee.feeId, feeId)) {
                    return fee;
                }
            }
        }
        if (StringUtils.isNotEmpty(feeCode) && snapshot.feesByCode != null && snapshot.feesByCode.containsKey(feeCode)) {
            return snapshot.feesByCode.get(feeCode);
        }
        throw new ServiceException("指定费用在当前发布版本快照中不存在");
    }

    private String resolveRuleExpression(RuntimeSnapshot snapshot, RuntimeRule rule) {
        if (RULE_TYPE_FORMULA.equals(rule.ruleType)) {
            return requireRuleFormula(snapshot, rule).formulaExpr;
        }
        return rule.amountFormula;
    }

    private Set<String> extractExpressionVariableCodes(String expression, Map<String, RuntimeVariable> variableMap) {
        return expressionService.extractReferencedCodes(expression,
                variableMap == null ? Collections.emptySet() : variableMap.keySet());
    }

    private String buildTemplateInputJson(List<RuntimeVariable> variables, String taskType, String objectDimension) {
        if (TASK_TYPE_FORMAL_BATCH.equals(taskType) || TASK_TYPE_SIMULATION_BATCH.equals(taskType)) {
            List<Map<String, Object>> samples = new ArrayList<>();
            samples.add(buildSelectedInputTemplate(variables, taskType, 1, objectDimension));
            samples.add(buildSelectedInputTemplate(variables, taskType, 2, objectDimension));
            return writeJson(samples);
        }
        return writeJson(buildSelectedInputTemplate(variables, taskType, 1, objectDimension));
    }

    private String normalizeTemplateTaskType(String taskType) {
        if (StringUtils.isEmpty(taskType)) {
            return TASK_TYPE_FORMAL_SINGLE;
        }
        return taskType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSnapshotMode(String snapshotMode) {
        if (StringUtils.isEmpty(snapshotMode)) {
            return SNAPSHOT_MODE_ACTIVE;
        }
        return SNAPSHOT_MODE_DRAFT.equalsIgnoreCase(snapshotMode.trim())
            ? SNAPSHOT_MODE_DRAFT : SNAPSHOT_MODE_ACTIVE;
    }

    private boolean preferDraftSnapshotWhenVersionMissing(String snapshotMode) {
        return SNAPSHOT_MODE_DRAFT.equals(normalizeSnapshotMode(snapshotMode));
    }

    private RuntimeSnapshot loadRuntimeSnapshot(Long sceneId, Long versionId, boolean requireFormalVersion) {
        return loadRuntimeSnapshot(sceneId, versionId, requireFormalVersion, false);
    }

    private RuntimeSnapshot loadRuntimeSnapshot(Long sceneId, Long versionId, boolean requireFormalVersion,
                                                boolean preferDraftWhenVersionMissing) {
        CostScene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new ServiceException("场景不存在，请刷新后重试");
        }
        if (!requireFormalVersion && preferDraftWhenVersionMissing && versionId == null) {
            return buildDraftRuntimeSnapshot(scene);
        }
        Long targetVersionId = versionId == null ? scene.getActiveVersionId() : versionId;
        if (targetVersionId == null && !requireFormalVersion) {
            return buildDraftRuntimeSnapshot(scene);
        }
        if (targetVersionId == null) {
            throw new ServiceException("当前场景尚未设置生效版本，无法执行运行链");
        }
        CostPublishVersion version = publishVersionMapper.selectPublishVersionDetail(targetVersionId);
        if (version == null) {
            throw new ServiceException("发布版本不存在，请刷新后重试");
        }
        if (requireFormalVersion && versionId == null && !"ACTIVE".equals(version.getVersionStatus())) {
            throw new ServiceException("正式核算默认只能按当前生效版本执行");
        }
        String cacheKey = buildRuntimeCacheKey(targetVersionId);
        try {
            String cached = redisCache.getCacheObject(cacheKey);
            if (StringUtils.isNotEmpty(cached)) {
                return hydrateRuntimeSnapshot(objectMapper.readValue(cached, RuntimeSnapshot.class));
            }
        } catch (Exception ignored) {
        }
        List<CostPublishSnapshot> snapshots = publishVersionMapper.selectSnapshotList(targetVersionId, null);
        if (snapshots == null || snapshots.isEmpty()) {
            throw new ServiceException("发布快照为空，无法执行试算或正式核算");
        }

        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.sceneId = sceneId;
        snapshot.versionId = targetVersionId;
        snapshot.sceneCode = scene.getSceneCode();
        snapshot.sceneName = scene.getSceneName();
        snapshot.defaultObjectDimension = scene.getDefaultObjectDimension();
        snapshot.versionNo = version.getVersionNo();
        snapshot.snapshotSource = SNAPSHOT_SOURCE_PUBLISHED;
        Map<String, Long> feeIdByCode = feeMapper.selectList(Wrappers.<CostFeeItem>lambdaQuery()
                        .eq(CostFeeItem::getSceneId, sceneId))
                .stream()
                .collect(Collectors.toMap(CostFeeItem::getFeeCode, CostFeeItem::getFeeId, (left, right) -> left, LinkedHashMap::new));
        for (CostPublishSnapshot item : snapshots) {
            Map<String, Object> json = parseJsonMap(item.getSnapshotJson());
            if ("SCENE".equals(item.getSnapshotType())) {
                snapshot.defaultObjectDimension = firstNonBlank(stringValue(json.get("defaultObjectDimension")),
                        snapshot.defaultObjectDimension);
            } else if ("FEE".equals(item.getSnapshotType())) {
                RuntimeFee fee = new RuntimeFee();
                fee.feeCode = stringValue(json.get("feeCode"));
                fee.feeId = feeIdByCode.get(fee.feeCode);
                fee.feeName = stringValue(json.get("feeName"));
                fee.unitCode = stringValue(json.get("unitCode"));
                fee.objectDimension = stringValue(json.get("objectDimension"));
                fee.sortNo = intValue(json.get("sortNo"));
                snapshot.fees.add(fee);
                snapshot.feesByCode.put(fee.feeCode, fee);
            } else if ("VARIABLE".equals(item.getSnapshotType())) {
                RuntimeVariable variable = new RuntimeVariable();
                variable.variableCode = stringValue(json.get("variableCode"));
                variable.variableName = stringValue(json.get("variableName"));
                variable.sourceType = stringValue(json.get("sourceType"));
                variable.sourceSystem = stringValue(json.get("sourceSystem"));
                variable.dataType = stringValue(json.get("dataType"));
                variable.remoteApi = stringValue(json.get("remoteApi"));
                variable.authType = stringValue(json.get("authType"));
                variable.authConfigJson = stringValue(json.get("authConfigJson"));
                variable.dataPath = stringValue(json.get("dataPath"));
                variable.mappingConfigJson = stringValue(json.get("mappingConfigJson"));
                variable.syncMode = stringValue(json.get("syncMode"));
                variable.cachePolicy = stringValue(json.get("cachePolicy"));
                variable.fallbackPolicy = stringValue(json.get("fallbackPolicy"));
                variable.formulaExpr = stringValue(json.get("formulaExpr"));
                variable.formulaCode = stringValue(json.get("formulaCode"));
                variable.defaultValue = json.get("defaultValue");
                variable.sortNo = intValue(json.get("sortNo"));
                snapshot.variables.add(variable);
                snapshot.variablesByCode.put(variable.variableCode, variable);
            } else if ("FORMULA".equals(item.getSnapshotType())) {
                RuntimeFormula formula = new RuntimeFormula();
                formula.formulaCode = stringValue(json.get("formulaCode"));
                formula.formulaName = stringValue(json.get("formulaName"));
                formula.businessFormula = stringValue(json.get("businessFormula"));
                formula.formulaExpr = stringValue(json.get("formulaExpr"));
                formula.returnType = stringValue(json.get("returnType"));
                snapshot.formulasByCode.put(formula.formulaCode, formula);
            } else if ("RULE".equals(item.getSnapshotType())) {
                RuntimeRule rule = new RuntimeRule();
                rule.feeCode = stringValue(json.get("feeCode"));
                rule.ruleCode = stringValue(json.get("ruleCode"));
                rule.ruleName = stringValue(json.get("ruleName"));
                rule.ruleType = stringValue(json.get("ruleType"));
                rule.conditionLogic = firstNonBlank(stringValue(json.get("conditionLogic")), "AND");
                rule.priority = intValue(json.get("priority"));
                rule.quantityVariableCode = stringValue(json.get("quantityVariableCode"));
                rule.pricingMode = firstNonBlank(stringValue(json.get("pricingMode")), "TYPED");
                rule.pricingConfig = json.get("pricingJson") instanceof Map ? castMap(json.get("pricingJson")) : new LinkedHashMap<>();
                rule.amountFormula = stringValue(json.get("amountFormula"));
                rule.amountFormulaCode = stringValue(json.get("amountFormulaCode"));
                rule.amountBusinessFormula = stringValue(json.get("amountBusinessFormula"));
                rule.sortNo = intValue(json.get("sortNo"));
                snapshot.rulesByCode.put(rule.ruleCode, rule);
            } else if ("RULE_CONDITION".equals(item.getSnapshotType())) {
                RuntimeCondition condition = new RuntimeCondition();
                condition.ruleCode = stringValue(json.get("ruleCode"));
                condition.groupNo = intValue(json.get("groupNo"));
                condition.sortNo = intValue(json.get("sortNo"));
                condition.variableCode = stringValue(json.get("variableCode"));
                condition.displayName = firstNonBlank(stringValue(json.get("displayName")), condition.variableCode);
                condition.operatorCode = stringValue(json.get("operatorCode"));
                condition.compareValue = stringValue(json.get("compareValue"));
                snapshot.conditionsByRuleCode.computeIfAbsent(condition.ruleCode, key -> new ArrayList<>()).add(condition);
            } else if ("RULE_TIER".equals(item.getSnapshotType())) {
                RuntimeTier tier = new RuntimeTier();
                tier.ruleCode = stringValue(json.get("ruleCode"));
                tier.tierNo = intValue(json.get("tierNo"));
                tier.startValue = toBigDecimal(json.get("startValue"));
                tier.endValue = toBigDecimal(json.get("endValue"));
                tier.rateValue = toBigDecimal(json.get("rateValue"));
                tier.intervalMode = firstNonBlank(stringValue(json.get("intervalMode")), INTERVAL_LCRO);
                snapshot.tiersByRuleCode.computeIfAbsent(tier.ruleCode, key -> new ArrayList<>()).add(tier);
            }
        }

        snapshot = hydrateRuntimeSnapshot(snapshot);
        try {
            redisCache.setCacheObject(cacheKey, objectMapper.writeValueAsString(snapshot), 30, TimeUnit.MINUTES);
        } catch (JsonProcessingException ignored) {
        }
        return snapshot;
    }

    private RuntimeSnapshot hydrateRuntimeSnapshot(RuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return new RuntimeSnapshot();
        }
        if (snapshot.fees == null) {
            snapshot.fees = new ArrayList<>();
        }
        if (snapshot.variables == null) {
            snapshot.variables = new ArrayList<>();
        }
        if (snapshot.feesByCode == null) {
            snapshot.feesByCode = new LinkedHashMap<>();
        }
        if (snapshot.variablesByCode == null) {
            snapshot.variablesByCode = new LinkedHashMap<>();
        }
        if (snapshot.rulesByCode == null) {
            snapshot.rulesByCode = new LinkedHashMap<>();
        }
        if (snapshot.rulesByFeeCode == null) {
            snapshot.rulesByFeeCode = new LinkedHashMap<>();
        }
        if (snapshot.executionVariablesByFeeCode == null) {
            snapshot.executionVariablesByFeeCode = new LinkedHashMap<>();
        }
        if (snapshot.conditionsByRuleCode == null) {
            snapshot.conditionsByRuleCode = new LinkedHashMap<>();
        }
        if (snapshot.tiersByRuleCode == null) {
            snapshot.tiersByRuleCode = new LinkedHashMap<>();
        }
        snapshot.fees.sort(Comparator.comparingInt(item -> item.sortNo == null ? 9999 : item.sortNo));
        snapshot.variables.sort(Comparator.comparingInt(item -> item.sortNo == null ? 9999 : item.sortNo));
        snapshot.feesByCode.clear();
        for (RuntimeFee fee : snapshot.fees) {
            fee.objectDimension = firstNonBlank(fee.objectDimension, snapshot.defaultObjectDimension);
            snapshot.feesByCode.put(fee.feeCode, fee);
        }
        snapshot.variablesByCode.clear();
        for (RuntimeVariable variable : snapshot.variables) {
            snapshot.variablesByCode.put(variable.variableCode, variable);
        }
        snapshot.rulesByFeeCode.clear();
        for (RuntimeRule rule : snapshot.rulesByCode.values()) {
            rule.conditions = snapshot.conditionsByRuleCode.getOrDefault(rule.ruleCode, Collections.emptyList()).stream()
                    .sorted(Comparator.comparingInt((RuntimeCondition item) -> item.groupNo == null ? 1 : item.groupNo)
                            .thenComparingInt(item -> item.sortNo == null ? 9999 : item.sortNo))
                    .collect(Collectors.toList());
            rule.tiers = snapshot.tiersByRuleCode.getOrDefault(rule.ruleCode, Collections.emptyList()).stream()
                    .sorted(Comparator.comparingInt(item -> item.tierNo == null ? 9999 : item.tierNo))
                    .collect(Collectors.toList());
            rule.conditionGroups = buildConditionGroups(rule.conditions);
            snapshot.rulesByFeeCode.computeIfAbsent(rule.feeCode, key -> new ArrayList<>()).add(rule);
        }
        for (List<RuntimeRule> rules : snapshot.rulesByFeeCode.values()) {
            rules.sort(Comparator.comparingInt((RuntimeRule item) -> item.priority == null ? 0 : item.priority).reversed()
                    .thenComparingInt(item -> item.sortNo == null ? 9999 : item.sortNo));
        }
        snapshot.executionVariablesByFeeCode.clear();
        for (RuntimeFee fee : snapshot.fees) {
            snapshot.executionVariablesByFeeCode.put(fee.feeCode,
                    buildExecutionVariables(snapshot, snapshot.rulesByFeeCode.getOrDefault(fee.feeCode, Collections.emptyList())));
        }
        return snapshot;
    }

    private RuntimeSnapshot buildDraftRuntimeSnapshot(CostScene scene) {
        CostFeeItem feeQuery = new CostFeeItem();
        feeQuery.setSceneId(scene.getSceneId());
        feeQuery.setStatus(STATUS_ENABLED);
        List<CostFeeItem> feeItems = feeMapper.selectFeeOptions(feeQuery);
        Map<Long, CostFeeItem> feeById = feeItems.stream()
                .collect(Collectors.toMap(CostFeeItem::getFeeId, item -> item, (left, right) -> left, LinkedHashMap::new));

        CostVariable variableQuery = new CostVariable();
        variableQuery.setSceneId(scene.getSceneId());
        variableQuery.setStatus(STATUS_ENABLED);
        List<CostVariable> variables = variableMapper.selectVariableOptions(variableQuery);

        CostFormula formulaQuery = new CostFormula();
        formulaQuery.setSceneId(scene.getSceneId());
        formulaQuery.setStatus(STATUS_ENABLED);
        List<CostFormula> formulas = formulaMapper.selectFormulaOptions(formulaQuery);

        CostRule ruleQuery = new CostRule();
        ruleQuery.setSceneId(scene.getSceneId());
        ruleQuery.setStatus(STATUS_ENABLED);
        List<CostRule> rules = ruleMapper.selectRuleList(ruleQuery).stream()
                .filter(item -> feeById.containsKey(item.getFeeId()))
                .sorted(Comparator.comparing(CostRule::getRuleCode, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.sceneId = scene.getSceneId();
        snapshot.sceneCode = scene.getSceneCode();
        snapshot.sceneName = scene.getSceneName();
        snapshot.defaultObjectDimension = scene.getDefaultObjectDimension();
        snapshot.versionId = null;
        snapshot.versionNo = DRAFT_VERSION_LABEL;
        snapshot.snapshotSource = SNAPSHOT_SOURCE_DRAFT;
        populateDraftFees(snapshot, feeItems);
        populateDraftVariables(snapshot, variables);
        populateDraftFormulas(snapshot, formulas);
        populateDraftRules(snapshot, rules);
        populateDraftConditions(snapshot, publishVersionMapper.selectRuleConditionsForPublish(scene.getSceneId()));
        populateDraftTiers(snapshot, publishVersionMapper.selectRuleTiersForPublish(scene.getSceneId()));
        return hydrateRuntimeSnapshot(snapshot);
    }

    private void populateDraftFees(RuntimeSnapshot snapshot, List<CostFeeItem> feeItems) {
        for (CostFeeItem fee : feeItems) {
            RuntimeFee runtimeFee = new RuntimeFee();
            runtimeFee.feeId = fee.getFeeId();
            runtimeFee.feeCode = fee.getFeeCode();
            runtimeFee.feeName = fee.getFeeName();
            runtimeFee.unitCode = fee.getUnitCode();
            runtimeFee.objectDimension = firstNonBlank(fee.getObjectDimension(), snapshot.defaultObjectDimension);
            runtimeFee.sortNo = fee.getSortNo();
            snapshot.fees.add(runtimeFee);
            snapshot.feesByCode.put(runtimeFee.feeCode, runtimeFee);
        }
    }

    private void populateDraftVariables(RuntimeSnapshot snapshot, List<CostVariable> variables) {
        for (CostVariable variable : variables) {
            RuntimeVariable runtimeVariable = new RuntimeVariable();
            runtimeVariable.variableCode = variable.getVariableCode();
            runtimeVariable.variableName = variable.getVariableName();
            runtimeVariable.sourceType = variable.getSourceType();
            runtimeVariable.sourceSystem = variable.getSourceSystem();
            runtimeVariable.dataType = variable.getDataType();
            runtimeVariable.remoteApi = variable.getRemoteApi();
            runtimeVariable.authType = variable.getAuthType();
            runtimeVariable.authConfigJson = variable.getAuthConfigJson();
            runtimeVariable.dataPath = variable.getDataPath();
            runtimeVariable.mappingConfigJson = variable.getMappingConfigJson();
            runtimeVariable.syncMode = variable.getSyncMode();
            runtimeVariable.cachePolicy = variable.getCachePolicy();
            runtimeVariable.fallbackPolicy = variable.getFallbackPolicy();
            runtimeVariable.formulaExpr = variable.getFormulaExpr();
            runtimeVariable.formulaCode = variable.getFormulaCode();
            runtimeVariable.defaultValue = variable.getDefaultValue();
            runtimeVariable.sortNo = variable.getSortNo();
            snapshot.variables.add(runtimeVariable);
            snapshot.variablesByCode.put(runtimeVariable.variableCode, runtimeVariable);
        }
    }

    private void populateDraftFormulas(RuntimeSnapshot snapshot, List<CostFormula> formulas) {
        for (CostFormula formula : formulas) {
            RuntimeFormula runtimeFormula = new RuntimeFormula();
            runtimeFormula.formulaCode = formula.getFormulaCode();
            runtimeFormula.formulaName = formula.getFormulaName();
            runtimeFormula.businessFormula = formula.getBusinessFormula();
            runtimeFormula.formulaExpr = formula.getFormulaExpr();
            runtimeFormula.returnType = formula.getReturnType();
            snapshot.formulasByCode.put(runtimeFormula.formulaCode, runtimeFormula);
        }
    }

    private void populateDraftRules(RuntimeSnapshot snapshot, List<CostRule> rules) {
        for (CostRule rule : rules) {
            RuntimeRule runtimeRule = new RuntimeRule();
            runtimeRule.ruleId = rule.getRuleId();
            runtimeRule.feeCode = rule.getFeeCode();
            runtimeRule.ruleCode = rule.getRuleCode();
            runtimeRule.ruleName = rule.getRuleName();
            runtimeRule.ruleType = rule.getRuleType();
            runtimeRule.conditionLogic = firstNonBlank(rule.getConditionLogic(), "AND");
            runtimeRule.priority = rule.getPriority();
            runtimeRule.quantityVariableCode = rule.getQuantityVariableCode();
            runtimeRule.pricingMode = firstNonBlank(rule.getPricingMode(), "TYPED");
            runtimeRule.pricingConfig = parseJsonMap(rule.getPricingJson());
            runtimeRule.amountFormula = rule.getAmountFormula();
            runtimeRule.amountFormulaCode = rule.getAmountFormulaCode();
            runtimeRule.amountBusinessFormula = rule.getAmountBusinessFormula();
            runtimeRule.sortNo = rule.getSortNo();
            snapshot.rulesByCode.put(runtimeRule.ruleCode, runtimeRule);
        }
    }

    private void populateDraftConditions(RuntimeSnapshot snapshot, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String ruleCode = stringValue(row.get("ruleCode"));
            if (!snapshot.rulesByCode.containsKey(ruleCode)) {
                continue;
            }
            RuntimeCondition condition = new RuntimeCondition();
            condition.ruleCode = ruleCode;
            condition.groupNo = intValue(row.get("groupNo"));
            condition.sortNo = intValue(row.get("sortNo"));
            condition.variableCode = stringValue(row.get("variableCode"));
            condition.displayName = firstNonBlank(stringValue(row.get("displayName")), condition.variableCode);
            condition.operatorCode = stringValue(row.get("operatorCode"));
            condition.compareValue = stringValue(row.get("compareValue"));
            snapshot.conditionsByRuleCode.computeIfAbsent(ruleCode, key -> new ArrayList<>()).add(condition);
        }
    }

    private void populateDraftTiers(RuntimeSnapshot snapshot, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String ruleCode = stringValue(row.get("ruleCode"));
            if (!snapshot.rulesByCode.containsKey(ruleCode)) {
                continue;
            }
            RuntimeTier tier = new RuntimeTier();
            tier.tierId = longValue(row.get("tierId"));
            tier.ruleCode = ruleCode;
            tier.tierNo = intValue(row.get("tierNo"));
            tier.startValue = toBigDecimal(row.get("startValue"));
            tier.endValue = toBigDecimal(row.get("endValue"));
            tier.rateValue = toBigDecimal(row.get("rateValue"));
            tier.intervalMode = firstNonBlank(stringValue(row.get("intervalMode")), INTERVAL_LCRO);
            snapshot.tiersByRuleCode.computeIfAbsent(ruleCode, key -> new ArrayList<>()).add(tier);
        }
    }

    private String buildInputTemplateMessage(RuntimeSnapshot snapshot) {
        if (isDraftSnapshot(snapshot)) {
            return "当前场景尚无生效版本，已按现有配置生成输入模板；公式变量无需手工输入，其余变量配置来源路径时生成多级对象，未配置时按变量编码生成平铺字段。";
        }
        return "已按发布快照生成输入模板；公式变量无需手工输入，其余变量配置来源路径时生成多级对象，未配置时按变量编码生成平铺字段。";
    }

    private String buildFeeTemplateMessage(RuntimeSnapshot snapshot, boolean noRule) {
        if (noRule) {
            return isDraftSnapshot(snapshot)
                    ? "当前费用在现有配置下未挂载可用规则，已返回空模板。"
                    : "当前费用在该发布版本下未挂载可用规则，已返回空模板。";
        }
        return isDraftSnapshot(snapshot)
                ? "已按当前配置和费用关联规则生成接入模板；path 会优先使用来源路径，未配置来源路径时回落为变量编码平铺字段。"
                : "已按发布快照和费用关联规则生成接入模板；path 会优先使用来源路径，未配置来源路径时回落为变量编码平铺字段。";
    }

    private boolean isSimulationTaskType(String taskType) {
        return "SIMULATION".equals(taskType) || TASK_TYPE_SIMULATION_BATCH.equals(taskType);
    }

    private boolean isDraftSnapshot(RuntimeSnapshot snapshot) {
        return snapshot != null && SNAPSHOT_SOURCE_DRAFT.equals(snapshot.snapshotSource);
    }

    private List<RuntimeConditionGroup> buildConditionGroups(List<RuntimeCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, List<RuntimeCondition>> grouped = conditions.stream()
                .collect(Collectors.groupingBy(item -> item.groupNo, LinkedHashMap::new, Collectors.toList()));
        List<RuntimeConditionGroup> result = new ArrayList<>();
        for (Map.Entry<Integer, List<RuntimeCondition>> entry : grouped.entrySet()) {
            RuntimeConditionGroup group = new RuntimeConditionGroup();
            group.groupNo = entry.getKey();
            group.conditions = entry.getValue();
            result.add(group);
        }
        result.sort(Comparator.comparingInt(item -> item.groupNo == null ? 1 : item.groupNo));
        return result;
    }

    private Map<String, Object> buildSingleInputTemplate(RuntimeSnapshot snapshot, String taskType, int index,
                                                         String objectDimension) {
        LinkedHashMap<String, Object> template = new LinkedHashMap<>();
        template.put("bizNo", buildTemplateBizNo(taskType, index));
        if (StringUtils.isNotEmpty(objectDimension)) {
            template.put("objectDimension", objectDimension);
        }
        template.put("objectCode", "OBJ-" + PARTITION_FORMAT.format(index));
        template.put("objectName", "示例对象" + index);
        Set<String> populatedPaths = new LinkedHashSet<>();
        for (RuntimeVariable variable : snapshot.variables) {
            if (SOURCE_TYPE_FORMULA.equals(variable.sourceType)) {
                continue;
            }
            String path = resolveTemplatePath(variable);
            if (StringUtils.isEmpty(path) || populatedPaths.contains(path)) {
                continue;
            }
            Object exampleValue = buildTemplateValue(variable, index);
            populatePathValue(template, path, exampleValue);
            populatedPaths.add(path);
        }
        return template;
    }

    private Map<String, Object> buildSelectedInputTemplate(List<RuntimeVariable> variables, String taskType, int index,
                                                           String objectDimension) {
        LinkedHashMap<String, Object> template = new LinkedHashMap<>();
        template.put("bizNo", buildTemplateBizNo(taskType, index));
        if (StringUtils.isNotEmpty(objectDimension)) {
            template.put("objectDimension", objectDimension);
        }
        template.put("objectCode", "OBJ-" + PARTITION_FORMAT.format(index));
        template.put("objectName", "示例对象" + index);
        Set<String> populatedPaths = new LinkedHashSet<>();
        for (RuntimeVariable variable : variables) {
            if (SOURCE_TYPE_FORMULA.equals(variable.sourceType)) {
                continue;
            }
            String path = resolveTemplatePath(variable);
            if (StringUtils.isEmpty(path) || populatedPaths.contains(path)) {
                continue;
            }
            Object exampleValue = buildTemplateValue(variable, index);
            populatePathValue(template, path, exampleValue);
            populatedPaths.add(path);
        }
        return template;
    }

    private String buildTemplateBizNo(String taskType, int index) {
        String prefix;
        if (TASK_TYPE_FORMAL_BATCH.equals(taskType)) {
            prefix = "BATCH";
        } else if (TASK_TYPE_FORMAL_SINGLE.equals(taskType)) {
            prefix = "FORMAL";
        } else {
            prefix = "SIM";
        }
        return prefix + "-" + PARTITION_FORMAT.format(index);
    }

    private Object buildTemplateValue(RuntimeVariable variable, int index) {
        String dataType = StringUtils.isEmpty(variable.dataType) ? "" : variable.dataType.toUpperCase(Locale.ROOT);
        if (DATA_TYPE_NUMBER.equals(dataType)) {
            return BigDecimal.ONE;
        }
        if (DATA_TYPE_BOOLEAN.equals(dataType)) {
            return Boolean.TRUE;
        }
        if (DATA_TYPE_JSON.equals(dataType)) {
            return new LinkedHashMap<>();
        }
        return firstNonBlank(variable.variableName, variable.variableCode) + index;
    }

    private void populatePathValue(Map<String, Object> root, String path, Object value) {
        String[] pieces = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < pieces.length; i++) {
            String piece = pieces[i];
            if (i == pieces.length - 1) {
                current.putIfAbsent(piece, value);
                return;
            }
            Object child = current.get(piece);
            if (!(child instanceof Map)) {
                LinkedHashMap<String, Object> next = new LinkedHashMap<>();
                current.put(piece, next);
                current = next;
                continue;
            }
            current = castMap(child);
        }
    }

    private List<CostCalcTask> selectTaskListInternal(CostCalcTask query) {
        return calcTaskMapper.selectList(Wrappers.<CostCalcTask>lambdaQuery()
                .eq(query.getTaskId() != null, CostCalcTask::getTaskId, query.getTaskId())
                .eq(query.getSceneId() != null, CostCalcTask::getSceneId, query.getSceneId())
                .eq(query.getVersionId() != null, CostCalcTask::getVersionId, query.getVersionId())
                .eq(StringUtils.isNotEmpty(query.getTaskType()), CostCalcTask::getTaskType, query.getTaskType())
                .eq(StringUtils.isNotEmpty(query.getTaskStatus()), CostCalcTask::getTaskStatus, query.getTaskStatus())
                .eq(StringUtils.isNotEmpty(query.getBillMonth()), CostCalcTask::getBillMonth, query.getBillMonth())
                .eq(StringUtils.isNotEmpty(query.getRequestNo()), CostCalcTask::getRequestNo, query.getRequestNo())
                .like(StringUtils.isNotEmpty(query.getTaskNo()), CostCalcTask::getTaskNo, query.getTaskNo())
                .orderByDesc(CostCalcTask::getTaskId));
    }

    private List<CostResultLedger> selectResultListInternal(CostResultLedger query) {
        return selectResultListInternal(query, null);
    }

    private List<CostResultLedger> selectResultListInternal(CostResultLedger query, Integer limit) {
        if (query == null) {
            query = new CostResultLedger();
        }
        List<Long> requestTaskIds = resolveResultRequestTaskIds(query.getRequestNo());
        if (StringUtils.isNotEmpty(query.getRequestNo()) && requestTaskIds.isEmpty()) {
            return Collections.emptyList();
        }
        return resultLedgerMapper.selectList(Wrappers.<CostResultLedger>lambdaQuery()
                .eq(query.getTaskId() != null, CostResultLedger::getTaskId, query.getTaskId())
                .in(requestTaskIds != null && !requestTaskIds.isEmpty(), CostResultLedger::getTaskId, requestTaskIds)
                .eq(query.getSceneId() != null, CostResultLedger::getSceneId, query.getSceneId())
                .eq(query.getVersionId() != null, CostResultLedger::getVersionId, query.getVersionId())
                .eq(query.getFeeId() != null, CostResultLedger::getFeeId, query.getFeeId())
                .eq(StringUtils.isNotEmpty(query.getBillMonth()), CostResultLedger::getBillMonth, query.getBillMonth())
                .eq(StringUtils.isNotEmpty(query.getTaskNo()), CostResultLedger::getTaskNo, query.getTaskNo())
                .eq(StringUtils.isNotEmpty(query.getFeeCode()), CostResultLedger::getFeeCode, query.getFeeCode())
                .eq(StringUtils.isNotEmpty(query.getObjectCode()), CostResultLedger::getObjectCode, query.getObjectCode())
                .like(StringUtils.isNotEmpty(query.getObjectName()), CostResultLedger::getObjectName, query.getObjectName())
                .eq(StringUtils.isNotEmpty(query.getBizNo()), CostResultLedger::getBizNo, query.getBizNo())
                .eq(StringUtils.isNotEmpty(query.getResultStatus()), CostResultLedger::getResultStatus, query.getResultStatus())
                .orderByDesc(CostResultLedger::getResultId)
                .last(limit != null && limit > 0, "limit " + limit));
    }

    private CostResultCompareSource buildResultCompareSource(CostResultCompareBo query, boolean leftSide) {
        String sourceType = firstNonBlank(leftSide ? query.getLeftSourceType() : query.getRightSourceType(), "FORMAL").toUpperCase(Locale.ROOT);
        if ("SIMULATION".equals(sourceType)) {
            return buildSimulationCompareSource(leftSide ? query.getLeftSimulationId() : query.getRightSimulationId());
        }
        return buildFormalCompareSource(
                leftSide ? query.getLeftTaskId() : query.getRightTaskId(),
                leftSide ? query.getLeftSceneId() : query.getRightSceneId(),
                leftSide ? query.getLeftVersionId() : query.getRightVersionId(),
                leftSide ? query.getLeftBillMonth() : query.getRightBillMonth());
    }

    private CostResultCompareSource buildFormalCompareSource(Long taskId, Long sceneId, Long versionId, String billMonth) {
        boolean hasTaskScope = taskId != null;
        boolean hasBusinessScope = StringUtils.isNotEmpty(billMonth) && (sceneId != null || versionId != null);
        if (!hasTaskScope && !hasBusinessScope) {
            throw new ServiceException("正式结果对比请补充任务ID，或选择账期并至少指定场景/版本");
        }
        List<CostResultLedger> results = resultLedgerMapper.selectList(Wrappers.<CostResultLedger>lambdaQuery()
                .eq(taskId != null, CostResultLedger::getTaskId, taskId)
                .eq(sceneId != null, CostResultLedger::getSceneId, sceneId)
                .eq(versionId != null, CostResultLedger::getVersionId, versionId)
                .eq(StringUtils.isNotEmpty(billMonth), CostResultLedger::getBillMonth, billMonth)
                .orderByDesc(CostResultLedger::getResultId)
                .last("limit " + (RESULT_COMPARE_MAX_ROWS + 1)));
        if (results.size() > RESULT_COMPARE_MAX_ROWS) {
            throw new ServiceException("结果对比最多支持 " + RESULT_COMPARE_MAX_ROWS + " 条，请缩小筛选范围后重试");
        }
        enrichResults(results);

        // 批量查询关联追溯记录，用于按规则命中维度聚合
        List<Long> traceIds = results.stream()
                .map(CostResultLedger::getTraceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, CostResultTrace> traceMap = traceIds.isEmpty() ? Collections.emptyMap()
                : resultTraceMapper.selectBatchIds(traceIds).stream()
                        .collect(Collectors.toMap(CostResultTrace::getTraceId, t -> t, (a, b) -> a));

        CostResultCompareSource source = new CostResultCompareSource();
        source.sourceType = "FORMAL";
        source.sourceName = "正式结果";
        source.taskId = taskId;
        source.sceneId = sceneId;
        source.versionId = versionId;
        source.billMonth = billMonth;
        source.resultCount = results.size();
        for (CostResultLedger ledger : results) {
            source.sceneName = firstNonBlank(source.sceneName, ledger.getSceneName());
            source.versionNo = firstNonBlank(source.versionNo, ledger.getVersionNo());
            source.sourceNo = firstNonBlank(source.sourceNo, ledger.getTaskNo());
            source.billMonth = firstNonBlank(source.billMonth, ledger.getBillMonth());
            source.addFee(ledger.getFeeCode(), ledger.getFeeName(), ledger.getAmountValue(), 1L);
            source.addObject(ledger.getObjectDimension(), ledger.getObjectCode(), ledger.getObjectName(), ledger.getAmountValue(), 1L);
            // 按规则命中聚合：从追溯记录中获取 ruleId
            CostResultTrace trace = ledger.getTraceId() != null ? traceMap.get(ledger.getTraceId()) : null;
            Long ruleId = trace != null ? trace.getRuleId() : null;
            source.addRule(ledger.getFeeCode(), ledger.getFeeName(), ruleId, ledger.getAmountValue(), 1L);
        }
        source.amountTotal = source.fees.values().stream()
                .map(item -> item.amountTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return source;
    }

    private CostResultCompareSource buildSimulationCompareSource(Long simulationId) {
        if (simulationId == null) {
            throw new ServiceException("试算结果对比请补充试算ID");
        }
        CostSimulationRecord record = simulationRecordMapper.selectById(simulationId);
        if (record == null) {
            throw new ServiceException("试算记录不存在，请刷新后重试");
        }
        enrichSimulationRecords(Collections.singletonList(record));
        Map<String, Object> result = parseJsonObjectForExport(record.getResultJson());
        List<Map<String, Object>> feeResults = castMapList(result.get("feeResults"));

        CostResultCompareSource source = new CostResultCompareSource();
        source.sourceType = "SIMULATION";
        source.sourceName = "试算结果";
        source.sourceNo = record.getSimulationNo();
        source.simulationId = record.getSimulationId();
        source.sceneId = record.getSceneId();
        source.sceneName = record.getSceneName();
        source.versionId = record.getVersionId();
        source.versionNo = record.getVersionNo();
        source.billMonth = record.getBillMonth();
        for (Map<String, Object> fee : feeResults) {
            String feeCode = stringValue(fee.get("feeCode"));
            String feeName = stringValue(fee.get("feeName"));
            BigDecimal amountValue = toBigDecimal(fee.get("amountValue"));
            source.addFee(feeCode, feeName, amountValue, 1L);
            source.addObject(stringValue(fee.get("objectDimension")), stringValue(fee.get("objectCode")),
                    stringValue(fee.get("objectName")), amountValue, 1L);
            source.addRule(feeCode, feeName, toLongObject(fee.get("ruleId")), amountValue, 1L);
        }
        if (source.fees.isEmpty()) {
            source.addFee("TOTAL", "试算合计", toBigDecimal(result.get("amountTotal")), 1L);
        }
        source.resultCount = feeResults.size();
        source.amountTotal = defaultZero(toBigDecimal(result.get("amountTotal")));
        if (source.amountTotal.compareTo(BigDecimal.ZERO) == 0) {
            source.amountTotal = source.fees.values().stream()
                    .map(item -> item.amountTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        source.amountTotal = source.amountTotal.setScale(2, RoundingMode.HALF_UP);
        return source;
    }

    private List<Map<String, Object>> buildResultCompareRows(CostResultCompareSource left, CostResultCompareSource right) {
        LinkedHashSet<String> feeCodes = new LinkedHashSet<>();
        feeCodes.addAll(left.fees.keySet());
        feeCodes.addAll(right.fees.keySet());
        return feeCodes.stream().map(feeCode -> {
                    CostResultCompareFeeAggregate leftFee = left.fees.get(feeCode);
                    CostResultCompareFeeAggregate rightFee = right.fees.get(feeCode);
                    BigDecimal leftAmount = leftFee == null ? BigDecimal.ZERO : leftFee.amountTotal;
                    BigDecimal rightAmount = rightFee == null ? BigDecimal.ZERO : rightFee.amountTotal;
                    BigDecimal diffAmount = rightAmount.subtract(leftAmount).setScale(2, RoundingMode.HALF_UP);
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("feeCode", feeCode);
                    row.put("feeName", firstNonBlank(leftFee == null ? "" : leftFee.feeName, rightFee == null ? "" : rightFee.feeName));
                    row.put("leftAmount", leftAmount.setScale(2, RoundingMode.HALF_UP));
                    row.put("rightAmount", rightAmount.setScale(2, RoundingMode.HALF_UP));
                    row.put("diffAmount", diffAmount);
                    row.put("leftCount", leftFee == null ? 0L : leftFee.resultCount);
                    row.put("rightCount", rightFee == null ? 0L : rightFee.resultCount);
                    row.put("changeType", resolveResultCompareChangeType(leftFee, rightFee, diffAmount));
                    return row;
                })
                .sorted((leftRow, rightRow) -> {
                    BigDecimal leftDiff = defaultZero(toBigDecimal(leftRow.get("diffAmount"))).abs();
                    BigDecimal rightDiff = defaultZero(toBigDecimal(rightRow.get("diffAmount"))).abs();
                    return rightDiff.compareTo(leftDiff);
                })
                .collect(Collectors.toList());
    }

    private String resolveResultCompareChangeType(CostResultCompareFeeAggregate leftFee, CostResultCompareFeeAggregate rightFee, BigDecimal diffAmount) {
        if (leftFee == null && rightFee != null) {
            return "ADDED";
        }
        if (leftFee != null && rightFee == null) {
            return "REMOVED";
        }
        return diffAmount.compareTo(BigDecimal.ZERO) == 0 ? "UNCHANGED" : "CHANGED";
    }

    /** 按对象维度对比 */
    private List<Map<String, Object>> buildResultCompareRowsByObject(CostResultCompareSource left, CostResultCompareSource right) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(left.objects.keySet());
        keys.addAll(right.objects.keySet());
        return keys.stream().map(key -> {
                    CostResultCompareObjectAggregate leftObj = left.objects.get(key);
                    CostResultCompareObjectAggregate rightObj = right.objects.get(key);
                    BigDecimal leftAmount = leftObj == null ? BigDecimal.ZERO : leftObj.amountTotal;
                    BigDecimal rightAmount = rightObj == null ? BigDecimal.ZERO : rightObj.amountTotal;
                    BigDecimal diffAmount = rightAmount.subtract(leftAmount).setScale(2, RoundingMode.HALF_UP);
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("objectDimension", leftObj == null ? rightObj.objectDimension : leftObj.objectDimension);
                    row.put("objectCode", leftObj == null ? rightObj.objectCode : leftObj.objectCode);
                    row.put("objectName", firstNonBlank(leftObj == null ? "" : leftObj.objectName, rightObj == null ? "" : rightObj.objectName));
                    row.put("leftAmount", leftAmount.setScale(2, RoundingMode.HALF_UP));
                    row.put("rightAmount", rightAmount.setScale(2, RoundingMode.HALF_UP));
                    row.put("diffAmount", diffAmount);
                    row.put("leftCount", leftObj == null ? 0L : leftObj.resultCount);
                    row.put("rightCount", rightObj == null ? 0L : rightObj.resultCount);
                    row.put("changeType", resolveGenericChangeType(leftObj, rightObj, diffAmount));
                    return row;
                })
                .sorted((a, b) -> defaultZero(toBigDecimal(b.get("diffAmount"))).abs()
                        .compareTo(defaultZero(toBigDecimal(a.get("diffAmount"))).abs()))
                .collect(Collectors.toList());
    }

    /** 按规则命中对比 */
    private List<Map<String, Object>> buildResultCompareRowsByRule(CostResultCompareSource left, CostResultCompareSource right) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(left.rules.keySet());
        keys.addAll(right.rules.keySet());
        return keys.stream().map(key -> {
                    CostResultCompareRuleAggregate leftRule = left.rules.get(key);
                    CostResultCompareRuleAggregate rightRule = right.rules.get(key);
                    BigDecimal leftAmount = leftRule == null ? BigDecimal.ZERO : leftRule.amountTotal;
                    BigDecimal rightAmount = rightRule == null ? BigDecimal.ZERO : rightRule.amountTotal;
                    BigDecimal diffAmount = rightAmount.subtract(leftAmount).setScale(2, RoundingMode.HALF_UP);
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("feeCode", leftRule == null ? rightRule.feeCode : leftRule.feeCode);
                    row.put("feeName", firstNonBlank(leftRule == null ? "" : leftRule.feeName, rightRule == null ? "" : rightRule.feeName));
                    row.put("ruleId", leftRule == null ? rightRule.ruleId : leftRule.ruleId);
                    row.put("leftAmount", leftAmount.setScale(2, RoundingMode.HALF_UP));
                    row.put("rightAmount", rightAmount.setScale(2, RoundingMode.HALF_UP));
                    row.put("diffAmount", diffAmount);
                    row.put("leftCount", leftRule == null ? 0L : leftRule.resultCount);
                    row.put("rightCount", rightRule == null ? 0L : rightRule.resultCount);
                    row.put("changeType", resolveGenericChangeType(leftRule, rightRule, diffAmount));
                    return row;
                })
                .sorted((a, b) -> defaultZero(toBigDecimal(b.get("diffAmount"))).abs()
                        .compareTo(defaultZero(toBigDecimal(a.get("diffAmount"))).abs()))
                .collect(Collectors.toList());
    }

    /** 通用变化类型判定：左侧缺失为新增，右侧缺失为移除，金额相同为无变化 */
    private String resolveGenericChangeType(Object leftItem, Object rightItem, BigDecimal diffAmount) {
        if (leftItem == null && rightItem != null) {
            return "ADDED";
        }
        if (leftItem != null && rightItem == null) {
            return "REMOVED";
        }
        return diffAmount.compareTo(BigDecimal.ZERO) == 0 ? "UNCHANGED" : "CHANGED";
    }

    private Map<String, Object> buildResultCompareSummary(CostResultCompareSource left, CostResultCompareSource right, List<Map<String, Object>> rows) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        BigDecimal diffAmount = right.amountTotal.subtract(left.amountTotal).setScale(2, RoundingMode.HALF_UP);
        summary.put("leftAmountTotal", left.amountTotal.setScale(2, RoundingMode.HALF_UP));
        summary.put("rightAmountTotal", right.amountTotal.setScale(2, RoundingMode.HALF_UP));
        summary.put("diffAmount", diffAmount);
        summary.put("leftResultCount", left.resultCount);
        summary.put("rightResultCount", right.resultCount);
        summary.put("addedCount", countCompareRows(rows, "ADDED"));
        summary.put("removedCount", countCompareRows(rows, "REMOVED"));
        summary.put("changedCount", countCompareRows(rows, "CHANGED"));
        summary.put("unchangedCount", countCompareRows(rows, "UNCHANGED"));
        return summary;
    }

    private long countCompareRows(List<Map<String, Object>> rows, String changeType) {
        return rows.stream().filter(row -> changeType.equals(row.get("changeType"))).count();
    }

    private Map<String, Object> buildResultCompareSourceMap(CostResultCompareSource source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", source.sourceType);
        result.put("sourceName", source.sourceName);
        result.put("sourceNo", source.sourceNo);
        result.put("taskId", source.taskId);
        result.put("simulationId", source.simulationId);
        result.put("sceneId", source.sceneId);
        result.put("sceneName", source.sceneName);
        result.put("versionId", source.versionId);
        result.put("versionNo", source.versionNo);
        result.put("billMonth", source.billMonth);
        result.put("resultCount", source.resultCount);
        result.put("amountTotal", source.amountTotal.setScale(2, RoundingMode.HALF_UP));
        return result;
    }

    private List<Long> resolveResultRequestTaskIds(String requestNo) {
        if (StringUtils.isEmpty(requestNo)) {
            return Collections.emptyList();
        }
        return calcTaskMapper.selectList(Wrappers.<CostCalcTask>lambdaQuery()
                        .eq(CostCalcTask::getRequestNo, requestNo)
                        .orderByDesc(CostCalcTask::getTaskId))
                .stream()
                .map(CostCalcTask::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void validateResultQueryScope(CostResultLedger query) {
        if (query == null) {
            throw new ServiceException("结果台账数据量较大，请至少补充账期、任务ID、任务号或业务单号后再查询");
        }
        boolean hasPrimaryScope = query.getTaskId() != null
                || StringUtils.isNotEmpty(query.getTaskNo())
                || StringUtils.isNotEmpty(query.getRequestNo());
        boolean hasBusinessScope = StringUtils.isNotEmpty(query.getBillMonth())
                || StringUtils.isNotEmpty(query.getBizNo());
        if (!hasPrimaryScope && !hasBusinessScope) {
            throw new ServiceException("结果台账数据量较大，请至少补充账期、任务ID、任务号或业务单号后再查询");
        }
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Long toLongObject(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.isNotEmpty((String) value)) {
            try {
                return Long.valueOf((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Map<String, Object>> normalizeAmountRows(List<Map<String, Object>> rows, String amountKey) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(row -> {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(row);
            BigDecimal amount = toBigDecimal(row.get(amountKey));
            normalized.put(amountKey, defaultZero(amount).setScale(2, RoundingMode.HALF_UP));
            normalized.put("resultCount", toLong(row.get("resultCount")));
            if (row.containsKey("objectCount")) {
                normalized.put("objectCount", toLong(row.get("objectCount")));
            }
            return normalized;
        }).collect(Collectors.toList());
    }

    private Map<Long, CostPublishVersion> selectVersionMap(Set<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return publishVersionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(CostPublishVersion::getVersionId, item -> item));
    }

    private void enrichSimulationRecords(List<CostSimulationRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, CostScene> sceneMap = sceneMapper.selectBatchIds(records.stream().map(CostSimulationRecord::getSceneId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        Map<Long, CostPublishVersion> versionMap = selectVersionMap(records.stream()
                .map(CostSimulationRecord::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        for (CostSimulationRecord record : records) {
            CostScene scene = sceneMap.get(record.getSceneId());
            if (scene != null) {
                record.setSceneCode(scene.getSceneCode());
                record.setSceneName(scene.getSceneName());
            }
            CostPublishVersion version = versionMap.get(record.getVersionId());
            if (version != null) {
                record.setVersionNo(version.getVersionNo());
            } else if (record.getVersionId() == null) {
                record.setVersionNo(DRAFT_VERSION_LABEL);
            }
        }
    }

    private void enrichTasks(List<CostCalcTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Map<Long, CostScene> sceneMap = sceneMapper.selectBatchIds(tasks.stream().map(CostCalcTask::getSceneId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        Map<Long, CostPublishVersion> versionMap = selectVersionMap(tasks.stream()
                .map(CostCalcTask::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        for (CostCalcTask task : tasks) {
            CostScene scene = sceneMap.get(task.getSceneId());
            if (scene != null) {
                task.setSceneCode(scene.getSceneCode());
                task.setSceneName(scene.getSceneName());
            }
            CostPublishVersion version = versionMap.get(task.getVersionId());
            if (version != null) {
                task.setVersionNo(version.getVersionNo());
            }
        }
    }

    private TaskExecutionSummary summarizeTaskDetails(Long taskId) {
        List<CostCalcTaskDetail> details = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .select(CostCalcTaskDetail::getDetailStatus)
                .eq(CostCalcTaskDetail::getTaskId, taskId));
        TaskExecutionSummary summary = new TaskExecutionSummary();
        summary.totalCount = details.size();
        for (CostCalcTaskDetail detail : details) {
            if (DETAIL_STATUS_SUCCESS.equals(detail.getDetailStatus())) {
                summary.successCount++;
            } else if (DETAIL_STATUS_FAILED.equals(detail.getDetailStatus())) {
                summary.failedCount++;
            }
        }
        summary.processedCount = summary.successCount + summary.failedCount;
        return summary;
    }

    private PartitionExecutionResult summarizePartitionDetails(Long taskId, Integer partitionNo) {
        List<CostCalcTaskDetail> details = calcTaskDetailMapper.selectList(Wrappers.<CostCalcTaskDetail>lambdaQuery()
                .select(CostCalcTaskDetail::getDetailStatus)
                .eq(CostCalcTaskDetail::getTaskId, taskId)
                .eq(CostCalcTaskDetail::getPartitionNo, partitionNo));
        PartitionExecutionResult summary = new PartitionExecutionResult();
        for (CostCalcTaskDetail detail : details) {
            if (DETAIL_STATUS_SUCCESS.equals(detail.getDetailStatus())) {
                summary.successCount++;
            } else if (DETAIL_STATUS_FAILED.equals(detail.getDetailStatus())) {
                summary.failedCount++;
            }
        }
        summary.processedCount = summary.successCount + summary.failedCount;
        return summary;
    }

    /**
     * 按任务集合一次性加载分片台账，避免任务总览逐个查询分片。
     */
    private List<CostCalcTaskPartition> selectTaskPartitions(List<CostCalcTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> taskIds = tasks.stream().map(CostCalcTask::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Collections.emptyList();
        }
        return calcTaskPartitionMapper.selectList(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .in(CostCalcTaskPartition::getTaskId, taskIds)
                .orderByDesc(CostCalcTaskPartition::getPartitionId));
    }

    /**
     * 构建最近 N 天的任务趋势。
     */
    private List<Map<String, Object>> buildTaskTrend(List<CostCalcTask> tasks, int recentDays) {
        ZoneId zoneId = ZoneId.systemDefault();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<LocalDate, List<CostCalcTask>> grouped = tasks.stream()
                .filter(Objects::nonNull)
                .filter(item -> resolveTaskTrendDate(item) != null)
                .collect(Collectors.groupingBy(item -> resolveTaskTrendDate(item).toInstant().atZone(zoneId).toLocalDate()));
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusDays(Math.max(recentDays - 1L, 0L));
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<CostCalcTask> dayTasks = grouped.getOrDefault(date, List.of());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.format(formatter));
            row.put("count", dayTasks.size());
            row.put("runningCount", dayTasks.stream().filter(item -> TASK_STATUS_RUNNING.equals(item.getTaskStatus())).count());
            row.put("failedCount", dayTasks.stream().filter(item -> isTaskProblematic(item.getTaskStatus())).count());
            result.add(row);
        }
        return result;
    }

    /**
     * 构建最近 N 天的分片趋势。
     */
    private List<Map<String, Object>> buildPartitionTrend(List<CostCalcTaskPartition> partitions, int recentDays) {
        ZoneId zoneId = ZoneId.systemDefault();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<LocalDate, List<CostCalcTaskPartition>> grouped = partitions.stream()
                .filter(Objects::nonNull)
                .filter(item -> resolvePartitionTrendDate(item) != null)
                .collect(Collectors.groupingBy(item -> resolvePartitionTrendDate(item).toInstant().atZone(zoneId).toLocalDate()));
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusDays(Math.max(recentDays - 1L, 0L));
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<CostCalcTaskPartition> dayPartitions = grouped.getOrDefault(date, List.of());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.format(formatter));
            row.put("count", dayPartitions.size());
            row.put("failedCount", dayPartitions.stream().filter(item -> isPartitionProblematic(item)).count());
            row.put("avgDurationMs", dayPartitions.isEmpty() ? 0L : Math.round(dayPartitions.stream()
                                                                               .map(CostCalcTaskPartition::getDurationMs)
                                                                               .filter(Objects::nonNull)
                                                                               .mapToLong(Long::longValue)
                                                                               .average()
                                                                               .orElse(0D)));
            result.add(row);
        }
        return result;
    }

    /**
     * 构建失败优先的高风险任务视图。
     */
    private List<Map<String, Object>> buildTopRiskTasks(List<CostCalcTask> tasks, List<CostCalcTaskPartition> partitions, int limit) {
        Map<Long, List<CostCalcTaskPartition>> partitionMap = partitions.stream()
                .filter(item -> item.getTaskId() != null)
                .collect(Collectors.groupingBy(CostCalcTaskPartition::getTaskId));
        return tasks.stream()
                .filter(item -> item.getTaskId() != null)
                .filter(item -> NumberUtils.toInt(String.valueOf(item.getFailCount()), 0) > 0 || isTaskProblematic(item.getTaskStatus()))
                .sorted(Comparator
                        .comparingInt((CostCalcTask item) -> item.getFailCount() == null ? 0 : item.getFailCount()).reversed()
                        .thenComparing(CostCalcTask::getTaskId, Comparator.reverseOrder()))
                .limit(limit)
                .map(task -> {
                    List<CostCalcTaskPartition> taskPartitions = partitionMap.getOrDefault(task.getTaskId(), List.of());
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("taskId", task.getTaskId());
                    row.put("taskNo", task.getTaskNo());
                    row.put("sceneName", firstNonBlank(task.getSceneName(), "-"));
                    row.put("billMonth", firstNonBlank(task.getBillMonth(), "-"));
                    row.put("taskStatus", firstNonBlank(task.getTaskStatus(), TASK_STATUS_INIT));
                    row.put("failCount", task.getFailCount() == null ? 0 : task.getFailCount());
                    row.put("partitionFailCount", taskPartitions.stream().filter(this::isPartitionProblematic).count());
                    row.put("sourceCount", task.getSourceCount() == null ? 0 : task.getSourceCount());
                    return row;
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建任务状态分布，帮助快速判断任务停留阶段。
     */
    private List<Map<String, Object>> buildTaskStatusDistribution(List<CostCalcTask> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(item -> firstNonBlank(item.getTaskStatus(), TASK_STATUS_INIT), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("taskStatus", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建输入来源分布，区分 JSON 直传与批次导入。
     */
    private List<Map<String, Object>> buildInputSourceDistribution(List<CostCalcTask> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(item -> firstNonBlank(item.getInputSourceType(), "INLINE_JSON"), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("inputSourceType", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildPartitionOwnerSummary(List<CostCalcTaskPartition> partitions) {
        List<CostCalcTaskPartition> safePartitions = partitions == null ? List.of() : partitions;
        LocalDateTime staleThreshold = LocalDateTime.now().minusSeconds(resolveTaskStaleTimeoutSeconds());
        long claimedPartitionCount = safePartitions.stream()
                .filter(item -> StringUtils.isNotEmpty(item.getExecuteNode()))
                .count();
        long activeOwnerCount = safePartitions.stream()
                .map(CostCalcTaskPartition::getExecuteNode)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .count();
        long staleRunningOwnerCount = safePartitions.stream()
                .filter(item -> TASK_STATUS_RUNNING.equals(item.getPartitionStatus()))
                .filter(item -> StringUtils.isNotEmpty(item.getExecuteNode()))
                .filter(item -> item.getClaimTime() != null)
                .filter(item -> item.getClaimTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isBefore(staleThreshold))
                .count();
        long runningWithoutOwnerCount = safePartitions.stream()
                .filter(item -> TASK_STATUS_RUNNING.equals(item.getPartitionStatus()))
                .filter(item -> StringUtils.isEmpty(item.getExecuteNode()))
                .count();
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("claimedPartitionCount", claimedPartitionCount);
        summary.put("activeOwnerCount", activeOwnerCount);
        summary.put("staleRunningOwnerCount", staleRunningOwnerCount);
        summary.put("runningWithoutOwnerCount", runningWithoutOwnerCount);
        return summary;
    }

    private List<Map<String, Object>> buildPartitionOwnerDistribution(List<CostCalcTaskPartition> partitions, int limit) {
        return (partitions == null ? List.<CostCalcTaskPartition>of() : partitions).stream()
                .filter(item -> StringUtils.isNotEmpty(item.getExecuteNode()))
                .collect(Collectors.groupingBy(CostCalcTaskPartition::getExecuteNode))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<CostCalcTaskPartition> ownerPartitions = entry.getValue();
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("executeNode", entry.getKey());
                    row.put("partitionCount", ownerPartitions.size());
                    row.put("runningCount", ownerPartitions.stream()
                            .filter(item -> TASK_STATUS_RUNNING.equals(item.getPartitionStatus()))
                            .count());
                    row.put("problematicCount", ownerPartitions.stream()
                            .filter(this::isPartitionProblematic)
                            .count());
                    row.put("latestClaimTime", ownerPartitions.stream()
                            .map(CostCalcTaskPartition::getClaimTime)
                            .filter(Objects::nonNull)
                            .max(Date::compareTo)
                            .orElse(null));
                    return row;
                })
                .sorted(Comparator
                        .comparingLong((Map<String, Object> item) -> NumberUtils.toLong(String.valueOf(item.get("partitionCount")), 0L)).reversed()
                        .thenComparing(item -> String.valueOf(item.get("executeNode"))))
                .limit(Math.max(limit, 0))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTopOwnerRiskTasks(List<CostCalcTask> tasks, List<CostCalcTaskPartition> partitions, int limit) {
        LocalDateTime staleThreshold = LocalDateTime.now().minusSeconds(resolveTaskStaleTimeoutSeconds());
        Map<Long, List<CostCalcTaskPartition>> partitionMap = (partitions == null ? List.<CostCalcTaskPartition>of() : partitions).stream()
                .filter(item -> item.getTaskId() != null)
                .collect(Collectors.groupingBy(CostCalcTaskPartition::getTaskId));
        return (tasks == null ? List.<CostCalcTask>of() : tasks).stream()
                .filter(item -> item.getTaskId() != null)
                .map(task -> buildOwnerRiskTaskRow(task, partitionMap.getOrDefault(task.getTaskId(), List.of()), staleThreshold))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingLong((Map<String, Object> item) -> NumberUtils.toLong(String.valueOf(item.get("staleRunningOwnerCount")), 0L)).reversed()
                        .thenComparingLong(item -> NumberUtils.toLong(String.valueOf(item.get("runningWithoutOwnerCount")), 0L)).reversed()
                        .thenComparingLong(item -> NumberUtils.toLong(String.valueOf(item.get("activeOwnerCount")), 0L)).reversed()
                        .thenComparing(item -> NumberUtils.toLong(String.valueOf(item.get("taskId")), 0L), Comparator.reverseOrder()))
                .limit(Math.max(limit, 0))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildOwnerRiskTaskRow(CostCalcTask task, List<CostCalcTaskPartition> taskPartitions,
                                                      LocalDateTime staleThreshold) {
        if (task == null || task.getTaskId() == null) {
            return null;
        }
        long staleRunningOwnerCount = taskPartitions.stream()
                .filter(item -> TASK_STATUS_RUNNING.equals(item.getPartitionStatus()))
                .filter(item -> StringUtils.isNotEmpty(item.getExecuteNode()))
                .filter(item -> item.getClaimTime() != null)
                .filter(item -> item.getClaimTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isBefore(staleThreshold))
                .count();
        long runningWithoutOwnerCount = taskPartitions.stream()
                .filter(item -> TASK_STATUS_RUNNING.equals(item.getPartitionStatus()))
                .filter(item -> StringUtils.isEmpty(item.getExecuteNode()))
                .count();
        long activeOwnerCount = taskPartitions.stream()
                .map(CostCalcTaskPartition::getExecuteNode)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .count();
        if (staleRunningOwnerCount <= 0 && runningWithoutOwnerCount <= 0) {
            return null;
        }
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", task.getTaskId());
        row.put("taskNo", task.getTaskNo());
        row.put("sceneName", firstNonBlank(task.getSceneName(), "-"));
        row.put("billMonth", firstNonBlank(task.getBillMonth(), "-"));
        row.put("taskStatus", firstNonBlank(task.getTaskStatus(), TASK_STATUS_INIT));
        row.put("staleRunningOwnerCount", staleRunningOwnerCount);
        row.put("runningWithoutOwnerCount", runningWithoutOwnerCount);
        row.put("activeOwnerCount", activeOwnerCount);
        return row;
    }

    private Date resolveTaskTrendDate(CostCalcTask task) {
        return task.getStartedTime() != null ? task.getStartedTime() : task.getCreateTime();
    }

    private Date resolvePartitionTrendDate(CostCalcTaskPartition partition) {
        return partition.getStartedTime() != null ? partition.getStartedTime() : partition.getCreateTime();
    }

    private boolean isTaskProblematic(String taskStatus) {
        return TASK_STATUS_FAILED.equals(taskStatus) || TASK_STATUS_PART_SUCCESS.equals(taskStatus) || TASK_STATUS_CANCELLED.equals(taskStatus);
    }

    private boolean isPartitionProblematic(CostCalcTaskPartition partition) {
        return partition != null
                && (TASK_STATUS_FAILED.equals(partition.getPartitionStatus())
                || TASK_STATUS_PART_SUCCESS.equals(partition.getPartitionStatus())
                || (partition.getFailCount() != null && partition.getFailCount() > 0));
    }

    private void enrichInputBatches(List<CostCalcInputBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return;
        }
        List<CostCalcInputBatch> filtered = batches.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return;
        }
        Map<Long, CostScene> sceneMap = sceneMapper.selectBatchIds(filtered.stream().map(CostCalcInputBatch::getSceneId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        Map<Long, CostPublishVersion> versionMap = selectVersionMap(filtered.stream()
                .map(CostCalcInputBatch::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        for (CostCalcInputBatch batch : filtered) {
            CostScene scene = sceneMap.get(batch.getSceneId());
            if (scene != null) {
                batch.setSceneCode(scene.getSceneCode());
                batch.setSceneName(scene.getSceneName());
            }
            CostPublishVersion version = versionMap.get(batch.getVersionId());
            if (version != null) {
                batch.setVersionNo(version.getVersionNo());
            }
        }
    }

    private void enrichResults(List<CostResultLedger> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        Map<Long, CostScene> sceneMap = sceneMapper.selectBatchIds(results.stream().map(CostResultLedger::getSceneId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CostScene::getSceneId, item -> item));
        Map<Long, CostPublishVersion> versionMap = selectVersionMap(results.stream()
                .map(CostResultLedger::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, CostFeeItem> feeMap = feeMapper.selectBatchIds(results.stream().map(CostResultLedger::getFeeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CostFeeItem::getFeeId, item -> item));
        Map<Long, CostResultTrace> traceMap = resultTraceMapper.selectBatchIds(results.stream().map(CostResultLedger::getTraceId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CostResultTrace::getTraceId, item -> item));
        for (CostResultLedger result : results) {
            CostScene scene = sceneMap.get(result.getSceneId());
            if (scene != null) {
                result.setSceneCode(scene.getSceneCode());
                result.setSceneName(scene.getSceneName());
            }
            CostPublishVersion version = versionMap.get(result.getVersionId());
            if (version != null) {
                result.setVersionNo(version.getVersionNo());
            }
            CostFeeItem fee = feeMap.get(result.getFeeId());
            if (fee != null) {
                result.setUnitCode(fee.getUnitCode());
            }
            CostResultTrace trace = traceMap.get(result.getTraceId());
            if (trace != null) {
                Map<String, Object> pricing = parseJsonMap(trace.getPricingJson());
                result.setMatchedGroupNo(intValue(pricing.get("matchedGroupNo")));
                result.setPricingMode(stringValue(pricing.get("pricingMode")));
                result.setPricingSource(stringValue(pricing.get("pricingSource")));
            }
        }
    }

    private void appendBillPeriodPrecheck(Long sceneId, Long versionId, String billMonth,
                                          List<Map<String, Object>> blockingItems,
                                          List<Map<String, Object>> warningItems) {
        CostBillPeriod period = billPeriodMapper.selectOne(Wrappers.<CostBillPeriod>lambdaQuery()
                .eq(CostBillPeriod::getSceneId, sceneId)
                .eq(CostBillPeriod::getBillMonth, billMonth)
                .last("limit 1"));
        if (period == null) {
            warningItems.add(buildTaskPrecheckItem("WARNING", "BILL_PERIOD_NEW", "账期将自动创建",
                    "当前账期尚未初始化，提交后会自动创建账期并进入核算中。"));
            return;
        }
        if (PERIOD_STATUS_SEALED.equals(period.getPeriodStatus())) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "BILL_PERIOD_SEALED", "账期已封存",
                    "当前账期已封存，禁止提交正式核算任务。"));
        }
        if (period.getActiveVersionId() != null && versionId != null && !Objects.equals(period.getActiveVersionId(), versionId)) {
            warningItems.add(buildTaskPrecheckItem("WARNING", "BILL_PERIOD_VERSION_CHANGED", "账期版本将更新",
                    "账期当前版本与本次执行版本不同，提交后会同步为本次执行版本。"));
        }
    }

    private void appendInputBatchPrecheck(CostCalcTaskSubmitBo bo, RuntimeSnapshot snapshot,
                                          List<Map<String, Object>> blockingItems,
                                          List<Map<String, Object>> warningItems) {
        if (StringUtils.isEmpty(bo.getSourceBatchNo())) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_BATCH_EMPTY", "缺少导入批次",
                    "导入批次模式下必须选择或填写批次号。"));
            return;
        }
        CostCalcInputBatch batch = selectInputBatchByNo(bo.getSourceBatchNo());
        if (batch == null) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_BATCH_NOT_FOUND", "导入批次不存在",
                    "未找到对应导入批次，请刷新或重新选择批次。"));
            return;
        }
        if (!Objects.equals(batch.getSceneId(), bo.getSceneId())) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_BATCH_SCENE_MISMATCH", "批次场景不匹配",
                    "导入批次所属场景与当前运行场景不一致。"));
        }
        if (StringUtils.isNotEmpty(bo.getBillMonth()) && !Objects.equals(batch.getBillMonth(), bo.getBillMonth())) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_BATCH_BILL_MONTH_MISMATCH", "批次账期不匹配",
                    "导入批次账期与当前任务账期不一致。"));
        }
        if (snapshot != null && batch.getVersionId() != null && !Objects.equals(batch.getVersionId(), snapshot.versionId)) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_BATCH_VERSION_MISMATCH", "批次版本不匹配",
                    "导入批次版本与本次执行版本不一致，请重新选择批次或调整执行版本。"));
        }
        if (batch.getTotalCount() == null || batch.getTotalCount() <= 0) {
            blockingItems.add(buildTaskPrecheckItem("BLOCKING", "INPUT_BATCH_EMPTY_ITEMS", "批次无有效明细",
                    "导入批次没有可用于正式核算的输入明细。"));
        }
        if (batch.getErrorCount() != null && batch.getErrorCount() > 0) {
            warningItems.add(buildTaskPrecheckItem("WARNING", "INPUT_BATCH_HAS_ERRORS", "批次存在错误记录",
                    "导入批次包含错误记录，建议先治理后再提交正式核算。"));
        }
        if (StringUtils.isNotEmpty(batch.getBatchStatus()) && !INPUT_BATCH_STATUS_READY.equals(batch.getBatchStatus())) {
            warningItems.add(buildTaskPrecheckItem("WARNING", "INPUT_BATCH_STATUS", "批次状态需确认",
                    "导入批次当前状态为 " + batch.getBatchStatus() + "，请确认是否继续引用。"));
        }
    }

    private boolean canParseTaskInputForPrecheck(CostCalcTaskSubmitBo bo, List<Map<String, Object>> blockingItems) {
        if (bo == null || bo.getSceneId() == null || StringUtils.isEmpty(bo.getTaskType()) || StringUtils.isEmpty(bo.getBillMonth())) {
            return false;
        }
        boolean hasInputBlocking = blockingItems.stream()
                .map(item -> stringValue(item.get("code")))
                .anyMatch(code -> code.startsWith("INPUT_BATCH") || "INPUT_JSON_EMPTY".equals(code));
        return !hasInputBlocking;
    }

    private void appendVariableCompletenessPrecheck(RuntimeSnapshot snapshot, List<Map<String, Object>> inputs,
                                                    List<Map<String, Object>> blockingItems) {
        List<RuntimeVariable> requiredVariables = snapshot.variables.stream()
                .filter(item -> !SOURCE_TYPE_FORMULA.equals(item.sourceType))
                .filter(item -> StringUtils.isNotEmpty(resolveTemplatePath(item)))
                .filter(item -> !hasUsableDefaultValue(item.defaultValue))
                .collect(Collectors.toList());
        if (requiredVariables.isEmpty()) {
            return;
        }
        Map<String, Integer> missingCounts = new LinkedHashMap<>();
        List<String> samples = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            Map<String, Object> input = inputs.get(i);
            for (RuntimeVariable variable : requiredVariables) {
                String path = resolveTemplatePath(variable);
                if (!isMissingInputValue(resolvePathValue(input, path))) {
                    continue;
                }
                missingCounts.merge(variable.variableCode, 1, Integer::sum);
                if (samples.size() < 8) {
                    samples.add("第 " + (i + 1) + " 条缺少 " + firstNonBlank(variable.variableName, variable.variableCode)
                            + "(" + path + ")");
                }
            }
        }
        if (missingCounts.isEmpty()) {
            return;
        }
        String summary = missingCounts.entrySet().stream()
                .limit(8)
                .map(item -> item.getKey() + " 缺失 " + item.getValue() + " 条")
                .collect(Collectors.joining("；"));
        if (missingCounts.size() > 8) {
            summary = summary + "；等 " + missingCounts.size() + " 个变量";
        }
        blockingItems.add(buildTaskPrecheckItem("BLOCKING", "VARIABLE_INPUT_MISSING", "输入变量不完整",
                "存在必填变量缺失：" + summary + "。样例：" + String.join("；", samples)));
    }

    private Map<String, Object> buildTaskPrecheckItem(String level, String code, String title, String description) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("code", code);
        item.put("title", title);
        item.put("description", description);
        return item;
    }

    private CostCalcInputBatch selectInputBatchByNo(String batchNo) {
        if (StringUtils.isEmpty(batchNo)) {
            return null;
        }
        return calcInputBatchMapper.selectOne(Wrappers.<CostCalcInputBatch>lambdaQuery()
                .eq(CostCalcInputBatch::getBatchNo, batchNo)
                .last("limit 1"));
    }

    private void validateInputBatchMatchesSnapshot(CostCalcTaskSubmitBo bo, RuntimeSnapshot snapshot) {
        if (!INPUT_SOURCE_BATCH.equals(resolveInputSourceType(bo))) {
            return;
        }
        CostCalcInputBatch batch = selectInputBatchByNo(bo.getSourceBatchNo());
        if (batch != null && batch.getVersionId() != null && snapshot != null
                && !Objects.equals(batch.getVersionId(), snapshot.versionId)) {
            throw new ServiceException("来源导入批次与当前执行版本不匹配，请重新选择批次或调整执行版本");
        }
    }

    private boolean hasUsableDefaultValue(Object value) {
        return value != null && StringUtils.isNotEmpty(String.valueOf(value));
    }

    private Object resolvePathValue(Map<String, Object> input, String path) {
        if (input == null || StringUtils.isEmpty(path)) {
            return null;
        }
        Object current = input;
        for (String piece : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = castMap(current).get(piece);
        }
        return current;
    }

    private boolean isMissingInputValue(Object value) {
        return value == null || (value instanceof String && StringUtils.isEmpty((String) value));
    }

    private List<Map<String, Object>> parseTaskInput(CostCalcTaskSubmitBo bo) {
        if (INPUT_SOURCE_BATCH.equals(resolveInputSourceType(bo))) {
            return loadInputBatchItems(bo);
        }
        if (TASK_TYPE_FORMAL_SINGLE.equals(bo.getTaskType())) {
            return Collections.singletonList(parseObjectJson(bo.getInputJson(), "单笔正式核算输入必须是 JSON 对象"));
        }
        if (TASK_TYPE_FORMAL_BATCH.equals(bo.getTaskType())) {
            List<Map<String, Object>> inputs = parseArrayJson(bo.getInputJson(), "批量任务输入必须是 JSON 数组");
            if (inputs.isEmpty()) {
                throw new ServiceException("批量任务输入不能为空数组");
            }
            validateDuplicateBizNo(inputs);
            return inputs;
        }
        throw new ServiceException("暂不支持的任务类型: " + bo.getTaskType());
    }

    private String resolveInputSourceType(CostCalcTaskSubmitBo bo) {
        if (StringUtils.isNotEmpty(bo.getInputSourceType())) {
            return bo.getInputSourceType().trim().toUpperCase(Locale.ROOT);
        }
        return StringUtils.isNotEmpty(bo.getSourceBatchNo()) ? INPUT_SOURCE_BATCH : INPUT_SOURCE_INLINE_JSON;
    }

    private List<Map<String, Object>> loadInputBatchItems(CostCalcTaskSubmitBo bo) {
        if (StringUtils.isEmpty(bo.getSourceBatchNo())) {
            throw new ServiceException("批次导入任务缺少来源批次号");
        }
        CostCalcInputBatch batch = calcInputBatchMapper.selectOne(Wrappers.<CostCalcInputBatch>lambdaQuery()
                .eq(CostCalcInputBatch::getBatchNo, bo.getSourceBatchNo())
                .last("limit 1"));
        if (batch == null) {
            throw new ServiceException("来源输入批次不存在，请刷新后重试");
        }
        if (!Objects.equals(batch.getSceneId(), bo.getSceneId())) {
            throw new ServiceException("来源输入批次与当前场景不匹配");
        }
        if (StringUtils.isNotEmpty(bo.getBillMonth()) && !Objects.equals(batch.getBillMonth(), bo.getBillMonth())) {
            throw new ServiceException("来源输入批次与当前账期不匹配");
        }
        List<CostCalcInputBatchItem> items = calcInputBatchItemMapper.selectList(Wrappers.<CostCalcInputBatchItem>lambdaQuery()
                .eq(CostCalcInputBatchItem::getBatchId, batch.getBatchId())
                .orderByAsc(CostCalcInputBatchItem::getItemNo)
                .orderByAsc(CostCalcInputBatchItem::getItemId));
        if (items.isEmpty()) {
            throw new ServiceException("来源输入批次暂无可用明细");
        }
        List<Map<String, Object>> inputs = new ArrayList<>();
        for (CostCalcInputBatchItem item : items) {
            inputs.add(parseObjectJson(item.getInputJson(), "批次明细必须是 JSON 对象"));
        }
        validateDuplicateBizNo(inputs);
        return inputs;
    }

    private void markInputBatchSubmitted(String sourceBatchNo, String operator) {
        if (StringUtils.isEmpty(sourceBatchNo)) {
            return;
        }
        calcInputBatchMapper.update(null, Wrappers.<CostCalcInputBatch>lambdaUpdate()
                .eq(CostCalcInputBatch::getBatchNo, sourceBatchNo)
                .set(CostCalcInputBatch::getBatchStatus, INPUT_BATCH_STATUS_SUBMITTED)
                .set(CostCalcInputBatch::getUpdateBy, operator)
                .set(CostCalcInputBatch::getUpdateTime, DateUtils.getNowDate()));
    }

    private void validateDuplicateBizNo(List<Map<String, Object>> inputs) {
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            String bizNo = resolveBizNo(inputs.get(i), i + 1);
            if (!seen.add(bizNo)) {
                throw new ServiceException("批量任务存在重复业务单号：" + bizNo);
            }
        }
    }

    private List<Map<String, Object>> parseInlineCalculationInputs(String inputJson) {
        Object parsed = parseJsonToObject(inputJson);
        if (parsed instanceof Map) {
            return Collections.singletonList(castMap(parsed));
        }
        if (parsed instanceof List) {
            List<Map<String, Object>> inputs = parseArrayJson(inputJson, "费用计算输入必须是 JSON 对象或对象数组");
            if (inputs.isEmpty()) {
                throw new ServiceException("费用计算输入不能为空数组");
            }
            validateDuplicateBizNo(inputs);
            return inputs;
        }
        throw new ServiceException("费用计算输入必须是 JSON 对象或对象数组");
    }

    private void validateBillMonth(String billMonth) {
        if (!billMonth.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException("账期格式必须为 yyyy-MM");
        }
    }

    private Map<String, Object> buildFeeCalculationRecord(Map<String, Object> input, RuntimeFee fee,
                                                          ExecutionResult executionResult, int index, boolean includeExplain, long durationMs) {
        FeeExecutionResult feeResult = findFeeExecutionResult(executionResult, fee.feeCode);
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        String bizNo = resolveBizNo(input, index);
        item.put("recordIndex", index);
        item.put("bizNo", bizNo);
        item.put("objectCode", firstNonBlank(resolveString(input, "objectCode", "object_code"), bizNo));
        item.put("objectName", resolveString(input, "objectName", "object_name", "name"));
        item.put("feeCode", fee.feeCode);
        item.put("feeName", fee.feeName);
        item.put("status", feeResult == null ? "NO_MATCH" : "SUCCESS");
        item.put("matched", feeResult != null);
        item.put("ruleCode", feeResult == null ? "" : feeResult.ruleCode);
        item.put("ruleName", feeResult == null ? "" : feeResult.ruleName);
        item.put("quantityValue", feeResult == null ? null : feeResult.quantityValue);
        item.put("unitPrice", feeResult == null ? null : feeResult.unitPrice);
        item.put("amountValue", feeResult == null ? null : feeResult.amountValue);
        item.put("matchedGroupNo", feeResult == null ? null : feeResult.pricingExplain.get("matchedGroupNo"));
        item.put("pricingMode", feeResult == null ? null : feeResult.pricingExplain.get("pricingMode"));
        item.put("pricingSource", feeResult == null ? null : feeResult.pricingExplain.get("pricingSource"));
        item.put("tierNo", feeResult == null ? null : feeResult.pricingExplain.get("tierNo"));
        item.put("tierRange", feeResult == null ? null : feeResult.pricingExplain.get("tierRange"));
        item.put("durationMs", durationMs);
        item.put("errorMessage", "");
        if (includeExplain) {
            item.put("explain", feeResult == null
                    ? executionResult.skippedFeeExplains.getOrDefault(fee.feeCode,
                    buildFeeNoMatchExplain(fee, Collections.emptyList(), executionResult.variableView, Collections.emptyList()))
                    : buildFeeCalculationExplain(feeResult));
        }
        return item;
    }

    private FeeExecutionResult findFeeExecutionResult(ExecutionResult executionResult, String feeCode) {
        if (executionResult == null || executionResult.feeResults == null || StringUtils.isEmpty(feeCode)) {
            return null;
        }
        return executionResult.feeResults.stream()
                .filter(item -> StringUtils.equals(item.feeCode, feeCode))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildFeeCalculationFailureRecord(Map<String, Object> input, RuntimeFee fee,
                                                                 int index, Exception exception, long durationMs, boolean includeExplain) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        String bizNo = resolveBizNo(input, index);
        item.put("recordIndex", index);
        item.put("bizNo", bizNo);
        item.put("objectCode", firstNonBlank(resolveString(input, "objectCode", "object_code"), bizNo));
        item.put("objectName", resolveString(input, "objectName", "object_name", "name"));
        item.put("feeCode", fee.feeCode);
        item.put("feeName", fee.feeName);
        item.put("status", "FAILED");
        item.put("matched", false);
        item.put("ruleCode", "");
        item.put("ruleName", "");
        item.put("quantityValue", null);
        item.put("unitPrice", null);
        item.put("amountValue", null);
        item.put("matchedGroupNo", null);
        item.put("pricingMode", null);
        item.put("pricingSource", null);
        item.put("tierNo", null);
        item.put("tierRange", null);
        item.put("durationMs", durationMs);
        item.put("errorMessage", limitLength(exception.getMessage(), 1000));
        if (includeExplain) {
            item.put("explain", buildFeeFailureExplain(fee, exception));
        }
        return item;
    }

    private Map<String, Object> buildFeeCalculationExplain(FeeExecutionResult feeResult) {
        return feeExecutionViewAssembler.buildFeeCalculationExplain(feeResult);
    }

    private Map<String, Object> buildFeeNoMatchExplain(RuntimeFee fee, List<RuntimeRule> rules,
                                                       Map<String, Object> variableValues, List<Map<String, Object>> ruleEvaluations) {
        return costNodeExecutor.buildFeeNoMatchExplain(fee, rules, variableValues, ruleEvaluations);
    }

    private Map<String, Object> buildFeeFailureExplain(RuntimeFee fee, Exception exception) {
        return costNodeExecutor.buildFeeFailureExplain(fee, exception);
    }

    private String buildDetailSummary(List<CostResultLedger> ledgers) {
        if (ledgers.isEmpty()) {
            return "未命中任何费用规则";
        }
        BigDecimal total = ledgers.stream().map(CostResultLedger::getAmountValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format(Locale.ROOT, "共命中 %d 条费用结果，累计金额 %s", ledgers.size(), total.setScale(2, RoundingMode.HALF_UP));
    }

    private List<CostCalcTaskDetail> buildTaskDetails(CostCalcTask task, List<Map<String, Object>> inputs) {
        List<CostCalcTaskDetail> details = new ArrayList<>();
        int partitionSize = resolveTaskPartitionSize(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            Map<String, Object> input = inputs.get(i);
            CostCalcTaskDetail detail = new CostCalcTaskDetail();
            detail.setTaskId(task.getTaskId());
            detail.setTaskNo(task.getTaskNo());
            detail.setBizNo(resolveBizNo(input, i + 1));
            detail.setPartitionNo(i / partitionSize + 1);
            detail.setDetailStatus(DETAIL_STATUS_INIT);
            detail.setRetryCount(0);
            detail.setInputJson(writeJson(input));
            detail.setResultSummary("");
            detail.setErrorMessage("");
            details.add(detail);
        }
        return details;
    }

    /**
     * 根据任务明细生成分片台账，为分片级监控、重试和失败定位提供基础。
     */
    private List<CostCalcTaskPartition> buildTaskPartitions(CostCalcTask task, List<CostCalcTaskDetail> details) {
        List<CostCalcTaskPartition> partitions = new ArrayList<>();
        List<List<CostCalcTaskDetail>> grouped = splitTaskPartitions(details);
        for (List<CostCalcTaskDetail> partitionDetails : grouped) {
            if (partitionDetails.isEmpty()) {
                continue;
            }
            CostCalcTaskPartition partition = new CostCalcTaskPartition();
            partition.setTaskId(task.getTaskId());
            partition.setTaskNo(task.getTaskNo());
            partition.setPartitionNo(partitionDetails.get(0).getPartitionNo());
            partition.setStartItemNo(resolvePartitionStartItemNo(partition.getPartitionNo()));
            partition.setEndItemNo(resolvePartitionEndItemNo(partition.getPartitionNo(), partitionDetails.size()));
            partition.setPartitionStatus(DETAIL_STATUS_INIT);
            partition.setTotalCount(partitionDetails.size());
            partition.setProcessedCount(0);
            partition.setSuccessCount(0);
            partition.setFailCount(0);
            partition.setAmountTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            partition.setPersistMode(PARTITION_PERSIST_MODE_BATCH);
            partition.setRecoveryHint("");
            partition.setLastErrorStage("");
            partition.setLastError("");
            partitions.add(partition);
        }
        return partitions;
    }

    private void insertTaskDetailsInChunks(List<CostCalcTaskDetail> details) {
        insertInChunks(details, DEFAULT_TASK_DETAIL_INSERT_SIZE, calcTaskDetailMapper::insertBatch);
    }

    private void insertTaskPartitionsInChunks(List<CostCalcTaskPartition> partitions) {
        insertInChunks(partitions, DEFAULT_TASK_PARTITION_INSERT_SIZE, calcTaskPartitionMapper::insertBatch);
    }

    private <T> void insertInChunks(List<T> items, int chunkSize, java.util.function.ToIntFunction<List<T>> inserter) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int safeChunkSize = Math.max(1, chunkSize);
        for (int start = 0; start < items.size(); start += safeChunkSize) {
            int end = Math.min(start + safeChunkSize, items.size());
            inserter.applyAsInt(items.subList(start, end));
        }
    }

    private List<List<CostCalcTaskDetail>> splitTaskPartitions(List<CostCalcTaskDetail> details) {
        return new ArrayList<>(details.stream().collect(Collectors.groupingBy(
                CostCalcTaskDetail::getPartitionNo,
                LinkedHashMap::new,
                Collectors.toList())).values());
    }

    private int resolveTaskParallelism(int partitionCount) {
        int poolSize = threadPoolTaskExecutor.getCorePoolSize() > 0
                ? threadPoolTaskExecutor.getCorePoolSize() : DEFAULT_TASK_PARALLELISM;
        return Math.max(1, Math.min(Math.min(poolSize, DEFAULT_TASK_PARALLELISM), partitionCount));
    }

    private int resolveTaskPartitionSize(int inputSize) {
        return inputSize <= 0 ? DEFAULT_TASK_PARTITION_SIZE : DEFAULT_TASK_PARTITION_SIZE;
    }

    private Map<String, Object> buildInputBatchLoadingGuide(int itemTotal) {
        int safeTotal = Math.max(itemTotal, 0);
        int partitionCount = Math.max(1, (int) Math.ceil(safeTotal / (double) DEFAULT_TASK_PARTITION_SIZE));
        LinkedHashMap<String, Object> guide = new LinkedHashMap<>();
        guide.put("partitionSize", DEFAULT_TASK_PARTITION_SIZE);
        guide.put("estimatedPartitionCount", partitionCount);
        if (safeTotal <= 0) {
            guide.put("type", "info");
            guide.put("title", "当前批次暂无有效输入");
            guide.put("description", "请先检查导入内容或重新生成批次，再进入正式核算。");
            return guide;
        }
        if (safeTotal <= 50) {
            guide.put("type", "info");
            guide.put("title", String.format(Locale.ROOT, "当前批次共 %d 条，预计拆成 %d 个分片", safeTotal, partitionCount));
            guide.put("description", "当前规模适合联调和小批量复核；如果只是验证少量样例，也可以回到任务中心使用 JSON 直传。");
            return guide;
        }
        if (safeTotal <= DEFAULT_TASK_PARTITION_SIZE) {
            guide.put("type", "success");
            guide.put("title", String.format(Locale.ROOT, "当前批次共 %d 条，预计拆成 %d 个分片", safeTotal, partitionCount));
            guide.put("description", "当前规模适合按企业级批次流程直接提交正式核算，可保留装载台账、分片进度和失败恢复能力。");
            return guide;
        }
        guide.put("type", "warning");
        guide.put("title", String.format(Locale.ROOT, "当前批次共 %d 条，预计拆成 %d 个分片", safeTotal, partitionCount));
        guide.put("description", "当前已属于大批量任务，请继续使用导入批次提交流程，不建议回退为 JSON 直传，以免丢失分页预览、装载台账和恢复治理能力。");
        return guide;
    }

    private int insertInputBatchItems(CostCalcInputBatch batch, String inputJson) {
        if (batch == null || batch.getBatchId() == null) {
            throw new ServiceException("导入批次不存在，无法写入批次明细");
        }
        String trimmed = StringUtils.trimToEmpty(inputJson);
        if (StringUtils.isEmpty(trimmed)) {
            throw new ServiceException("导入批次输入不能为空数组");
        }
        Set<String> bizNoSet = new LinkedHashSet<>();
        List<CostCalcInputBatchItem> buffer = new ArrayList<>(DEFAULT_INPUT_BATCH_INSERT_SIZE);
        int itemNo = 0;
        try (JsonParser parser = objectMapper.getFactory().createParser(trimmed)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken != JsonToken.START_ARRAY) {
                throw new ServiceException("导入批次输入必须是 JSON 数组");
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    throw new ServiceException("导入批次输入必须是 JSON 对象数组");
                }
                JsonNode node = objectMapper.readTree(parser);
                if (node == null || !node.isObject()) {
                    throw new ServiceException("导入批次输入必须是 JSON 对象数组");
                }
                Map<String, Object> input = objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                itemNo++;
                String bizNo = resolveBizNo(input, itemNo);
                if (!bizNoSet.add(bizNo)) {
                    throw new ServiceException("导入批次输入存在重复业务单号: " + bizNo);
                }
                CostCalcInputBatchItem item = new CostCalcInputBatchItem();
                item.setBatchId(batch.getBatchId());
                item.setBatchNo(batch.getBatchNo());
                item.setItemNo(itemNo);
                item.setBizNo(bizNo);
                item.setItemStatus(INPUT_BATCH_STATUS_READY);
                item.setInputJson(writeJson(input));
                item.setErrorMessage("");
                buffer.add(item);
                if (buffer.size() >= DEFAULT_INPUT_BATCH_INSERT_SIZE) {
                    calcInputBatchItemMapper.insertBatch(buffer);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                calcInputBatchItemMapper.insertBatch(buffer);
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ServiceException("导入批次输入必须是 JSON 数组");
        }
        if (itemNo <= 0) {
            throw new ServiceException("导入批次输入不能为空数组");
        }
        return itemNo;
    }

    private int resolvePartitionStartItemNo(Integer partitionNo) {
        int safePartitionNo = partitionNo == null || partitionNo <= 0 ? 1 : partitionNo;
        return (safePartitionNo - 1) * DEFAULT_TASK_PARTITION_SIZE + 1;
    }

    private int resolvePartitionEndItemNo(Integer partitionNo, int partitionItemCount) {
        return resolvePartitionStartItemNo(partitionNo) + Math.max(partitionItemCount, 1) - 1;
    }

    private boolean isTaskCancelled(Long taskId) {
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        return task == null || TASK_STATUS_CANCELLED.equals(task.getTaskStatus());
    }

    /**
     * 分片进入执行前先落运行态，便于任务中心观察分片实时进度。
     */
    private PartitionClaimToken markPartitionRunning(Long taskId, List<CostCalcTaskDetail> partitionDetails) {
        if (partitionDetails == null || partitionDetails.isEmpty()) {
            return null;
        }
        Integer partitionNo = partitionDetails.get(0).getPartitionNo();
        String executeNode = resolveExecuteNode();
        Date claimTime = normalizePartitionClaimTime(DateUtils.getNowDate());
        if (!tryClaimPartitionExecution(taskId, partitionNo, claimTime)) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "分片已被其他执行器认领，taskId=%d, partitionNo=%d", taskId, partitionNo));
        }
        return new PartitionClaimToken(taskId, partitionNo, executeNode, claimTime);
    }

    private boolean tryClaimPartitionExecution(Long taskId, Integer partitionNo, Date claimTime) {
        if (taskId == null || partitionNo == null) {
            return false;
        }
        Date claimedAt = normalizePartitionClaimTime(claimTime == null ? DateUtils.getNowDate() : claimTime);
        String executeNode = resolveExecuteNode();
        int updated = calcTaskPartitionMapper.update(null, Wrappers.<CostCalcTaskPartition>lambdaUpdate()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .eq(CostCalcTaskPartition::getPartitionNo, partitionNo)
                .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_INIT)
                .set(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_RUNNING)
                .set(CostCalcTaskPartition::getExecuteNode, executeNode)
                .set(CostCalcTaskPartition::getClaimTime, claimedAt)
                .set(CostCalcTaskPartition::getStartedTime, claimedAt)
                .set(CostCalcTaskPartition::getPersistMode, PARTITION_PERSIST_MODE_BATCH)
                .set(CostCalcTaskPartition::getRecoveryHint, "")
                .set(CostCalcTaskPartition::getLastErrorStage, "")
                .set(CostCalcTaskPartition::getLastError, "")
                .set(CostCalcTaskPartition::getUpdateTime, claimedAt));
        return updated > 0;
    }

    private boolean isPartitionClaimOwned(PartitionClaimToken claimToken) {
        if (claimToken == null || claimToken.taskId == null || claimToken.partitionNo == null
                || StringUtils.isEmpty(claimToken.executeNode) || claimToken.claimTime == null) {
            return false;
        }
        return calcTaskPartitionMapper.selectCount(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .eq(CostCalcTaskPartition::getTaskId, claimToken.taskId)
                .eq(CostCalcTaskPartition::getPartitionNo, claimToken.partitionNo)
                .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_RUNNING)
                .eq(CostCalcTaskPartition::getExecuteNode, claimToken.executeNode)
                .eq(CostCalcTaskPartition::getClaimTime, claimToken.claimTime)) > 0;
    }

    private boolean touchPartitionHeartbeat(Long taskId, PartitionClaimToken claimToken) {
        if (taskId == null || claimToken == null || !isPartitionClaimOwned(claimToken)) {
            return false;
        }
        Date heartbeatTime = DateUtils.getNowDate();
        int partitionUpdated = calcTaskPartitionMapper.update(null, Wrappers.<CostCalcTaskPartition>lambdaUpdate()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .eq(CostCalcTaskPartition::getPartitionNo, claimToken.partitionNo)
                .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_RUNNING)
                .eq(CostCalcTaskPartition::getExecuteNode, claimToken.executeNode)
                .eq(CostCalcTaskPartition::getClaimTime, claimToken.claimTime)
                .set(CostCalcTaskPartition::getUpdateTime, heartbeatTime));
        if (partitionUpdated <= 0) {
            return false;
        }
        calcTaskMapper.update(null, Wrappers.<CostCalcTask>lambdaUpdate()
                .eq(CostCalcTask::getTaskId, taskId)
                .eq(CostCalcTask::getTaskStatus, TASK_STATUS_RUNNING)
                .set(CostCalcTask::getUpdateTime, heartbeatTime));
        return true;
    }

    private Date normalizePartitionClaimTime(Date value) {
        if (value == null) {
            return null;
        }
        return new Date((value.getTime() / 1000L) * 1000L);
    }

    /**
     * 分片完成后回写统计与错误摘要，支撑后续分片重试和监控。
     */
    private void finishPartition(Long taskId, List<CostCalcTaskDetail> partitionDetails, PartitionClaimToken claimToken,
                                 PartitionExecutionResult result, Throwable throwable) {
        if (partitionDetails == null || partitionDetails.isEmpty() || claimToken == null || !isPartitionClaimOwned(claimToken)) {
            return;
        }
        Integer partitionNo = partitionDetails.get(0).getPartitionNo();
        PartitionExecutionResult summary = summarizePartitionDetails(taskId, partitionNo);
        String status = summary.failedCount <= 0 ? TASK_STATUS_SUCCESS
                : (summary.successCount > 0 ? TASK_STATUS_PART_SUCCESS : TASK_STATUS_FAILED);
        Date finishedTime = DateUtils.getNowDate();
        CostCalcTaskPartition partition = calcTaskPartitionMapper.selectOne(Wrappers.<CostCalcTaskPartition>lambdaQuery()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .eq(CostCalcTaskPartition::getPartitionNo, partitionNo)
                .last("limit 1"));
        Long durationMs = partition == null || partition.getStartedTime() == null ? 0L
                : Math.max(0L, finishedTime.getTime() - partition.getStartedTime().getTime());
        BigDecimal partitionAmountTotal = defaultZero(partition == null ? null : partition.getAmountTotal())
                .add(defaultZero(result.amountTotal))
                .setScale(2, RoundingMode.HALF_UP);
        calcTaskPartitionMapper.update(null, Wrappers.<CostCalcTaskPartition>lambdaUpdate()
                .eq(CostCalcTaskPartition::getTaskId, taskId)
                .eq(CostCalcTaskPartition::getPartitionNo, partitionNo)
                .eq(CostCalcTaskPartition::getPartitionStatus, TASK_STATUS_RUNNING)
                .eq(CostCalcTaskPartition::getExecuteNode, claimToken.executeNode)
                .eq(CostCalcTaskPartition::getClaimTime, claimToken.claimTime)
                .set(CostCalcTaskPartition::getPartitionStatus, status)
                .set(CostCalcTaskPartition::getProcessedCount, summary.processedCount)
                .set(CostCalcTaskPartition::getSuccessCount, summary.successCount)
                .set(CostCalcTaskPartition::getFailCount, summary.failedCount)
                .set(CostCalcTaskPartition::getAmountTotal, partitionAmountTotal)
                .set(CostCalcTaskPartition::getPersistMode, firstNonBlank(result.persistMode, PARTITION_PERSIST_MODE_BATCH))
                .set(CostCalcTaskPartition::getRecoveryHint, firstNonBlank(result.recoveryHint, ""))
                .set(CostCalcTaskPartition::getLastErrorStage, firstNonBlank(result.lastErrorStage,
                        throwable == null ? "" : PARTITION_STAGE_EXECUTION))
                .set(CostCalcTaskPartition::getExecuteNode, claimToken.executeNode)
                .set(CostCalcTaskPartition::getClaimTime, claimToken.claimTime)
                .set(CostCalcTaskPartition::getFinishedTime, finishedTime)
                .set(CostCalcTaskPartition::getDurationMs, durationMs)
                .set(CostCalcTaskPartition::getLastError, throwable == null ? "" : limitLength(throwable.getMessage(), 1000))
                .set(CostCalcTaskPartition::getUpdateTime, finishedTime));
    }

    protected PartitionExecutionResult executeTaskPartition(Long taskId, RuntimeSnapshot snapshot, List<CostCalcTaskDetail> details,
                                                            PartitionClaimToken claimToken) {
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null || TASK_STATUS_CANCELLED.equals(task.getTaskStatus()) || details.isEmpty()
                || claimToken == null || !isPartitionClaimOwned(claimToken)) {
            return new PartitionExecutionResult();
        }

        PartitionExecutionBundle bundle = new PartitionExecutionBundle();
        touchPartitionHeartbeat(taskId, claimToken);
        long nextHeartbeatAt = System.currentTimeMillis() + TASK_HEARTBEAT_INTERVAL_MILLIS;
        for (CostCalcTaskDetail detail : details) {
            if (isTaskCancelled(taskId) || !isPartitionClaimOwned(claimToken)) {
                break;
            }
            if (System.currentTimeMillis() >= nextHeartbeatAt) {
                if (!touchPartitionHeartbeat(taskId, claimToken)) {
                    bundle.markOwnerLost();
                    break;
                }
                nextHeartbeatAt = System.currentTimeMillis() + TASK_HEARTBEAT_INTERVAL_MILLIS;
            }
            prepareTaskDetailExecution(task, detail, snapshot, bundle);
        }
        if (!bundle.ownerLost) {
            touchPartitionHeartbeat(taskId, claimToken);
        }
        if (!bundle.detailUpdates.isEmpty()) {
            persistPartitionBundle(taskId, claimToken, bundle);
        }
        if (!bundle.ownerLost) {
            for (TaskDetailFailure failure : bundle.failures) {
                createTaskAlarm(task, failure.detail, "TASK_DETAIL_FAILED", "WARN",
                        "任务明细执行失败", "业务单号 " + failure.detail.getBizNo() + " 执行失败：" + limitLength(failure.errorMessage, 300));
            }
        }
        return bundle.toResult();
    }

    private void prepareTaskDetailExecution(CostCalcTask task, CostCalcTaskDetail detail, RuntimeSnapshot snapshot, PartitionExecutionBundle bundle) {
        try {
            Map<String, Object> input = parseObjectJson(detail.getInputJson(), "任务明细输入必须是 JSON 对象");
            ExecutionResult executionResult = executeSingle(snapshot, task.getTaskNo(), task.getBillMonth(), input);
            List<CostResultLedger> detailLedgers = new ArrayList<>();
            for (FeeExecutionResult feeResult : executionResult.feeResults) {
                CostResultTrace trace = buildTraceRecord(snapshot, feeResult);
                CostResultLedger ledger = buildLedgerRecord(task, detail, snapshot, input, feeResult, trace.getTraceId());
                bundle.traceInserts.add(trace);
                bundle.ledgerInserts.add(ledger);
                detailLedgers.add(ledger);
            }
            bundle.detailUpdates.add(buildTaskDetailUpdate(detail, DETAIL_STATUS_SUCCESS, buildDetailSummary(detailLedgers), ""));
            bundle.amountTotal = bundle.amountTotal.add(detailLedgers.stream()
                    .map(CostResultLedger::getAmountValue)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            bundle.processedCount++;
            bundle.successCount++;
        } catch (Exception e) {
            String errorMessage = limitLength(e.getMessage(), 1000);
            bundle.detailUpdates.add(buildTaskDetailUpdate(detail, DETAIL_STATUS_FAILED, "执行失败", errorMessage));
            bundle.failures.add(new TaskDetailFailure(detail, errorMessage));
            bundle.processedCount++;
            bundle.failedCount++;
        }
    }

    private void persistPartitionBundle(Long taskId, PartitionClaimToken claimToken, PartitionExecutionBundle bundle) {
        if (!isPartitionClaimOwned(claimToken)) {
            bundle.markOwnerLost();
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> persistPartitionBundleInBatch(taskId, bundle));
            bundle.persistMode = PARTITION_PERSIST_MODE_BATCH;
            bundle.recoveryHint = "";
            bundle.lastErrorStage = "";
        } catch (Exception batchException) {
            String batchMessage = limitLength(resolveThrowableMessage(batchException, "分片批量落库失败"), 500);
            try {
                transactionTemplate.executeWithoutResult(status -> persistPartitionBundleRowByRow(taskId, bundle));
                bundle.persistMode = PARTITION_PERSIST_MODE_SINGLE;
                bundle.recoveryHint = limitLength("批量落库失败后已自动降级为逐条写入：" + batchMessage, 500);
                bundle.lastErrorStage = PARTITION_STAGE_BATCH_PERSIST;
            } catch (Exception singleException) {
                String singleMessage = limitLength(resolveThrowableMessage(singleException, "分片逐条落库失败"), 500);
                throw new ServiceException("分片结果落库失败，批量写入与逐条降级均未成功。批量阶段："
                        + batchMessage + "；逐条阶段：" + singleMessage);
            }
        }
    }

    protected void persistPartitionBundleInBatch(Long taskId, PartitionExecutionBundle bundle) {
        purgeExistingTaskResults(taskId, bundle.bizNos());
        if (!bundle.traceInserts.isEmpty()) {
            resultTraceMapper.insertBatch(bundle.traceInserts);
        }
        if (!bundle.ledgerInserts.isEmpty()) {
            resultLedgerMapper.insertBatch(bundle.ledgerInserts);
        }
        calcTaskDetailMapper.updateBatchResult(bundle.detailUpdates);
    }

    private void persistPartitionBundleRowByRow(Long taskId, PartitionExecutionBundle bundle) {
        purgeExistingTaskResults(taskId, bundle.bizNos());
        for (CostResultTrace trace : bundle.traceInserts) {
            resultTraceMapper.insert(trace);
        }
        for (CostResultLedger ledger : bundle.ledgerInserts) {
            resultLedgerMapper.insert(ledger);
        }
        for (CostCalcTaskDetail detailUpdate : bundle.detailUpdates) {
            calcTaskDetailMapper.updateById(detailUpdate);
        }
    }

    private String resolveThrowableMessage(Throwable throwable, String fallback) {
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.isNotEmpty(current.getMessage())) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return fallback;
    }

    private void purgeExistingTaskResults(Long taskId, Collection<String> bizNos) {
        if (bizNos == null || bizNos.isEmpty()) {
            return;
        }
        List<CostResultLedger> existing = resultLedgerMapper.selectList(Wrappers.<CostResultLedger>lambdaQuery()
                .eq(CostResultLedger::getTaskId, taskId)
                .in(CostResultLedger::getBizNo, bizNos));
        if (existing.isEmpty()) {
            return;
        }
        List<Long> traceIds = existing.stream().map(CostResultLedger::getTraceId).filter(Objects::nonNull).collect(Collectors.toList());
        resultLedgerMapper.deleteBatchIds(existing.stream().map(CostResultLedger::getResultId).collect(Collectors.toList()));
        if (!traceIds.isEmpty()) {
            resultTraceMapper.deleteBatchIds(traceIds);
        }
    }

    private CostResultTrace buildTraceRecord(RuntimeSnapshot snapshot, FeeExecutionResult feeResult) {
        CostResultTrace trace = new CostResultTrace();
        trace.setTraceId(nextSnowflakeId());
        trace.setSceneId(snapshot.sceneId);
        trace.setVersionId(snapshot.versionId);
        trace.setRuleId(feeResult.ruleId);
        trace.setTierId(feeResult.tierId);
        trace.setVariableJson(writeJson(feeResult.variableExplain));
        trace.setConditionJson(writeJson(feeResult.conditionExplain));
        trace.setPricingJson(writeJson(feeResult.pricingExplain));
        trace.setTimelineJson(writeJson(feeResult.timelineSteps));
        return trace;
    }

    private CostResultLedger buildLedgerRecord(CostCalcTask task, CostCalcTaskDetail detail, RuntimeSnapshot snapshot,
                                               Map<String, Object> input, FeeExecutionResult feeResult, Long traceId) {
        CostResultLedger ledger = new CostResultLedger();
        ledger.setResultId(nextSnowflakeId());
        ledger.setTaskId(task.getTaskId());
        ledger.setTaskNo(task.getTaskNo());
        ledger.setSceneId(snapshot.sceneId);
        ledger.setVersionId(snapshot.versionId);
        ledger.setFeeId(feeResult.feeId);
        ledger.setFeeCode(feeResult.feeCode);
        ledger.setFeeName(feeResult.feeName);
        ledger.setBizNo(detail.getBizNo());
        ledger.setBillMonth(task.getBillMonth());
        ledger.setObjectDimension(firstNonBlank(feeResult.objectDimension, resolveString(input, "objectDimension", "object_dimension")));
        ledger.setObjectCode(firstNonBlank(resolveString(input, "objectCode", "object_code"), detail.getBizNo()));
        ledger.setObjectName(resolveString(input, "objectName", "object_name", "name"));
        ledger.setQuantityValue(feeResult.quantityValue);
        ledger.setUnitPrice(feeResult.unitPrice);
        ledger.setAmountValue(feeResult.amountValue);
        ledger.setCurrencyCode("CNY");
        ledger.setResultStatus(RESULT_STATUS_SUCCESS);
        ledger.setTraceId(traceId);
        return ledger;
    }

    private CostCalcTaskDetail buildTaskDetailUpdate(CostCalcTaskDetail detail, String status, String resultSummary, String errorMessage) {
        CostCalcTaskDetail update = new CostCalcTaskDetail();
        update.setDetailId(detail.getDetailId());
        update.setBizNo(detail.getBizNo());
        update.setDetailStatus(status);
        update.setRetryCount(detail.getRetryCount());
        update.setResultSummary(resultSummary);
        update.setErrorMessage(errorMessage);
        return update;
    }

    private long nextSnowflakeId() {
        return IdWorker.getId();
    }

    private PartitionExecutionResult markPartitionFailed(Long taskId, List<CostCalcTaskDetail> partition,
                                                         PartitionClaimToken claimToken, Throwable throwable) {
        CostCalcTask task = calcTaskMapper.selectById(taskId);
        if (task == null || partition == null || partition.isEmpty() || !isPartitionClaimOwned(claimToken)) {
            return new PartitionExecutionResult();
        }
        String errorMessage = limitLength(throwable == null ? "分片执行失败" : throwable.getMessage(), 1000);
        List<CostCalcTaskDetail> updates = partition.stream()
                .map(detail -> buildTaskDetailUpdate(detail, DETAIL_STATUS_FAILED, "分片执行失败", errorMessage))
                .collect(Collectors.toList());
        transactionTemplate.executeWithoutResult(status -> calcTaskDetailMapper.updateBatchResult(updates));
        for (CostCalcTaskDetail detail : partition) {
            createTaskAlarm(task, detail, "TASK_PARTITION_FAILED", "ERROR",
                    "任务分片执行失败", "分片 " + detail.getPartitionNo() + " 执行失败：" + limitLength(errorMessage, 300));
        }
        PartitionExecutionResult result = new PartitionExecutionResult();
        result.processedCount = partition.size();
        result.failedCount = partition.size();
        result.amountTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        result.persistMode = PARTITION_PERSIST_MODE_BATCH;
        result.lastErrorStage = PARTITION_STAGE_EXECUTION;
        return result;
    }

    private Map<String, Object> parseObjectJson(String json, String errorMessage) {
        Object parsed = parseJsonToObject(json);
        if (!(parsed instanceof Map)) {
            throw new ServiceException(errorMessage);
        }
        return castMap(parsed);
    }

    private List<Map<String, Object>> parseArrayJson(String json, String errorMessage) {
        Object parsed = parseJsonToObject(json);
        if (!(parsed instanceof List)) {
            throw new ServiceException(errorMessage);
        }
        List<?> list = (List<?>) parsed;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map)) {
                throw new ServiceException(errorMessage);
            }
            result.add(castMap(item));
        }
        return result;
    }

    private Map<String, Object> buildInputPreviewResult(Long sceneId, Long versionId, List<Long> feeIds, Long feeId,
                                                        String feeCode, String taskType, String rawJson, String mappingJson) {
        List<Map<String, Object>> rawRecords = parseInlineCalculationInputs(rawJson);
        InputBuildContext context = buildInputBuildContext(sceneId, versionId, feeIds, feeId, feeCode, taskType, mappingJson);
        return buildMappedInputResult(context, rawRecords, 1);
    }

    private InputBuildContext buildInputBuildContext(Long sceneId, Long versionId, List<Long> feeIds, Long feeId,
                                                     String feeCode, String taskType, String mappingJson) {
        Map<String, Object> template = buildFeeInputTemplate(sceneId, versionId, feeIds, feeId, feeCode, taskType);
        return accessProfileInputMappingService.buildContext(template, mappingJson);
    }

    private Map<String, Object> buildMappedInputResult(InputBuildContext context, List<Map<String, Object>> rawRecords,
                                                       int startIndex) {
        return accessProfileInputMappingService.buildMappedInputResult(context, rawRecords, startIndex);
    }

    private Object parseJsonToObject(String json) {
        if (StringUtils.isEmpty(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 解析失败：" + e.getOriginalMessage());
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (StringUtils.isEmpty(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new ServiceException("发布快照解析 JSON 失败");
        }
    }

    private Map<String, Object> parseOptionalJsonMap(String json) {
        String normalized = StringUtils.trim(json);
        if (StringUtils.isEmpty(normalized)) {
            return new LinkedHashMap<>();
        }
        return parseJsonMap(normalized);
    }

    private List<Map<String, Object>> castFieldList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add(castMap(item));
            }
        }
        return result;
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 序列化失败");
        }
    }

    private Object evaluateExpression(String expression, Map<String, Object> context) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        try {
            return expressionService.evaluate(expression, context);
        } catch (Exception e) {
            throw new ServiceException("表达式执行失败：" + expression);
        }
    }

    private Map<String, Object> mergeContext(Map<String, Object> inputContext, Map<String, Object> variableValues, Map<String, Object> feeResultContext) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        if (inputContext != null) {
            context.putAll(inputContext);
        }
        if (variableValues != null) {
            context.putAll(variableValues);
        }
        context.put("I", inputContext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputContext));
        context.put("V", variableValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variableValues));
        LinkedHashMap<String, Object> common = new LinkedHashMap<>();
        common.put("sceneCode", inputContext == null ? null : inputContext.get("sceneCode"));
        common.put("sceneName", inputContext == null ? null : inputContext.get("sceneName"));
        common.put("versionNo", inputContext == null ? null : inputContext.get("versionNo"));
        common.put("billMonth", inputContext == null ? null : inputContext.get("billMonth"));
        context.put("C", common);
        context.put("F", feeResultContext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(feeResultContext));
        context.put("T", new LinkedHashMap<>());
        return context;
    }

    private String resolveVariableFormula(RuntimeSnapshot snapshot, RuntimeVariable variable) {
        if (StringUtils.isEmpty(variable.formulaCode)) {
            throw new ServiceException("当前运行配置中的公式变量[" + variable.variableCode + "]未绑定公式编码，请先补齐配置后再执行");
        }
        RuntimeFormula formula = snapshot.formulasByCode.get(variable.formulaCode);
        if (formula == null || StringUtils.isEmpty(formula.formulaExpr)) {
            throw new ServiceException("当前运行配置中的公式变量[" + variable.variableCode + "]引用的公式编码[" + variable.formulaCode + "]不存在或不可执行，请先补齐配置后再执行");
        }
        return formula.formulaExpr;
    }

    private String resolveTemplatePath(RuntimeVariable variable) {
        if (variable == null) {
            return "";
        }
        if (SOURCE_TYPE_REMOTE.equalsIgnoreCase(variable.sourceType)) {
            return buildRemoteTemplatePath(variable);
        }
        return firstNonBlank(variable.dataPath, variable.variableCode);
    }

    private String buildRemoteTemplatePath(RuntimeVariable variable) {
        return runtimeRemoteVariableValueService.buildTemplatePath(variable);
    }

    private RuntimeFormula requireRuleFormula(RuntimeSnapshot snapshot, RuntimeRule rule) {
        if (StringUtils.isEmpty(rule.amountFormulaCode)) {
            throw new ServiceException("当前运行配置中的公式规则[" + rule.ruleCode + "]未绑定金额公式编码，请先补齐配置后再执行");
        }
        RuntimeFormula formula = snapshot.formulasByCode.get(rule.amountFormulaCode);
        if (formula == null || StringUtils.isEmpty(formula.formulaExpr)) {
            throw new ServiceException("当前运行配置中的公式规则[" + rule.ruleCode + "]引用的公式编码[" + rule.amountFormulaCode + "]不存在或不可执行，请先补齐配置后再执行");
        }
        return formula;
    }

    private List<String> splitValues(String compareValue) {
        if (StringUtils.isEmpty(compareValue)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String piece : compareValue.split(",")) {
            if (StringUtils.isNotEmpty(piece)) {
                result.add(piece.trim());
            }
        }
        return result;
    }

    private Long findFeeIdByCode(Long sceneId, String feeCode) {
        CostFeeItem fee = feeMapper.selectOne(Wrappers.<CostFeeItem>lambdaQuery()
                .eq(CostFeeItem::getSceneId, sceneId)
                .eq(CostFeeItem::getFeeCode, feeCode)
                .last("limit 1"));
        return fee == null ? null : fee.getFeeId();
    }

    private Map<String, Object> buildStep(String stepType, String objectCode, String objectName, String resultSummary) {
        LinkedHashMap<String, Object> step = new LinkedHashMap<>();
        step.put("stepType", stepType);
        step.put("objectCode", objectCode);
        step.put("objectName", objectName);
        step.put("resultSummary", resultSummary);
        return step;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Integer intValue(Object value) {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private String resolveBizNo(Map<String, Object> input, int fallbackNo) {
        String bizNo = resolveString(input, "bizNo", "biz_no", "businessNo", "business_no");
        return StringUtils.isNotEmpty(bizNo) ? bizNo : "BIZ-" + PARTITION_FORMAT.format(fallbackNo);
    }

    private String resolveString(Map<String, Object> input, String... keys) {
        for (String key : keys) {
            Object value = input.get(key);
            if (value != null && StringUtils.isNotEmpty(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private String buildRunNo(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String resolveExecuteNode() {
        return firstNonBlank(
                normalizeNodeValue(costDispatchProperties == null ? null : costDispatchProperties.getNodeId()),
                normalizeNodeValue(environment == null ? null : environment.getProperty("cost.dispatch.node-id")),
                normalizeNodeValue(System.getProperty("cost.dispatch.node-id")),
                normalizeNodeValue(System.getenv("COST_EXECUTE_NODE")),
                buildSpringNodeIdentity(),
                normalizeNodeValue(System.getenv("COMPUTERNAME")),
                normalizeNodeValue(System.getenv("HOSTNAME")),
                "LOCAL");
    }

    private long resolveTaskDispatchIntervalSeconds() {
        long configured = costDispatchProperties == null || costDispatchProperties.getDispatchIntervalSeconds() == null
                ? DEFAULT_TASK_DISPATCH_INTERVAL_SECONDS
                : costDispatchProperties.getDispatchIntervalSeconds();
        return Math.max(1L, configured);
    }

    private long resolveTaskStaleTimeoutSeconds() {
        long configured = costDispatchProperties == null || costDispatchProperties.getStaleTimeoutSeconds() == null
                ? DEFAULT_TASK_STALE_TIMEOUT_SECONDS
                : costDispatchProperties.getStaleTimeoutSeconds();
        return Math.max(10L, configured);
    }

    private long resolveTaskStaleTimeoutMillis() {
        return TimeUnit.SECONDS.toMillis(resolveTaskStaleTimeoutSeconds());
    }

    private String buildSpringNodeIdentity() {
        if (environment == null) {
            return null;
        }
        String applicationName = normalizeNodeValue(environment.getProperty("spring.application.name"));
        String serverPort = normalizeNodeValue(environment.getProperty("server.port"));
        String host = firstNonBlank(
                normalizeNodeValue(System.getenv("COMPUTERNAME")),
                normalizeNodeValue(System.getenv("HOSTNAME")));
        if (applicationName == null && serverPort == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(firstNonBlank(applicationName, "cost-platform"));
        if (host != null) {
            builder.append('@').append(host);
        }
        if (serverPort != null) {
            builder.append(':').append(serverPort);
        }
        return builder.toString();
    }

    private String normalizeNodeValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private String resolveOperator() {
        try {
            return firstNonBlank(SecurityUtils.getUsername(), "system");
        } catch (Exception ignored) {
            return "system";
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String limitLength(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private CostBillPeriod ensureBillPeriodAvailable(Long sceneId, String billMonth, Long versionId) {
        CostBillPeriod period = billPeriodMapper.selectOne(Wrappers.<CostBillPeriod>lambdaQuery()
                .eq(CostBillPeriod::getSceneId, sceneId)
                .eq(CostBillPeriod::getBillMonth, billMonth)
                .last("limit 1"));
        if (period == null) {
            Date now = DateUtils.getNowDate();
            String operator = resolveOperator();
            period = new CostBillPeriod();
            period.setSceneId(sceneId);
            period.setBillMonth(billMonth);
            period.setPeriodStatus(PERIOD_STATUS_NOT_STARTED);
            period.setActiveVersionId(versionId);
            period.setResultCount(0L);
            period.setAmountTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            period.setRemark("运行链自动创建账期");
            period.setCreateBy(operator);
            period.setCreateTime(now);
            period.setUpdateBy(operator);
            period.setUpdateTime(now);
            billPeriodMapper.insert(period);
            return period;
        }
        if (PERIOD_STATUS_SEALED.equals(period.getPeriodStatus())) {
            throw new ServiceException("当前账期已封存，禁止直接提交正式核算任务");
        }
        if (!Objects.equals(period.getActiveVersionId(), versionId)) {
            billPeriodMapper.update(null, Wrappers.<CostBillPeriod>lambdaUpdate()
                    .eq(CostBillPeriod::getPeriodId, period.getPeriodId())
                    .set(CostBillPeriod::getActiveVersionId, versionId)
                    .set(CostBillPeriod::getUpdateBy, resolveOperator())
                    .set(CostBillPeriod::getUpdateTime, DateUtils.getNowDate()));
            period.setActiveVersionId(versionId);
        }
        return period;
    }

    private void markPeriodInProgress(CostBillPeriod period, CostCalcTask task) {
        billPeriodMapper.update(null, Wrappers.<CostBillPeriod>lambdaUpdate()
                .eq(CostBillPeriod::getPeriodId, period.getPeriodId())
                .ne(CostBillPeriod::getPeriodStatus, PERIOD_STATUS_SEALED)
                .set(CostBillPeriod::getPeriodStatus, PERIOD_STATUS_IN_PROGRESS)
                .set(CostBillPeriod::getActiveVersionId, task.getVersionId())
                .set(CostBillPeriod::getLastTaskId, task.getTaskId())
                .set(CostBillPeriod::getLastTaskNo, task.getTaskNo())
                .set(CostBillPeriod::getUpdateBy, resolveOperator())
                .set(CostBillPeriod::getUpdateTime, DateUtils.getNowDate()));
    }

    private void refreshBillPeriod(Long sceneId, String billMonth, CostCalcTask task) {
        CostBillPeriod period = billPeriodMapper.selectOne(Wrappers.<CostBillPeriod>lambdaQuery()
                .eq(CostBillPeriod::getSceneId, sceneId)
                .eq(CostBillPeriod::getBillMonth, billMonth)
                .last("limit 1"));
        if (period == null) {
            return;
        }
        Long targetTaskId = task == null ? period.getLastTaskId() : task.getTaskId();
        Long resultCountValue = targetTaskId == null
                ? resultLedgerMapper.countBySceneAndBillMonth(sceneId, billMonth)
                : countTaskResults(targetTaskId);
        long resultCount = resultCountValue == null ? 0L : resultCountValue;
        BigDecimal amountTotal = defaultZero(targetTaskId == null
                ? resultLedgerMapper.sumAmountBySceneAndBillMonth(sceneId, billMonth)
                : calcTaskPartitionMapper.sumAmountByTaskId(targetTaskId))
                .setScale(2, RoundingMode.HALF_UP);
        String periodStatus = period.getPeriodStatus();
        if (!PERIOD_STATUS_SEALED.equals(periodStatus)) {
            if (task != null && (TASK_STATUS_RUNNING.equals(task.getTaskStatus()) || TASK_STATUS_INIT.equals(task.getTaskStatus()))) {
                periodStatus = PERIOD_STATUS_IN_PROGRESS;
            } else if (resultCount > 0) {
                periodStatus = PERIOD_STATUS_CLOSED;
            } else {
                periodStatus = PERIOD_STATUS_NOT_STARTED;
            }
        }
        billPeriodMapper.update(null, Wrappers.<CostBillPeriod>lambdaUpdate()
                .eq(CostBillPeriod::getPeriodId, period.getPeriodId())
                .set(CostBillPeriod::getPeriodStatus, periodStatus)
                .set(CostBillPeriod::getActiveVersionId, task == null ? period.getActiveVersionId() : task.getVersionId())
                .set(CostBillPeriod::getResultCount, resultCount)
                .set(CostBillPeriod::getAmountTotal, amountTotal)
                .set(CostBillPeriod::getLastTaskId, task == null ? period.getLastTaskId() : task.getTaskId())
                .set(CostBillPeriod::getLastTaskNo, task == null ? period.getLastTaskNo() : task.getTaskNo())
                .set(CostBillPeriod::getUpdateBy, resolveOperator())
                .set(CostBillPeriod::getUpdateTime, DateUtils.getNowDate()));
    }

    private void syncRecalcByTask(CostCalcTask task, String taskStatus) {
        CostRecalcOrder recalcOrder = recalcOrderMapper.selectOne(Wrappers.<CostRecalcOrder>lambdaQuery()
                .eq(CostRecalcOrder::getTargetTaskId, task.getTaskId())
                .last("limit 1"));
        if (recalcOrder == null) {
            return;
        }
        String recalcStatus = RECALC_STATUS_RUNNING;
        if (TASK_STATUS_SUCCESS.equals(taskStatus) || TASK_STATUS_PART_SUCCESS.equals(taskStatus)) {
            recalcStatus = RECALC_STATUS_SUCCESS;
        } else if (TASK_STATUS_FAILED.equals(taskStatus) || TASK_STATUS_CANCELLED.equals(taskStatus)) {
            recalcStatus = RECALC_STATUS_FAILED;
        }
        String diffSummaryJson = buildRecalcDiffSummary(recalcOrder, task);
        BigDecimal diffAmount = extractDiffAmount(diffSummaryJson);
        recalcOrderMapper.update(null, Wrappers.<CostRecalcOrder>lambdaUpdate()
                .eq(CostRecalcOrder::getRecalcId, recalcOrder.getRecalcId())
                .set(CostRecalcOrder::getRecalcStatus, recalcStatus)
                .set(CostRecalcOrder::getDiffSummaryJson, diffSummaryJson)
                .set(CostRecalcOrder::getDiffAmount, diffAmount)
                .set(CostRecalcOrder::getFinishTime, DateUtils.getNowDate())
                .set(CostRecalcOrder::getUpdateBy, resolveOperator())
                .set(CostRecalcOrder::getUpdateTime, DateUtils.getNowDate()));
    }

    private String buildRecalcDiffSummary(CostRecalcOrder recalcOrder, CostCalcTask task) {
        CostCalcTask baselineTask = recalcOrder.getBaselineTaskId() == null ? null : calcTaskMapper.selectById(recalcOrder.getBaselineTaskId());
        BigDecimal baselineAmount = sumTaskAmount(recalcOrder.getBaselineTaskId());
        BigDecimal targetAmount = sumTaskAmount(task.getTaskId());
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("baselineTaskNo", baselineTask == null ? recalcOrder.getBaselineTaskNo() : baselineTask.getTaskNo());
        summary.put("targetTaskNo", task.getTaskNo());
        summary.put("baselineAmount", baselineAmount);
        summary.put("targetAmount", targetAmount);
        summary.put("diffAmount", targetAmount.subtract(baselineAmount).setScale(2, RoundingMode.HALF_UP));
        summary.put("baselineResultCount", countTaskResults(recalcOrder.getBaselineTaskId()));
        summary.put("targetResultCount", countTaskResults(task.getTaskId()));
        summary.put("taskStatus", task.getTaskStatus());
        return writeJson(summary);
    }

    private BigDecimal extractDiffAmount(String diffSummaryJson) {
        try {
            Map<String, Object> summary = objectMapper.readValue(diffSummaryJson, new TypeReference<Map<String, Object>>() {
            });
            BigDecimal diffAmount = toBigDecimal(summary.get("diffAmount"));
            return defaultZero(diffAmount).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal sumTaskAmount(Long taskId) {
        if (taskId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return defaultZero(calcTaskPartitionMapper.sumAmountByTaskId(taskId)).setScale(2, RoundingMode.HALF_UP);
    }

    private long countTaskResults(Long taskId) {
        if (taskId == null) {
            return 0L;
        }
        return resultLedgerMapper.selectCount(Wrappers.<CostResultLedger>lambdaQuery()
                .eq(CostResultLedger::getTaskId, taskId));
    }

    private void runTaskFinishSideEffect(CostCalcTask task, String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            log.warn("Task finish side effect failed, taskId={}, action={}", task == null ? null : task.getTaskId(), action, ex);
            if (task == null) {
                return;
            }
            try {
                createTaskAlarm(task, null, "TASK_FINISH_SIDE_EFFECT_FAILED", "WARN",
                        "任务收尾治理失败",
                        "任务 " + task.getTaskNo() + " 在 " + action + " 阶段失败：" + limitLength(ex.getMessage(), 300));
            } catch (Exception alarmException) {
                log.warn("Task finish side effect alarm create failed, taskId={}, action={}",
                        task.getTaskId(), action, alarmException);
            }
        }
    }

    private void createTaskAlarm(CostCalcTask task, CostCalcTaskDetail detail, String alarmType,
                                 String alarmLevel, String alarmTitle, String alarmContent) {
        CostAlarmRecord alarm = new CostAlarmRecord();
        alarm.setSceneId(task.getSceneId());
        alarm.setVersionId(task.getVersionId());
        alarm.setTaskId(task.getTaskId());
        alarm.setDetailId(detail == null ? null : detail.getDetailId());
        alarm.setBillMonth(task.getBillMonth());
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setAlarmTitle(alarmTitle);
        alarm.setAlarmContent(limitLength(alarmContent, 1000));
        alarm.setSourceKey(task.getTaskNo() + ":" + alarmType + ":" + (detail == null ? "TASK" : detail.getDetailId()));
        alarmService.createAlarm(alarm);
    }

    private String buildRuntimeCacheKey(Long versionId) {
        return RUNTIME_CACHE_PREFIX + versionId;
    }

    public static class RuntimeSnapshot {
        public Long sceneId;
        public Long versionId;
        public String sceneCode;
        public String sceneName;
        public String defaultObjectDimension;
        public String versionNo;
        public String snapshotSource;
        public List<RuntimeFee> fees = new ArrayList<>();
        public Map<String, RuntimeFee> feesByCode = new LinkedHashMap<>();
        public List<RuntimeVariable> variables = new ArrayList<>();
        public Map<String, RuntimeVariable> variablesByCode = new LinkedHashMap<>();
        public Map<String, List<RuntimeVariable>> executionVariablesByFeeCode = new LinkedHashMap<>();
        public Map<String, RuntimeFormula> formulasByCode = new LinkedHashMap<>();
        public Map<String, RuntimeRule> rulesByCode = new LinkedHashMap<>();
        public Map<String, List<RuntimeRule>> rulesByFeeCode = new LinkedHashMap<>();
        public Map<String, List<RuntimeCondition>> conditionsByRuleCode = new LinkedHashMap<>();
        public Map<String, List<RuntimeTier>> tiersByRuleCode = new LinkedHashMap<>();
    }

    public static class RuntimeFee {
        public Long feeId;
        public String feeCode;
        public String feeName;
        public String unitCode;
        public String objectDimension;
        public Integer sortNo;
    }

    public static class RuntimeVariable {
        public String variableCode;
        public String variableName;
        public String sourceType;
        public String sourceSystem;
        public String dataType;
        public String remoteApi;
        public String authType;
        public String authConfigJson;
        public String dataPath;
        public String mappingConfigJson;
        public String syncMode;
        public String cachePolicy;
        public String fallbackPolicy;
        public String formulaExpr;
        public String formulaCode;
        public Object defaultValue;
        public Integer sortNo;
    }

    public static class RuntimeFormula {
        public String formulaCode;
        public String formulaName;
        public String businessFormula;
        public String formulaExpr;
        public String returnType;
    }

    public static class RuntimeRule {
        public Long ruleId;
        public String feeCode;
        public String ruleCode;
        public String ruleName;
        public String ruleType;
        public String conditionLogic;
        public Integer priority;
        public String quantityVariableCode;
        public String pricingMode;
        public Integer matchedGroupNo;
        public Map<String, Object> pricingConfig;
        public String amountFormula;
        public String amountFormulaCode;
        public String amountBusinessFormula;
        public Integer sortNo;
        public List<RuntimeCondition> conditions = Collections.emptyList();
        public List<RuntimeConditionGroup> conditionGroups = Collections.emptyList();
        public List<RuntimeTier> tiers = Collections.emptyList();
    }

    public static class RuntimeCondition {
        public String ruleCode;
        public Integer groupNo;
        public Integer sortNo;
        public String variableCode;
        public String displayName;
        public String operatorCode;
        public String compareValue;
    }

    public static class RuntimeConditionGroup {
        public Integer groupNo;
        public List<RuntimeCondition> conditions = Collections.emptyList();
    }

    public static class RuntimeTier {
        public Long tierId;
        public String ruleCode;
        public Integer tierNo;
        public BigDecimal startValue;
        public BigDecimal endValue;
        public BigDecimal rateValue;
        public String intervalMode;

        public String buildRangeSummary() {
            return String.format(Locale.ROOT, "%s ~ %s",
                    startValue == null ? "-INF" : startValue.toPlainString(),
                    endValue == null ? "+INF" : endValue.toPlainString());
        }
    }

    private static class FeeTemplateContext {
        private final Map<String, FeeTemplateVariable> variables = new LinkedHashMap<>();
        private final List<Map<String, Object>> ruleSummaries = new ArrayList<>();
    }

    private static class FeeTemplateVariable {
        private final RuntimeVariable variable;
        private final Set<String> templateRoles = new LinkedHashSet<>();
        private final Set<String> sourceRuleCodes = new LinkedHashSet<>();
        private final Set<String> dependsOn = new LinkedHashSet<>();
        private boolean includedInTemplate;

        private FeeTemplateVariable(RuntimeVariable variable) {
            this.variable = variable;
        }
    }

}
