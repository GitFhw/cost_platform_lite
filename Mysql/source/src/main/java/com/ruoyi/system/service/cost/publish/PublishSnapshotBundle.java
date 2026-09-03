package com.ruoyi.system.service.cost.publish;

import com.ruoyi.system.domain.cost.CostPublishSnapshot;

import java.util.*;

public class PublishSnapshotBundle {
    public Long sceneId;
    public String sceneCode;
    public String sceneName;
    public String snapshotHash;
    public Map<String, Object> sceneSnapshot = new LinkedHashMap<>();
    public Map<String, Map<String, Object>> feesByCode = new LinkedHashMap<>();
    public Map<String, Map<String, Object>> variablesByCode = new LinkedHashMap<>();
    public Map<String, Map<String, Object>> formulasByCode = new LinkedHashMap<>();
    public Map<String, Map<String, Object>> rulesByCode = new LinkedHashMap<>();
    public Map<String, List<Map<String, Object>>> ruleConditionsByRuleCode = new LinkedHashMap<>();
    public Map<String, List<Map<String, Object>>> ruleTiersByRuleCode = new LinkedHashMap<>();
    public Map<String, String> ruleToFeeCode = new LinkedHashMap<>();
    public Map<String, Set<String>> feeRuleCodes = new LinkedHashMap<>();
    public Map<String, Set<String>> feeReferencedVariables = new LinkedHashMap<>();
    public List<CostPublishSnapshot> snapshotRows = new ArrayList<>();
}
