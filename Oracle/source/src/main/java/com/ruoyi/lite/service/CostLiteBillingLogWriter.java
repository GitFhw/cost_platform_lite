package com.ruoyi.lite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.system.domain.cost.CostSimulationRecord;
import com.ruoyi.system.domain.cost.bo.CostFeeCalculateBo;
import com.ruoyi.system.mapper.cost.CostSimulationRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;

/**
 * 将同步计费调用写入母体已有试算记录表。
 */
@Service
public class CostLiteBillingLogWriter {
    private final CostSimulationRecordMapper simulationRecordMapper;
    private final CostLiteProperties properties;
    private final ObjectMapper objectMapper;

    public CostLiteBillingLogWriter(CostSimulationRecordMapper simulationRecordMapper,
                                    CostLiteProperties properties,
                                    ObjectMapper objectMapper) {
        this.simulationRecordMapper = simulationRecordMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "costLiteTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public CostSimulationRecord writeSuccess(CostFeeCalculateBo request, Map<String, Object> result) {
        if (!properties.isPersistBillingLog()) {
            return null;
        }
        CostSimulationRecord record = baseRecord(request, result);
        record.setStatus(resolveStatus(result));
        record.setVariableJson(writeJson(Collections.emptyMap()));
        record.setExplainJson(writeJson(buildExplain(result)));
        record.setResultJson(writeJson(result));
        record.setErrorMessage(resolveErrorMessage(result));
        simulationRecordMapper.insert(record);
        return record;
    }

    @Transactional(transactionManager = "costLiteTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public CostSimulationRecord writeFailure(CostFeeCalculateBo request, Throwable throwable) {
        if (!properties.isPersistBillingLog()) {
            return null;
        }
        CostSimulationRecord record = baseRecord(request, null);
        record.setStatus("FAILED");
        String message = limitLength(throwable == null ? "计费执行失败" : throwable.getMessage(), 1000);
        record.setVariableJson(writeJson(Collections.emptyMap()));
        record.setExplainJson(writeJson(Collections.singletonMap("error", message)));
        record.setResultJson(writeJson(Collections.emptyMap()));
        record.setErrorMessage(message);
        simulationRecordMapper.insert(record);
        return record;
    }

    private CostSimulationRecord baseRecord(CostFeeCalculateBo request, Map<String, Object> result) {
        CostSimulationRecord record = new CostSimulationRecord();
        record.setSceneId(request == null ? null : request.getSceneId());
        record.setVersionId(resolveLong(result, "versionId", request == null ? null : request.getVersionId()));
        record.setBillMonth(request == null ? "" : StringUtils.defaultString(request.getBillMonth()));
        record.setSimulationNo("LITE-" + IdUtils.fastSimpleUUID());
        record.setInputJson(normalizeInputJson(request == null ? null : request.getInputJson()));
        record.setCreateBy(resolveOperator());
        record.setCreateTime(DateUtils.getNowDate());
        return record;
    }

    /**
     * MySQL 的 JSON 列会拒绝格式错误的原始报文。失败日志仍需保留该报文，
     * 因此将其包装为合法 JSON，避免日志写入再次失败并丢失原始输入。
     */
    private String normalizeInputJson(String inputJson) {
        String value = StringUtils.defaultIfEmpty(inputJson, "{}");
        try {
            objectMapper.readTree(value);
            return value;
        } catch (Exception ignored) {
            LinkedHashMap<String, Object> invalidPayload = new LinkedHashMap<>();
            invalidPayload.put("invalidJson", true);
            invalidPayload.put("rawInput", value);
            return writeJson(invalidPayload);
        }
    }

    private Map<String, Object> buildExplain(Map<String, Object> result) {
        LinkedHashMap<String, Object> explain = new LinkedHashMap<>();
        if (result != null) {
            explain.put("includeExplain", result.get("includeExplain"));
            explain.put("targetFeeCodes", result.get("targetFeeCodes"));
            explain.put("dependentFeeCodes", result.get("dependentFeeCodes"));
            explain.put("recordCount", result.get("recordCount"));
        }
        return explain;
    }

    private String resolveStatus(Map<String, Object> result) {
        return result == null ? "FAILED" : (hasFailedRecords(result) ? "FAILED" : "SUCCESS");
    }

    private String resolveErrorMessage(Map<String, Object> result) {
        if (result == null) {
            return "";
        }
        return hasFailedRecords(result)
                ? "本次调用包含失败记录，详细结果请查看 resultJson"
                : "";
    }

    /**
     * 失败数量来自 JSON 结果，可能是数字、数字字符串或空值；解析失败时按无失败记录处理，
     * 避免日志写入路径因为非标准响应再次抛出 NumberFormatException。
     */
    private boolean hasFailedRecords(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        Object failedCount = result.get("failedCount");
        if (failedCount == null) {
            return false;
        }
        try {
            BigDecimal count = failedCount instanceof Number
                    ? new BigDecimal(String.valueOf(failedCount))
                    : new BigDecimal(String.valueOf(failedCount).trim());
            return count.compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private Long resolveLong(Map<String, Object> result, String key, Long fallback) {
        if (result == null || result.get(key) == null) {
            return fallback;
        }
        try {
            return Long.valueOf(String.valueOf(result.get(key)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String resolveOperator() {
        try {
            return StringUtils.defaultIfEmpty(SecurityUtils.getUsername(), properties.getOperator());
        } catch (Exception ignored) {
            return StringUtils.defaultIfEmpty(properties.getOperator(), "lite-admin");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
