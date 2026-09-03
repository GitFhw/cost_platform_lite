package com.ruoyi.system.service.impl.cost;

import com.ruoyi.common.utils.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

class CostResultCompareSource {
    String sourceType;
    String sourceName;
    String sourceNo;
    Long taskId;
    Long simulationId;
    Long sceneId;
    String sceneName;
    Long versionId;
    String versionNo;
    String billMonth;
    long resultCount;
    BigDecimal amountTotal = BigDecimal.ZERO;
    final Map<String, CostResultCompareFeeAggregate> fees = new LinkedHashMap<>();
    /** 按对象维度聚合：key = objectDimension::objectCode */
    final Map<String, CostResultCompareObjectAggregate> objects = new LinkedHashMap<>();
    /** 按规则命中聚合：key = feeCode::ruleId */
    final Map<String, CostResultCompareRuleAggregate> rules = new LinkedHashMap<>();

    void addFee(String feeCode, String feeName, BigDecimal amountValue, long count) {
        String key = StringUtils.isNotEmpty(feeCode) ? feeCode : "UNKNOWN";
        CostResultCompareFeeAggregate aggregate = fees.computeIfAbsent(key, code -> {
            CostResultCompareFeeAggregate item = new CostResultCompareFeeAggregate();
            item.feeCode = code;
            item.feeName = StringUtils.isNotEmpty(feeName) ? feeName : code;
            return item;
        });
        aggregate.feeName = StringUtils.isNotEmpty(aggregate.feeName) ? aggregate.feeName : feeName;
        aggregate.amountTotal = aggregate.amountTotal.add(amountValue == null ? BigDecimal.ZERO : amountValue);
        aggregate.resultCount += count;
    }

    void addObject(String objectDimension, String objectCode, String objectName, BigDecimal amountValue, long count) {
        String dimPart = StringUtils.isNotEmpty(objectDimension) ? objectDimension : "-";
        String codePart = StringUtils.isNotEmpty(objectCode) ? objectCode : "UNKNOWN";
        String key = dimPart + "::" + codePart;
        CostResultCompareObjectAggregate aggregate = objects.computeIfAbsent(key, k -> {
            CostResultCompareObjectAggregate item = new CostResultCompareObjectAggregate();
            item.objectDimension = dimPart;
            item.objectCode = codePart;
            item.objectName = StringUtils.isNotEmpty(objectName) ? objectName : codePart;
            return item;
        });
        aggregate.objectName = StringUtils.isNotEmpty(aggregate.objectName) ? aggregate.objectName : objectName;
        aggregate.amountTotal = aggregate.amountTotal.add(amountValue == null ? BigDecimal.ZERO : amountValue);
        aggregate.resultCount += count;
    }

    void addRule(String feeCode, String feeName, Long ruleId, BigDecimal amountValue, long count) {
        String feePart = StringUtils.isNotEmpty(feeCode) ? feeCode : "UNKNOWN";
        String rulePart = ruleId != null ? String.valueOf(ruleId) : "0";
        String key = feePart + "::" + rulePart;
        CostResultCompareRuleAggregate aggregate = rules.computeIfAbsent(key, k -> {
            CostResultCompareRuleAggregate item = new CostResultCompareRuleAggregate();
            item.feeCode = feePart;
            item.feeName = StringUtils.isNotEmpty(feeName) ? feeName : feePart;
            item.ruleId = ruleId;
            return item;
        });
        aggregate.amountTotal = aggregate.amountTotal.add(amountValue == null ? BigDecimal.ZERO : amountValue);
        aggregate.resultCount += count;
    }
}
