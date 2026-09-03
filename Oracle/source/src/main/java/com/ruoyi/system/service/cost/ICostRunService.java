package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.domain.cost.CostCalcTask;
import com.ruoyi.system.domain.cost.CostResultLedger;
import com.ruoyi.system.domain.cost.CostSimulationRecord;
import com.ruoyi.system.domain.cost.bo.*;

import java.util.List;
import java.util.Map;

/**
 * 线程五运行链服务接口
 *
 * @author HwFan
 */
public interface ICostRunService {
    Map<String, Object> selectSimulationStats(CostSimulationRecord query);

    List<CostSimulationRecord> selectSimulationList(CostSimulationRecord query);

    Map<String, Object> executeSimulation(CostSimulationExecuteBo bo);

    Map<String, Object> executeSimulationBatch(CostSimulationExecuteBo bo);

    Map<String, Object> selectSimulationDetail(Long simulationId);

    List<CostSimulationChargeExportRow> selectSimulationChargeExportRows(Long simulationId);

    List<CostSimulationBatchExportRow> selectSimulationBatchExportRows(String simulationIds, Boolean failedOnly);

    Map<String, Object> selectTaskStats(CostCalcTask query);

    Map<String, Object> selectTaskOverview(CostCalcTask query);

    List<CostCalcTask> selectTaskList(CostCalcTask query);

    Map<String, Object> precheckTask(CostCalcTaskSubmitBo bo);

    Map<String, Object> submitTask(CostCalcTaskSubmitBo bo);

    Map<String, Object> createInputBatch(CostCalcInputBatchCreateBo bo);

    List<CostCalcInputBatch> selectInputBatchList(CostCalcInputBatch query);

    Map<String, Object> selectInputBatchDetail(Long batchId, Integer pageNum, Integer pageSize);

    Map<String, Object> selectTaskDetail(Long taskId, Integer pageNum, Integer pageSize);

    int retryTaskDetail(Long detailId);

    int retryTaskPartition(Long partitionId);

    int cancelTask(Long taskId);

    Map<String, Object> selectResultStats(CostResultLedger query);

    Map<String, Object> selectResultCompare(CostResultCompareBo query);

    List<CostResultLedger> selectResultList(CostResultLedger query);

    List<CostResultLedger> selectResultExportList(CostResultLedger query);

    Map<String, Object> selectResultDetail(Long resultId);

    Map<String, Object> selectTraceDetail(Long traceId);

    List<Map<String, Object>> selectVersionOptions(Long sceneId);

    List<Map<String, Object>> selectRuntimeFeeOptions(Long sceneId, Long versionId, String snapshotMode);

    Map<String, Object> buildInputTemplate(Long sceneId, Long versionId, String taskType);

    Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, Long feeId, String feeCode, String taskType);

    Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, Long feeId, String feeCode,
                                              String taskType, String snapshotMode);

    Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, List<Long> feeIds, Long feeId,
                                              String feeCode, String taskType);

    Map<String, Object> buildFeeInputTemplate(Long sceneId, Long versionId, List<Long> feeIds, Long feeId,
                                              String feeCode, String taskType, String snapshotMode);

    Map<String, Object> previewBuiltInput(CostInputBuildPreviewBo bo);

    Map<String, Object> calculateFee(CostFeeCalculateBo bo);
}
