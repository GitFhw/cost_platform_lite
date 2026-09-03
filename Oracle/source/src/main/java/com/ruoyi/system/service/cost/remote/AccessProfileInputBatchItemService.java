package com.ruoyi.system.service.cost.remote;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.domain.cost.CostCalcInputBatchItem;
import com.ruoyi.system.mapper.cost.CostCalcInputBatchItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Persists access-profile mapped input items into an input batch.
 */
@Service
public class AccessProfileInputBatchItemService {
    private static final int DEFAULT_INPUT_BATCH_INSERT_SIZE = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CostCalcInputBatchItemMapper calcInputBatchItemMapper;

    public int appendItems(CostCalcInputBatch batch, List<Map<String, Object>> inputs, int startItemNo,
                           Set<String> seenBizNos) {
        if (inputs == null || inputs.isEmpty()) {
            return 0;
        }
        List<String> currentPageBizNos = new ArrayList<>(inputs.size());
        int probeItemNo = startItemNo;
        for (Map<String, Object> input : inputs) {
            probeItemNo++;
            String bizNo = resolveBizNo(input, probeItemNo);
            if (seenBizNos != null && !seenBizNos.add(bizNo)) {
                throw new ServiceException("分页装载时发现重复业务单号：" + bizNo);
            }
            currentPageBizNos.add(bizNo);
        }
        if (batch.getBatchId() != null) {
            List<String> existedBizNos = calcInputBatchItemMapper.selectList(Wrappers.<CostCalcInputBatchItem>lambdaQuery()
                            .eq(CostCalcInputBatchItem::getBatchId, batch.getBatchId())
                            .in(CostCalcInputBatchItem::getBizNo, currentPageBizNos)
                            .select(CostCalcInputBatchItem::getBizNo))
                    .stream()
                    .map(CostCalcInputBatchItem::getBizNo)
                    .filter(StringUtils::isNotEmpty)
                    .distinct()
                    .collect(Collectors.toList());
            if (!existedBizNos.isEmpty()) {
                throw new ServiceException("分页装载时发现批次内已存在业务单号：" + String.join(", ", existedBizNos));
            }
        }
        List<CostCalcInputBatchItem> buffer = new ArrayList<>(DEFAULT_INPUT_BATCH_INSERT_SIZE);
        int itemNo = startItemNo;
        for (Map<String, Object> input : inputs) {
            itemNo++;
            String bizNo = resolveBizNo(input, itemNo);
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
        return inputs.size();
    }

    private String resolveBizNo(Map<String, Object> input, int fallbackNo) {
        String bizNo = resolveString(input, "bizNo", "biz_no", "businessNo", "business_no");
        return StringUtils.isNotEmpty(bizNo) ? bizNo : "BIZ-" + String.format(Locale.ROOT, "%03d", fallbackNo);
    }

    private String resolveString(Map<String, Object> input, String... keys) {
        if (input == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = resolveByPath(input, key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private Object resolveByPath(Object input, String path) {
        if (!(input instanceof Map)) {
            return null;
        }
        return resolveByPath(castMap(input), path);
    }

    private Object resolveByPath(Map<String, Object> input, String path) {
        if (input == null || StringUtils.isEmpty(path)) {
            return null;
        }
        String[] pieces = path.split("\\.");
        Object current = input;
        for (String piece : pieces) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(piece);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : java.util.Collections.emptyMap();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 序列化失败");
        }
    }
}
