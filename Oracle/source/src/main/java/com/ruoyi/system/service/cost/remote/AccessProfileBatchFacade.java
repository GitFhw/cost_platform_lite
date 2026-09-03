package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.domain.cost.bo.CostAccessProfileBuildBatchBo;
import com.ruoyi.system.domain.cost.bo.CostCalcInputBatchCreateBo;
import com.ruoyi.system.service.cost.remote.AccessProfileInputMappingService.InputBuildContext;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Facade for access-profile driven input batch creation.
 */
@Service
public class AccessProfileBatchFacade {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AccessProfilePayloadFetchService payloadFetchService;
    private final AccessProfileFetchSupport fetchSupport;
    private final AccessProfileInputMappingService inputMappingService;
    private final AccessProfileBatchProgressService progressService;
    private final AccessProfileInputBatchService inputBatchService;
    private final AccessProfileBatchResultAssembler resultAssembler;
    private final AccessProfilePagedBatchOrchestrator pagedBatchOrchestrator;

    public AccessProfileBatchFacade(AccessProfilePayloadFetchService payloadFetchService,
                                    AccessProfileFetchSupport fetchSupport,
                                    AccessProfileInputMappingService inputMappingService,
                                    AccessProfileBatchProgressService progressService,
                                    AccessProfileInputBatchService inputBatchService,
                                    AccessProfileBatchResultAssembler resultAssembler,
                                    AccessProfilePagedBatchOrchestrator pagedBatchOrchestrator) {
        this.payloadFetchService = payloadFetchService;
        this.fetchSupport = fetchSupport;
        this.inputMappingService = inputMappingService;
        this.progressService = progressService;
        this.inputBatchService = inputBatchService;
        this.resultAssembler = resultAssembler;
        this.pagedBatchOrchestrator = pagedBatchOrchestrator;
    }

    public Map<String, Object> createNonPaged(CostAccessProfile profile,
                                              InputBuildContext context,
                                              AccessProfileFetchConfig fetchConfig,
                                              String billMonth,
                                              String requestPayloadJson,
                                              String remark,
                                              String operator,
                                              Function<CostCalcInputBatchCreateBo, Map<String, Object>> inputBatchCreator) {
        Map<String, Object> fetchResult = payloadFetchService.fetch(profile, requestPayloadJson);
        List<Map<String, Object>> rawRecords = fetchSupport.extractResponseRecords(profile, fetchResult.get("responsePayload"));
        Map<String, Object> preview = inputMappingService.buildMappedInputResult(context, rawRecords, 1);

        CostCalcInputBatchCreateBo createBo = new CostCalcInputBatchCreateBo();
        createBo.setSceneId(profile.getSceneId());
        createBo.setVersionId(profile.getVersionId());
        createBo.setBillMonth(billMonth);
        createBo.setInputJson(writeJsonString(preview.get("mappedRecords")));
        createBo.setRemark(remark);
        Map<String, Object> batchResult = inputBatchCreator.apply(createBo);
        progressService.bindTerminal(batchResult.get("batch"), profile.getProfileId(), requestPayloadJson, fetchConfig, operator);
        return resultAssembler.assembleNonPaged(batchResult, fetchResult, rawRecords, preview);
    }

    public AccessProfileFetchConfig buildFetchConfig(CostAccessProfile profile) {
        return fetchSupport.build(profile);
    }

    public Map<String, Object> preview(CostAccessProfile profile, InputBuildContext context, String requestPayloadJson) {
        Map<String, Object> fetchResult = payloadFetchService.fetch(profile, requestPayloadJson);
        List<Map<String, Object>> rawRecords = fetchSupport.extractResponseRecords(profile, fetchResult.get("responsePayload"));
        Map<String, Object> preview = inputMappingService.buildMappedInputResult(context, rawRecords, 1);
        preview.put("accessProfile", summarize(profile));
        preview.put("fetchMeta", fetchResult.get("fetchMeta"));
        preview.put("requestPayloadJson", fetchResult.get("requestPayload"));
        preview.put("fetchedPayloadJson", fetchResult.get("responsePayload"));
        preview.put("recordsPayloadJson", rawRecords);
        preview.put("message", buildProfileFetchPreviewMessage(profile, preview));
        return preview;
    }

    public Map<String, Object> createPaged(CostAccessProfile profile,
                                           InputBuildContext context,
                                           AccessProfileFetchConfig fetchConfig,
                                           Long sceneId,
                                           Long versionId,
                                           String batchNo,
                                           String billMonth,
                                           String requestPayloadJson,
                                           String remark,
                                           String operator,
                                           Function<Long, Map<String, Object>> batchDetailLoader,
                                           int previewLimit) {
        CostCalcInputBatch batch = inputBatchService.createShell(sceneId, versionId, batchNo, billMonth, remark,
                ACCESS_SOURCE_TYPE_HTTP_API, profile.getProfileId(), INPUT_BATCH_STATUS_LOADING, operator);
        AccessProfileFetchCheckpoint checkpoint = fetchSupport.initialCheckpoint(
                requestPayloadJson, profile.getSamplePayloadJson(), fetchConfig);
        progressService.persist(batch, checkpoint, INPUT_BATCH_STATUS_LOADING, "", remark, operator);
        return continuePaged(profile, context, batch, checkpoint, remark, fetchConfig, false, operator,
                batchDetailLoader, previewLimit);
    }

    public Map<String, Object> resumePaged(CostAccessProfile profile,
                                           InputBuildContext context,
                                           CostAccessProfileBuildBatchBo bo,
                                           String remark,
                                           AccessProfileFetchConfig fetchConfig,
                                           String operator,
                                           Function<Long, Map<String, Object>> batchDetailLoader,
                                           int previewLimit) {
        CostCalcInputBatch batch = inputBatchService.requireResumeBatch(profile, bo.getResumeBatchId(), bo.getBillMonth());
        AccessProfileFetchCheckpoint checkpoint = fetchSupport.parseCheckpoint(batch.getCheckpointJson());
        if (!Boolean.TRUE.equals(checkpoint.hasMore)) {
            throw new ServiceException("当前导入批次没有待继续的分页游标");
        }
        if (StringUtils.isNotEmpty(StringUtils.trim(bo.getRequestPayloadJson()))) {
            checkpoint.requestPayloadJson = StringUtils.trim(bo.getRequestPayloadJson());
        }
        return continuePaged(profile, context, batch, checkpoint, firstNonBlank(remark, batch.getRemark()),
                fetchConfig, true, operator, batchDetailLoader, previewLimit);
    }

    public Map<String, Object> complete(Map<String, Object> batchResult, Map<String, Object> accessProfile) {
        return resultAssembler.completeEnvelope(batchResult, accessProfile);
    }

    public Map<String, Object> summarize(CostAccessProfile profile) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("profileId", profile.getProfileId());
        summary.put("profileCode", profile.getProfileCode());
        summary.put("profileName", profile.getProfileName());
        summary.put("sceneId", profile.getSceneId());
        summary.put("sceneCode", profile.getSceneCode());
        summary.put("sceneName", profile.getSceneName());
        summary.put("feeScopeType", profile.getFeeScopeType());
        summary.put("feeIds", profile.getFeeIds());
        summary.put("feeIdsJson", profile.getFeeIdsJson());
        summary.put("feeId", profile.getFeeId());
        summary.put("feeCode", profile.getFeeCode());
        summary.put("feeName", profile.getFeeName());
        summary.put("versionId", profile.getVersionId());
        summary.put("versionNo", profile.getVersionNo());
        summary.put("sourceType", profile.getSourceType());
        summary.put("taskType", profile.getTaskType());
        summary.put("requestMethod", profile.getRequestMethod());
        summary.put("endpointUrl", profile.getEndpointUrl());
        summary.put("authType", profile.getAuthType());
        return summary;
    }

    private Map<String, Object> continuePaged(CostAccessProfile profile,
                                              InputBuildContext context,
                                              CostCalcInputBatch batch,
                                              AccessProfileFetchCheckpoint checkpoint,
                                              String remark,
                                              AccessProfileFetchConfig fetchConfig,
                                              boolean resumed,
                                              String operator,
                                              Function<Long, Map<String, Object>> batchDetailLoader,
                                              int previewLimit) {
        AccessProfileFetchSession session = pagedBatchOrchestrator.loadWithCheckpoint(
                profile, context, batch, checkpoint, fetchConfig,
                firstNonBlank(StringUtils.trim(checkpoint.requestPayloadJson), StringUtils.trim(profile.getSamplePayloadJson())),
                Math.max(NumberUtils.toInt(String.valueOf(batch.getTotalCount()), 0), inputBatchService.countItems(batch.getBatchId())),
                firstNonBlank(remark, batch.getRemark()), operator, previewLimit);
        Map<String, Object> result = batchDetailLoader.apply(batch.getBatchId());
        return resultAssembler.assemblePaged(result, profile, context, checkpoint, fetchConfig, session, session.hasMore, resumed);
    }

    private String writeJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 序列化失败");
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    private String buildProfileFetchPreviewMessage(CostAccessProfile profile, Map<String, Object> preview) {
        return "已按接入方案 " + profile.getProfileName() + " 拉取业务接口并完成标准计费对象预演，共生成 "
                + NumberUtils.toInt(String.valueOf(preview.get("mappedRecordCount"))) + " 条标准对象";
    }
}
