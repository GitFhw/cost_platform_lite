package com.ruoyi.system.service.cost.remote;

import com.ruoyi.system.domain.cost.CostAccessProfile;
import com.ruoyi.system.service.cost.remote.AccessProfileInputMappingService.InputBuildContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Assembles access-profile batch responses for preview and resume flows.
 */
@Service
public class AccessProfileBatchResultAssembler {
    private final AccessProfileFetchSupport fetchSupport;

    public AccessProfileBatchResultAssembler(AccessProfileFetchSupport fetchSupport) {
        this.fetchSupport = fetchSupport;
    }

    public Map<String, Object> assemblePaged(Map<String, Object> baseResult,
                                             CostAccessProfile profile,
                                             InputBuildContext context,
                                             AccessProfileFetchCheckpoint checkpoint,
                                             AccessProfileFetchConfig fetchConfig,
                                             AccessProfileFetchSession session,
                                             boolean resumable,
                                             boolean resumed) {
        putCommon(baseResult, profile, context, fetchConfig, session, resumable, resumed);
        baseResult.put("requestPayloadJson", session.firstRequestPayload == null ? checkpoint.requestPayloadJson : session.firstRequestPayload);
        baseResult.put("checkpoint", checkpoint);
        baseResult.put("resumable", resumable);
        baseResult.put("resumed", resumed);
        return baseResult;
    }

    public Map<String, Object> assembleNonPaged(Map<String, Object> baseResult,
                                                Map<String, Object> fetchResult,
                                                List<Map<String, Object>> rawRecords,
                                                Map<String, Object> preview) {
        baseResult.put("fetchMeta", fetchResult.get("fetchMeta"));
        baseResult.put("requestPayloadJson", fetchResult.get("requestPayload"));
        baseResult.put("fetchedPayloadJson", fetchResult.get("responsePayload"));
        baseResult.put("recordsPayloadJson", rawRecords);
        baseResult.put("mappedRecordCount", preview.get("mappedRecordCount"));
        baseResult.put("mappedRecords", preview.get("mappedRecords"));
        baseResult.put("missingPaths", preview.get("missingPaths"));
        baseResult.put("fieldMappings", preview.get("fieldMappings"));
        return baseResult;
    }

    public Map<String, Object> completeEnvelope(Map<String, Object> result, Map<String, Object> accessProfile) {
        result.put("accessProfile", accessProfile);
        if (Boolean.TRUE.equals(result.get("resumable"))) {
            result.put("message", "已按接入方案继续装载导入批次，当前仍有后续分页待继续拉取");
        } else {
            result.put("message", "已按接入方案拉取业务接口并生成导入批次，可继续发起正式核算");
        }
        return result;
    }

    private void putCommon(Map<String, Object> result,
                           CostAccessProfile profile,
                           InputBuildContext context,
                           AccessProfileFetchConfig fetchConfig,
                           AccessProfileFetchSession session,
                           boolean maxPageReached,
                           Boolean resumed) {
        result.put("fetchMeta", fetchSupport.buildFetchMeta(
                fetchConfig, session.pageSummaries, session.totalRawCount, session.responseTotal, maxPageReached, resumed));
        result.put("fetchedPayloadJson", session.firstResponsePayload);
        result.put("recordsPayloadJson", session.firstResponsePayload == null
                ? Collections.emptyList()
                : fetchSupport.extractResponseRecords(profile, session.firstResponsePayload));
        result.put("mappedRecordCount", session.totalMappedCount);
        result.put("mappedRecords", session.previewMappedRecords);
        result.put("missingPaths", new ArrayList<>(session.missingPaths));
        result.put("fieldMappings", context.fieldMappings);
    }
}
