package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.service.CostLiteBillingService;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.domain.cost.CostCalcTask;
import com.ruoyi.system.domain.cost.CostResultLedger;
import com.ruoyi.system.domain.cost.CostSimulationRecord;
import com.ruoyi.system.domain.cost.bo.CostCalcInputBatchCreateBo;
import com.ruoyi.system.domain.cost.bo.CostCalcTaskSubmitBo;
import com.ruoyi.system.domain.cost.bo.CostFeeCalculateBo;
import com.ruoyi.system.domain.cost.bo.CostInputBuildPreviewBo;
import com.ruoyi.system.domain.cost.bo.CostSimulationExecuteBo;
import com.ruoyi.system.service.cost.ICostRunService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 轻量运行链接口：模板、试算、正式任务、结果和计费调用留存。
 */
@RestController
@RequestMapping("/cost/run")
public class CostLiteRunController extends CostLiteControllerSupport {
    private final ICostRunService runService;
    private final CostLiteBillingService billingService;

    public CostLiteRunController(ICostRunService runService,
                                 CostLiteBillingService billingService,
                                 CostLiteProperties properties) {
        super(properties);
        this.runService = runService;
        this.billingService = billingService;
    }

    @GetMapping("/simulation/stats")
    public AjaxResult simulationStats(CostSimulationRecord query) {
        return success(runService.selectSimulationStats(query));
    }

    @GetMapping("/simulation/list")
    public TableDataInfo simulationList(CostSimulationRecord query,
                                        @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(runService.selectSimulationList(query), pageNum, pageSize, properties);
    }

    @PostMapping("/simulation/execute")
    public AjaxResult simulationExecute(@Valid @RequestBody CostSimulationExecuteBo request) {
        return success(runService.executeSimulation(request));
    }

    @PostMapping("/simulation/batch-execute")
    public AjaxResult simulationBatchExecute(@Valid @RequestBody CostSimulationExecuteBo request) {
        return success(runService.executeSimulationBatch(request));
    }

    @GetMapping("/simulation/{simulationId}")
    public AjaxResult simulationDetail(@PathVariable Long simulationId) {
        return success(runService.selectSimulationDetail(simulationId));
    }

    @PostMapping("/input-build/preview")
    public AjaxResult previewInputBuild(@Valid @RequestBody CostInputBuildPreviewBo request) {
        return success(runService.previewBuiltInput(request));
    }

    @GetMapping("/task/stats")
    public AjaxResult taskStats(CostCalcTask query) {
        return success(runService.selectTaskStats(query));
    }

    @GetMapping("/task/overview")
    public AjaxResult taskOverview(CostCalcTask query) {
        return success(runService.selectTaskOverview(query));
    }

    @GetMapping("/task/list")
    public TableDataInfo taskList(CostCalcTask query,
                                  @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                  @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(runService.selectTaskList(query), pageNum, pageSize, properties);
    }

    @PostMapping("/task/precheck")
    public AjaxResult precheckTask(@RequestBody CostCalcTaskSubmitBo request) {
        return success(runService.precheckTask(request));
    }

    @PostMapping("/task/submit")
    public AjaxResult submitTask(@Valid @RequestBody CostCalcTaskSubmitBo request) {
        return success(runService.submitTask(request));
    }

    @PostMapping("/task/input-batch")
    public AjaxResult createInputBatch(@Valid @RequestBody CostCalcInputBatchCreateBo request) {
        return success(runService.createInputBatch(request));
    }

    @GetMapping("/task/input-batch/list")
    public TableDataInfo inputBatchList(CostCalcInputBatch query,
                                        @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(runService.selectInputBatchList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/task/input-batch/{batchId}")
    public AjaxResult inputBatchDetail(@PathVariable Long batchId,
                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        return success(runService.selectInputBatchDetail(batchId, pageNum, pageSize));
    }

    @GetMapping("/task/{taskId}")
    public AjaxResult taskDetail(@PathVariable Long taskId,
                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        return success(runService.selectTaskDetail(taskId, pageNum, pageSize));
    }

    @PutMapping("/task/retry/{detailId}")
    public AjaxResult retryTaskDetail(@PathVariable Long detailId) {
        return toAjax(runService.retryTaskDetail(detailId));
    }

    @PutMapping("/task/partition/retry/{partitionId}")
    public AjaxResult retryTaskPartition(@PathVariable Long partitionId) {
        return toAjax(runService.retryTaskPartition(partitionId));
    }

    @PutMapping("/task/cancel/{taskId}")
    public AjaxResult cancelTask(@PathVariable Long taskId) {
        return toAjax(runService.cancelTask(taskId));
    }

    @GetMapping("/result/stats")
    public AjaxResult resultStats(CostResultLedger query) {
        return success(runService.selectResultStats(query));
    }

    @GetMapping("/result/compare")
    public AjaxResult resultCompare(com.ruoyi.system.domain.cost.bo.CostResultCompareBo query) {
        return success(runService.selectResultCompare(query));
    }

    @GetMapping("/result/list")
    public TableDataInfo resultList(CostResultLedger query,
                                    @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                    @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(runService.selectResultList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/result/{resultId}")
    public AjaxResult resultDetail(@PathVariable Long resultId) {
        return success(runService.selectResultDetail(resultId));
    }

    @GetMapping("/trace/{traceId}")
    public AjaxResult traceDetail(@PathVariable Long traceId) {
        return success(runService.selectTraceDetail(traceId));
    }

    @GetMapping("/version-options/{sceneId}")
    public AjaxResult versionOptions(@PathVariable Long sceneId) {
        return success(runService.selectVersionOptions(sceneId));
    }

    @GetMapping("/input-template")
    public AjaxResult inputTemplate(@RequestParam Long sceneId,
                                    @RequestParam(required = false) Long versionId,
                                    @RequestParam(required = false) String taskType) {
        return success(runService.buildInputTemplate(sceneId, versionId, taskType));
    }

    @GetMapping("/input-template/fee")
    public AjaxResult feeInputTemplate(@RequestParam Long sceneId,
                                       @RequestParam(required = false) Long versionId,
                                       @RequestParam(required = false) String feeIds,
                                       @RequestParam(required = false) Long feeId,
                                       @RequestParam(required = false) String feeCode,
                                       @RequestParam(required = false) String taskType,
                                       @RequestParam(required = false) String snapshotMode) {
        return success(runService.buildFeeInputTemplate(sceneId, versionId, parseIds(feeIds), feeId, feeCode,
                taskType, snapshotMode));
    }

    /**
     * 同步费用计算统一从这里进入，以便成功和失败都留存到母体试算记录表。
     */
    @PostMapping("/fee/calculate")
    public AjaxResult calculateFee(@Valid @RequestBody CostFeeCalculateBo request) {
        return success(billingService.calculate(request));
    }

    private List<Long> parseIds(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (String item : text.split(",")) {
            if (item != null && !item.trim().isEmpty()) {
                ids.add(Long.valueOf(item.trim()));
            }
        }
        return ids;
    }
}
