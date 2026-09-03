package com.ruoyi.system.service.cost.execution.model;

import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RuleMatchResult {
    public CostRunServiceImpl.RuntimeRule rule;
    public CostRunServiceImpl.RuntimeTier tier;
    public Integer matchedGroupNo;
    public List<Map<String, Object>> conditionExplain = Collections.emptyList();
    public List<Map<String, Object>> ruleEvaluations = new ArrayList<>();
}
