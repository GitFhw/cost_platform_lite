package com.ruoyi.system.service.cost.remote;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.service.cost.remote.AccessProfileInputMappingService.InputBuildContext;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.Map;

/**
 * Orchestrates paged access-profile loading while keeping checkpoint persistence explicit.
 */
@Service
public class AccessProfilePagedBatchOrchestrator {
    private final AccessProfileFetchPageProcessor pageProcessor;
    private final AccessProfileFetchSupport fetchSupport;
    private final AccessProfileBatchProgressService progressService;

    public AccessProfilePagedBatchOrchestrator(AccessProfileFetchPageProcessor pageProcessor,
                                               AccessProfileFetchSupport fetchSupport,
                                               AccessProfileBatchProgressService progressService) {
        this.pageProcessor = pageProcessor;
        this.fetchSupport = fetchSupport;
        this.progressService = progressService;
    }

    public AccessProfileFetchSession loadWithCheckpoint(CostAccessProfile profile,
                                                        InputBuildContext context,
                                                        CostCalcInputBatch batch,
                                                        AccessProfileFetchCheckpoint checkpoint,
                                                        AccessProfileFetchConfig fetchConfig,
                                                        String requestPayloadJson,
                                                        int existingMappedCount,
                                                        String remark,
                                                        String operator,
                                                        int previewLimit) {
        AccessProfileFetchSession session = AccessProfileFetchSession.resume(
                checkpoint, fetchConfig, requestPayloadJson, existingMappedCount);
        batch.setAccessProfileId(profile.getProfileId());
        batch.setRemark(firstNonBlank(remark, batch.getRemark()));
        progressService.persist(batch, checkpoint, INPUT_BATCH_STATUS_LOADING, "", batch.getRemark(), operator);
        try {
            while (session.hasMore && session.pageSummaries.size() < fetchConfig.maxPages) {
                Map<String, Object> pageMeta = pageProcessor.process(profile, context, batch, fetchConfig, session, previewLimit);
                fetchSupport.advanceCheckpoint(checkpoint, fetchConfig, session.requestPayloadJson, session.responseTotal,
                        session.totalFetchedPageCount, session.totalRawCount, session.totalMappedCount, session.pageNo,
                        session.nextCursor, session.hasMore, pageMeta);
                progressService.persist(batch, checkpoint, INPUT_BATCH_STATUS_LOADING, "", batch.getRemark(), operator);
                session.pageNo++;
            }
        } catch (Exception e) {
            fetchSupport.markFailed(checkpoint, session.pageNo, session.nextCursor);
            progressService.persist(batch, checkpoint, INPUT_BATCH_STATUS_PARTIAL, limitLength(e.getMessage(), 1000),
                    batch.getRemark(), operator);
            throw new ServiceException("分页拉取中途失败，已保留当前批次 " + batch.getBatchNo() + "，可稍后继续装载："
                    + limitLength(e.getMessage(), 200));
        }

        assertMappedRecords(session);
        progressService.persist(batch, checkpoint,
                session.hasMore ? INPUT_BATCH_STATUS_PARTIAL : INPUT_BATCH_STATUS_READY, "", batch.getRemark(), operator);
        return session;
    }

    private void assertMappedRecords(AccessProfileFetchSession session) {
        if (session.totalMappedCount <= 0) {
            throw new ServiceException("按接入方案分页拉取后未生成任何标准计费对象，无法创建导入批次");
        }
    }

    private String firstNonBlank(String first, String second) {
        return com.ruoyi.common.utils.StringUtils.isNotEmpty(first) ? first : second;
    }

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
