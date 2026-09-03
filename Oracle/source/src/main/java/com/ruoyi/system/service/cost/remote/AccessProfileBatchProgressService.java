package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.mapper.cost.CostCalcInputBatchMapper;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Persists access-profile input-batch progress and binding metadata.
 */
@Service
public class AccessProfileBatchProgressService {
    private final CostCalcInputBatchMapper calcInputBatchMapper;
    private final AccessProfileFetchSupport fetchSupport;
    private final ObjectMapper objectMapper;

    public AccessProfileBatchProgressService(CostCalcInputBatchMapper calcInputBatchMapper,
                                             AccessProfileFetchSupport fetchSupport) {
        this.calcInputBatchMapper = calcInputBatchMapper;
        this.fetchSupport = fetchSupport;
        this.objectMapper = new ObjectMapper();
    }

    public void persist(CostCalcInputBatch batch, AccessProfileFetchCheckpoint checkpoint, String batchStatus,
                        String errorMessage, String remark, String operator) {
        batch.setBatchStatus(firstNonBlank(batchStatus, batch.getBatchStatus()));
        batch.setCheckpointJson(writeJson(checkpoint));
        batch.setTotalCount(checkpoint.mappedRecordCount == null ? batch.getTotalCount() : checkpoint.mappedRecordCount);
        batch.setValidCount(checkpoint.mappedRecordCount == null ? batch.getValidCount() : checkpoint.mappedRecordCount);
        batch.setErrorCount(0);
        batch.setRemark(remark);
        batch.setErrorMessage(firstNonBlank(errorMessage, ""));
        batch.setUpdateBy(operator);
        batch.setUpdateTime(DateUtils.getNowDate());
        calcInputBatchMapper.updateById(batch);
    }

    public void bindTerminal(Object batchPayload, Long profileId, String requestPayloadJson,
                             AccessProfileFetchConfig fetchConfig, String operator) {
        Object batchIdValue = batchPayload instanceof CostCalcInputBatch
                ? ((CostCalcInputBatch) batchPayload).getBatchId()
                : batchPayload instanceof Map ? castMap(batchPayload).get("batchId") : null;
        if (batchIdValue == null) {
            return;
        }
        CostCalcInputBatch batch = calcInputBatchMapper.selectById(NumberUtils.toLong(String.valueOf(batchIdValue)));
        if (batch == null) {
            return;
        }
        AccessProfileFetchCheckpoint checkpoint = fetchSupport.terminalCheckpoint(
                StringUtils.trim(requestPayloadJson), fetchConfig, batch.getTotalCount());
        batch.setAccessProfileId(profileId);
        persist(batch, checkpoint, batch.getBatchStatus(), "", batch.getRemark(), operator);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 序列化失败");
        }
    }
}
