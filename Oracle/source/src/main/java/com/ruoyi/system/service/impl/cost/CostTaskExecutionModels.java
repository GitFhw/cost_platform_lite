package com.ruoyi.system.service.impl.cost;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostCalcTaskDetail;
import com.ruoyi.system.domain.cost.CostResultLedger;
import com.ruoyi.system.domain.cost.CostResultTrace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

class TaskClaimResult {
    final Date startedTime;

    TaskClaimResult(Date startedTime) {
        this.startedTime = startedTime;
    }
}

class PartitionClaimToken {
    final Long taskId;
    final Integer partitionNo;
    final String executeNode;
    final Date claimTime;

    PartitionClaimToken(Long taskId, Integer partitionNo, String executeNode, Date claimTime) {
        this.taskId = taskId;
        this.partitionNo = partitionNo;
        this.executeNode = executeNode;
        this.claimTime = claimTime;
    }
}

class PartitionDispatchContext {
    final List<CostCalcTaskDetail> partitionDetails;
    final PartitionClaimToken claimToken;

    PartitionDispatchContext(List<CostCalcTaskDetail> partitionDetails, PartitionClaimToken claimToken) {
        this.partitionDetails = partitionDetails;
        this.claimToken = claimToken;
    }
}

class TaskDetailFailure {
    final CostCalcTaskDetail detail;
    final String errorMessage;

    TaskDetailFailure(CostCalcTaskDetail detail, String errorMessage) {
        this.detail = detail;
        this.errorMessage = errorMessage;
    }
}

class TaskExecutionSummary {
    int totalCount;
    int processedCount;
    int successCount;
    int failedCount;
}

class PartitionExecutionResult {
    private static final String PARTITION_PERSIST_MODE_BATCH = "BATCH";

    int processedCount;
    int successCount;
    int failedCount;
    BigDecimal amountTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    String persistMode = PARTITION_PERSIST_MODE_BATCH;
    String recoveryHint = "";
    String lastErrorStage = "";
}

class PartitionExecutionBundle {
    private static final String PARTITION_PERSIST_MODE_BATCH = "BATCH";
    private static final String PARTITION_STAGE_EXECUTION = "EXECUTION";

    final List<CostResultTrace> traceInserts = new ArrayList<>();
    final List<CostResultLedger> ledgerInserts = new ArrayList<>();
    final List<CostCalcTaskDetail> detailUpdates = new ArrayList<>();
    final List<TaskDetailFailure> failures = new ArrayList<>();
    boolean ownerLost;
    int processedCount;
    int successCount;
    int failedCount;
    BigDecimal amountTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    String persistMode = PARTITION_PERSIST_MODE_BATCH;
    String recoveryHint = "";
    String lastErrorStage = "";

    Collection<String> bizNos() {
        return detailUpdates.stream()
                .map(CostCalcTaskDetail::getBizNo)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    PartitionExecutionResult toResult() {
        PartitionExecutionResult result = new PartitionExecutionResult();
        result.processedCount = processedCount;
        result.successCount = successCount;
        result.failedCount = failedCount;
        result.amountTotal = amountTotal.setScale(2, RoundingMode.HALF_UP);
        result.persistMode = persistMode;
        result.recoveryHint = recoveryHint;
        result.lastErrorStage = lastErrorStage;
        return result;
    }

    void markOwnerLost() {
        this.ownerLost = true;
        this.processedCount = 0;
        this.successCount = 0;
        this.failedCount = 0;
        this.amountTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.traceInserts.clear();
        this.ledgerInserts.clear();
        this.detailUpdates.clear();
        this.failures.clear();
        this.persistMode = PARTITION_PERSIST_MODE_BATCH;
        this.recoveryHint = "分片已被其他执行器接管，本次结果未写回";
        this.lastErrorStage = PARTITION_STAGE_EXECUTION;
    }
}
