package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.system.domain.cost.CostPublishVersion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable-ish context for publish precheck rules.
 */
public class PublishValidationContext {
    private Long sceneId;
    private Map<String, Object> sceneSnapshot = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> feesByCode = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> variablesByCode = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> formulasByCode = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> rulesByCode = new LinkedHashMap<>();
    private Map<String, java.util.List<Map<String, Object>>> ruleConditionsByRuleCode = new LinkedHashMap<>();
    private String draftSnapshotHash;
    private CostPublishVersion activeVersion;
    private int impactedFeeCount;

    public Long getSceneId() {
        return sceneId;
    }

    public void setSceneId(Long sceneId) {
        this.sceneId = sceneId;
    }

    public Map<String, Object> getSceneSnapshot() {
        return sceneSnapshot == null ? Collections.emptyMap() : sceneSnapshot;
    }

    public void setSceneSnapshot(Map<String, Object> sceneSnapshot) {
        this.sceneSnapshot = sceneSnapshot;
    }

    public Map<String, Map<String, Object>> getFeesByCode() {
        return feesByCode == null ? Collections.emptyMap() : feesByCode;
    }

    public void setFeesByCode(Map<String, Map<String, Object>> feesByCode) {
        this.feesByCode = feesByCode;
    }

    public Map<String, Map<String, Object>> getVariablesByCode() {
        return variablesByCode == null ? Collections.emptyMap() : variablesByCode;
    }

    public void setVariablesByCode(Map<String, Map<String, Object>> variablesByCode) {
        this.variablesByCode = variablesByCode;
    }

    public Map<String, Map<String, Object>> getFormulasByCode() {
        return formulasByCode == null ? Collections.emptyMap() : formulasByCode;
    }

    public void setFormulasByCode(Map<String, Map<String, Object>> formulasByCode) {
        this.formulasByCode = formulasByCode;
    }

    public Map<String, Map<String, Object>> getRulesByCode() {
        return rulesByCode == null ? Collections.emptyMap() : rulesByCode;
    }

    public void setRulesByCode(Map<String, Map<String, Object>> rulesByCode) {
        this.rulesByCode = rulesByCode;
    }

    public Map<String, java.util.List<Map<String, Object>>> getRuleConditionsByRuleCode() {
        return ruleConditionsByRuleCode == null ? Collections.emptyMap() : ruleConditionsByRuleCode;
    }

    public void setRuleConditionsByRuleCode(Map<String, java.util.List<Map<String, Object>>> ruleConditionsByRuleCode) {
        this.ruleConditionsByRuleCode = ruleConditionsByRuleCode;
    }

    public CostPublishVersion getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(CostPublishVersion activeVersion) {
        this.activeVersion = activeVersion;
    }

    public String getDraftSnapshotHash() {
        return draftSnapshotHash;
    }

    public void setDraftSnapshotHash(String draftSnapshotHash) {
        this.draftSnapshotHash = draftSnapshotHash;
    }

    public int getImpactedFeeCount() {
        return impactedFeeCount;
    }

    public void setImpactedFeeCount(int impactedFeeCount) {
        this.impactedFeeCount = impactedFeeCount;
    }
}
