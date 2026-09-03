package com.ruoyi.system.service.cost.constant;

import com.ruoyi.system.service.cost.enums.CostAccessSourceType;
import com.ruoyi.system.service.cost.enums.CostDetailStatus;
import com.ruoyi.system.service.cost.enums.CostInputBatchStatus;
import com.ruoyi.system.service.cost.enums.CostInputSourceType;
import com.ruoyi.system.service.cost.enums.CostPeriodStatus;
import com.ruoyi.system.service.cost.enums.CostRecalcStatus;
import com.ruoyi.system.service.cost.enums.CostSnapshotSource;
import com.ruoyi.system.service.cost.enums.CostTaskStatus;
import com.ruoyi.system.service.cost.enums.CostTaskType;
import com.ruoyi.system.service.cost.enums.CostVariableDataType;
import com.ruoyi.system.service.cost.enums.CostVariableSourceType;

public final class CostDomainConstants {
    public static final String STATUS_ENABLED = "0";

    public static final String SIMULATION_STATUS_SUCCESS = CostTaskStatus.SUCCESS.code();
    public static final String SIMULATION_STATUS_FAILED = CostTaskStatus.FAILED.code();

    public static final String TASK_TYPE_SIMULATION_BATCH = CostTaskType.SIMULATION_BATCH.code();
    public static final String TASK_TYPE_FORMAL_SINGLE = CostTaskType.FORMAL_SINGLE.code();
    public static final String TASK_TYPE_FORMAL_BATCH = CostTaskType.FORMAL_BATCH.code();

    public static final String INPUT_SOURCE_INLINE_JSON = CostInputSourceType.INLINE_JSON.code();
    public static final String INPUT_SOURCE_BATCH = CostInputSourceType.INPUT_BATCH.code();

    public static final String INPUT_BATCH_STATUS_LOADING = CostInputBatchStatus.LOADING.code();
    public static final String INPUT_BATCH_STATUS_PARTIAL = CostInputBatchStatus.PARTIAL.code();
    public static final String INPUT_BATCH_STATUS_READY = CostInputBatchStatus.READY.code();
    public static final String INPUT_BATCH_STATUS_SUBMITTED = CostInputBatchStatus.SUBMITTED.code();
    public static final String INPUT_BATCH_STATUS_CONSUMED = CostInputBatchStatus.CONSUMED.code();

    public static final String TASK_STATUS_INIT = CostTaskStatus.INIT.code();
    public static final String TASK_STATUS_RUNNING = CostTaskStatus.RUNNING.code();
    public static final String TASK_STATUS_SUCCESS = CostTaskStatus.SUCCESS.code();
    public static final String TASK_STATUS_PART_SUCCESS = CostTaskStatus.PART_SUCCESS.code();
    public static final String TASK_STATUS_FAILED = CostTaskStatus.FAILED.code();
    public static final String TASK_STATUS_CANCELLED = CostTaskStatus.CANCELLED.code();

    public static final String DETAIL_STATUS_INIT = CostDetailStatus.INIT.code();
    public static final String DETAIL_STATUS_SUCCESS = CostDetailStatus.SUCCESS.code();
    public static final String DETAIL_STATUS_FAILED = CostDetailStatus.FAILED.code();

    public static final String RESULT_STATUS_SUCCESS = CostDetailStatus.SUCCESS.code();

    public static final String PERIOD_STATUS_NOT_STARTED = CostPeriodStatus.NOT_STARTED.code();
    public static final String PERIOD_STATUS_IN_PROGRESS = CostPeriodStatus.IN_PROGRESS.code();
    public static final String PERIOD_STATUS_CLOSED = CostPeriodStatus.CLOSED.code();
    public static final String PERIOD_STATUS_SEALED = CostPeriodStatus.SEALED.code();

    public static final String RECALC_STATUS_PENDING = CostRecalcStatus.PENDING_APPROVAL.code();
    public static final String RECALC_STATUS_APPROVED = CostRecalcStatus.APPROVED.code();
    public static final String RECALC_STATUS_REJECTED = CostRecalcStatus.REJECTED.code();
    public static final String RECALC_STATUS_RUNNING = CostRecalcStatus.RUNNING.code();
    public static final String RECALC_STATUS_SUCCESS = CostRecalcStatus.SUCCESS.code();
    public static final String RECALC_STATUS_FAILED = CostRecalcStatus.FAILED.code();

    public static final String SNAPSHOT_SOURCE_PUBLISHED = CostSnapshotSource.PUBLISHED.code();
    public static final String SNAPSHOT_SOURCE_DRAFT = CostSnapshotSource.DRAFT.code();

    public static final String SOURCE_TYPE_FORMULA = CostVariableSourceType.FORMULA.code();
    public static final String SOURCE_TYPE_REMOTE = CostVariableSourceType.REMOTE.code();
    public static final String SOURCE_TYPE_INPUT = CostVariableSourceType.INPUT.code();
    public static final String SOURCE_TYPE_DICT = CostVariableSourceType.DICT.code();

    public static final String DATA_TYPE_NUMBER = CostVariableDataType.NUMBER.code();
    public static final String DATA_TYPE_BOOLEAN = CostVariableDataType.BOOLEAN.code();
    public static final String DATA_TYPE_JSON = CostVariableDataType.JSON.code();

    public static final String ACCESS_SOURCE_TYPE_HTTP_API = CostAccessSourceType.HTTP_API.code();

    private CostDomainConstants() {
    }
}
