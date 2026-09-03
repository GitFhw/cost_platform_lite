package com.ruoyi.system.service.cost.publish;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.cost.publish.validation.PublishJsonSupport;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PublishSnapshotViewService {

    public PublishSnapshotBundle normalizeBundle(PublishSnapshotBundle bundle) {
        return bundle == null ? new PublishSnapshotBundle() : bundle;
    }

    public boolean isSnapshotEquivalent(PublishSnapshotBundle fromBundle, PublishSnapshotBundle toBundle) {
        fromBundle = normalizeBundle(fromBundle);
        toBundle = normalizeBundle(toBundle);
        if (StringUtils.isEmpty(fromBundle.snapshotHash) || StringUtils.isEmpty(toBundle.snapshotHash)) {
            return false;
        }
        return StringUtils.equals(fromBundle.snapshotHash, toBundle.snapshotHash);
    }

    public List<Map<String, Object>> buildFeeDiffSummary(PublishSnapshotBundle fromBundle,
                                                         PublishSnapshotBundle toBundle,
                                                         String feeCode) {
        fromBundle = normalizeBundle(fromBundle);
        toBundle = normalizeBundle(toBundle);
        if (isSnapshotEquivalent(fromBundle, toBundle)) {
            return new ArrayList<>();
        }
        Set<String> feeCodes = new TreeSet<>();
        feeCodes.addAll(fromBundle.feesByCode.keySet());
        feeCodes.addAll(toBundle.feesByCode.keySet());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String code : feeCodes) {
            if (StringUtils.isNotEmpty(feeCode) && !StringUtils.equals(code, feeCode)) {
                continue;
            }
            Map<String, Object> fromFee = fromBundle.feesByCode.get(code);
            Map<String, Object> toFee = toBundle.feesByCode.get(code);
            String changeType = determineChangeType(fromFee, toFee);
            List<Map<String, Object>> changedRules = buildChangedRuleList(fromBundle, toBundle, code);
            List<Map<String, Object>> changedVariables = buildChangedVariableList(fromBundle, toBundle, code);
            int ruleChangeCount = changedRules.size();
            int variableChangeCount = changedVariables.size();
            boolean feeDirectChanged = !"UNCHANGED".equals(changeType);
            if (!feeDirectChanged && ruleChangeCount == 0 && variableChangeCount == 0) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("feeCode", code);
            item.put("feeName", firstNonBlank(
                    stringValue(firstNonNull(fromFee == null ? null : fromFee.get("feeName"), toFee == null ? null : toFee.get("feeName"))),
                    stringValue(firstNonNull(fromFee == null ? null : fromFee.get("feeCode"), toFee == null ? null : toFee.get("feeCode")))));
            item.put("changeType", changeType);
            item.put("changedFields", compareChangedFields(fromFee, toFee));
            item.put("ruleChangeCount", ruleChangeCount);
            item.put("variableChangeCount", variableChangeCount);
            item.put("summary", buildFeeSummaryText(changeType, feeDirectChanged, ruleChangeCount, variableChangeCount));
            item.put("changedRules", changedRules);
            item.put("changedVariables", changedVariables);
            item.put("fromFee", fromFee);
            item.put("toFee", toFee);
            item.put("fromRules", buildFeeRuleCompositeList(fromBundle, code));
            item.put("toRules", buildFeeRuleCompositeList(toBundle, code));
            item.put("fromVariables", buildFeeVariableList(fromBundle, code));
            item.put("toVariables", buildFeeVariableList(toBundle, code));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> buildRuleDiffSummary(PublishSnapshotBundle fromBundle,
                                                          PublishSnapshotBundle toBundle,
                                                          String feeCode) {
        fromBundle = normalizeBundle(fromBundle);
        toBundle = normalizeBundle(toBundle);
        if (isSnapshotEquivalent(fromBundle, toBundle)) {
            return new ArrayList<>();
        }
        Set<String> ruleCodes = new TreeSet<>();
        if (StringUtils.isEmpty(feeCode)) {
            ruleCodes.addAll(fromBundle.rulesByCode.keySet());
            ruleCodes.addAll(toBundle.rulesByCode.keySet());
        } else {
            ruleCodes.addAll(fromBundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>()));
            ruleCodes.addAll(toBundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>()));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ruleCode : ruleCodes) {
            Map<String, Object> fromRule = fromBundle.rulesByCode.get(ruleCode);
            Map<String, Object> toRule = toBundle.rulesByCode.get(ruleCode);
            String changeType = determineChangeType(fromRule, toRule);
            int conditionChangeCount = countCollectionDiff(
                    fromBundle == null ? null : fromBundle.ruleConditionsByRuleCode.get(ruleCode),
                    toBundle == null ? null : toBundle.ruleConditionsByRuleCode.get(ruleCode));
            int tierChangeCount = countCollectionDiff(
                    fromBundle == null ? null : fromBundle.ruleTiersByRuleCode.get(ruleCode),
                    toBundle == null ? null : toBundle.ruleTiersByRuleCode.get(ruleCode));
            if ("UNCHANGED".equals(changeType) && conditionChangeCount == 0 && tierChangeCount == 0) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("ruleCode", ruleCode);
            item.put("ruleName", firstNonBlank(
                    stringValue(firstNonNull(fromRule == null ? null : fromRule.get("ruleName"), toRule == null ? null : toRule.get("ruleName"))),
                    stringValue(firstNonNull(fromRule == null ? null : fromRule.get("ruleCode"), toRule == null ? null : toRule.get("ruleCode")))));
            item.put("feeCode", firstNonBlank(
                    stringValue(firstNonNull(fromRule == null ? null : fromRule.get("feeCode"), toRule == null ? null : toRule.get("feeCode"))),
                    stringValue(firstNonNull(fromRule == null ? null : fromRule.get("ruleCode"), toRule == null ? null : toRule.get("ruleCode")))));
            item.put("changeType", changeType);
            item.put("changedFields", compareChangedFields(fromRule, toRule));
            item.put("conditionChangeCount", conditionChangeCount);
            item.put("tierChangeCount", tierChangeCount);
            item.put("fromRule", fromRule);
            item.put("toRule", toRule);
            item.put("fromConditions", normalizeCollection(fromBundle == null ? null : fromBundle.ruleConditionsByRuleCode.get(ruleCode)));
            item.put("toConditions", normalizeCollection(toBundle == null ? null : toBundle.ruleConditionsByRuleCode.get(ruleCode)));
            item.put("fromTiers", normalizeCollection(fromBundle == null ? null : fromBundle.ruleTiersByRuleCode.get(ruleCode)));
            item.put("toTiers", normalizeCollection(toBundle == null ? null : toBundle.ruleTiersByRuleCode.get(ruleCode)));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> buildSceneDiffSummary(Map<String, Object> fromScene, Map<String, Object> toScene) {
        Set<String> fields = new LinkedHashSet<>();
        if (fromScene != null) fields.addAll(fromScene.keySet());
        if (toScene != null) fields.addAll(toScene.keySet());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String field : fields) {
            Object fromValue = fromScene == null ? null : fromScene.get(field);
            Object toValue = toScene == null ? null : toScene.get(field);
            if (Objects.equals(PublishJsonSupport.canonicalJson(fromValue), PublishJsonSupport.canonicalJson(toValue))) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("field", field);
            item.put("fieldLabel", resolveSceneFieldLabel(field));
            item.put("fromValue", fromValue);
            item.put("toValue", toValue);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> buildObjectDiffSummary(Map<String, Map<String, Object>> fromObjects,
                                                            Map<String, Map<String, Object>> toObjects,
                                                            String codeKey,
                                                            String nameKey,
                                                            String codeProperty,
                                                            String nameProperty) {
        Map<String, Map<String, Object>> normalizedFrom = fromObjects == null ? Collections.emptyMap() : fromObjects;
        Map<String, Map<String, Object>> normalizedTo = toObjects == null ? Collections.emptyMap() : toObjects;
        Set<String> objectCodes = new TreeSet<>();
        objectCodes.addAll(normalizedFrom.keySet());
        objectCodes.addAll(normalizedTo.keySet());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String objectCode : objectCodes) {
            Map<String, Object> fromObject = normalizedFrom.get(objectCode);
            Map<String, Object> toObject = normalizedTo.get(objectCode);
            String changeType = determineChangeType(fromObject, toObject);
            if ("UNCHANGED".equals(changeType)) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put(codeProperty, objectCode);
            item.put(nameProperty, firstNonBlank(
                    stringValue(firstNonNull(fromObject == null ? null : fromObject.get(nameKey), toObject == null ? null : toObject.get(nameKey))),
                    stringValue(firstNonNull(fromObject == null ? null : fromObject.get(codeKey), toObject == null ? null : toObject.get(codeKey)))));
            item.put("changeType", changeType);
            item.put("changedFields", compareChangedFields(fromObject, toObject));
            item.put("fromObject", fromObject);
            item.put("toObject", toObject);
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> buildSnapshotCounts(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        LinkedHashMap<String, Object> counts = new LinkedHashMap<>();
        counts.put("scene", bundle.sceneSnapshot.isEmpty() ? 0 : 1);
        counts.put("fee", StringUtils.isEmpty(feeCode) ? bundle.feesByCode.size() : (bundle.feesByCode.containsKey(feeCode) ? 1 : 0));
        counts.put("variable", buildFeeVariableList(bundle, feeCode).size());
        counts.put("formula", bundle.formulasByCode.size());
        counts.put("rule", buildFeeRuleList(bundle, feeCode).size());
        counts.put("condition", buildFeeConditionList(bundle, feeCode).size());
        counts.put("tier", buildFeeTierList(bundle, feeCode).size());
        return counts;
    }

    public Map<String, Object> buildSnapshotGroups(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        LinkedHashMap<String, Object> groups = new LinkedHashMap<>();
        groups.put("scene", bundle.sceneSnapshot);
        groups.put("fees", buildFeeList(bundle, feeCode));
        groups.put("variables", buildFeeVariableList(bundle, feeCode));
        groups.put("formulas", new ArrayList<>(bundle.formulasByCode.values()));
        groups.put("rules", buildFeeRuleList(bundle, feeCode));
        groups.put("conditions", buildFeeConditionList(bundle, feeCode));
        groups.put("tiers", buildFeeTierList(bundle, feeCode));
        return groups;
    }

    public Map<String, Object> buildFeeDigest(PublishSnapshotBundle bundle, String feeCode) {
        if (bundle == null || !bundle.feesByCode.containsKey(feeCode)) {
            return null;
        }
        LinkedHashMap<String, Object> digest = new LinkedHashMap<>();
        digest.put("fee", bundle.feesByCode.get(feeCode));
        digest.put("rules", buildFeeRuleCompositeList(bundle, feeCode));
        digest.put("variables", buildFeeVariableList(bundle, feeCode));
        return digest;
    }

    public Map<String, Object> buildRuleComposite(PublishSnapshotBundle bundle, String ruleCode) {
        if (bundle == null || !bundle.rulesByCode.containsKey(ruleCode)) {
            return null;
        }
        LinkedHashMap<String, Object> composite = new LinkedHashMap<>();
        composite.put("rule", bundle.rulesByCode.get(ruleCode));
        composite.put("conditions", normalizeCollection(bundle.ruleConditionsByRuleCode.get(ruleCode)));
        composite.put("tiers", normalizeCollection(bundle.ruleTiersByRuleCode.get(ruleCode)));
        return composite;
    }

    public List<Map<String, Object>> buildFeeRuleCompositeList(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ruleCode : bundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>())) {
            result.add(buildRuleComposite(bundle, ruleCode));
        }
        return result;
    }

    public int countFeeRuleChanges(PublishSnapshotBundle fromBundle, PublishSnapshotBundle toBundle, String feeCode) {
        return buildChangedRuleList(fromBundle, toBundle, feeCode).size();
    }

    public int countFeeVariableChanges(PublishSnapshotBundle fromBundle, PublishSnapshotBundle toBundle, String feeCode) {
        return buildChangedVariableList(fromBundle, toBundle, feeCode).size();
    }

    public List<Map<String, Object>> buildChangedRuleList(PublishSnapshotBundle fromBundle, PublishSnapshotBundle toBundle, String feeCode) {
        Set<String> ruleCodes = new TreeSet<>();
        if (fromBundle != null) ruleCodes.addAll(fromBundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>()));
        if (toBundle != null) ruleCodes.addAll(toBundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ruleCode : ruleCodes) {
            Map<String, Object> fromRule = fromBundle == null ? null : fromBundle.rulesByCode.get(ruleCode);
            Map<String, Object> toRule = toBundle == null ? null : toBundle.rulesByCode.get(ruleCode);
            String changeType = determineChangeType(buildRuleComposite(fromBundle, ruleCode), buildRuleComposite(toBundle, ruleCode));
            if ("UNCHANGED".equals(changeType)) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("ruleCode", ruleCode);
            item.put("ruleName", firstNonBlank(
                    stringValue(firstNonNull(fromRule == null ? null : fromRule.get("ruleName"), toRule == null ? null : toRule.get("ruleName"))),
                    ruleCode));
            item.put("changeType", changeType);
            item.put("changedFields", compareChangedFields(fromRule, toRule));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> buildChangedVariableList(PublishSnapshotBundle fromBundle, PublishSnapshotBundle toBundle, String feeCode) {
        Set<String> variableCodes = new TreeSet<>();
        if (fromBundle != null)
            variableCodes.addAll(fromBundle.feeReferencedVariables.getOrDefault(feeCode, new TreeSet<>()));
        if (toBundle != null)
            variableCodes.addAll(toBundle.feeReferencedVariables.getOrDefault(feeCode, new TreeSet<>()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String variableCode : variableCodes) {
            Map<String, Object> fromVariable = fromBundle == null ? null : fromBundle.variablesByCode.get(variableCode);
            Map<String, Object> toVariable = toBundle == null ? null : toBundle.variablesByCode.get(variableCode);
            if (!isEquivalentValue(fromVariable, toVariable)) {
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("variableCode", variableCode);
                item.put("variableName", firstNonBlank(
                        stringValue(firstNonNull(fromVariable == null ? null : fromVariable.get("variableName"), toVariable == null ? null : toVariable.get("variableName"))),
                        variableCode));
                item.put("changeType", determineChangeType(fromVariable, toVariable));
                item.put("changedFields", compareChangedFields(fromVariable, toVariable));
                result.add(item);
            }
        }
        return result;
    }

    public int countCollectionDiff(List<Map<String, Object>> fromList, List<Map<String, Object>> toList) {
        List<Map<String, Object>> normalizedFrom = normalizeCollection(fromList);
        List<Map<String, Object>> normalizedTo = normalizeCollection(toList);
        int diffCount = 0;
        int maxSize = Math.max(normalizedFrom.size(), normalizedTo.size());
        for (int index = 0; index < maxSize; index++) {
            Map<String, Object> fromItem = index < normalizedFrom.size() ? normalizedFrom.get(index) : null;
            Map<String, Object> toItem = index < normalizedTo.size() ? normalizedTo.get(index) : null;
            if (!isEquivalentValue(fromItem, toItem)) {
                diffCount++;
            }
        }
        return diffCount;
    }

    public List<Map<String, Object>> buildFeeList(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        if (StringUtils.isNotEmpty(feeCode)) {
            return bundle.feesByCode.containsKey(feeCode) ? Collections.singletonList(bundle.feesByCode.get(feeCode)) : Collections.emptyList();
        }
        return new ArrayList<>(bundle.feesByCode.values());
    }

    public List<Map<String, Object>> buildFeeVariableList(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        if (StringUtils.isEmpty(feeCode)) {
            return new ArrayList<>(bundle.variablesByCode.values());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String variableCode : bundle.feeReferencedVariables.getOrDefault(feeCode, new TreeSet<>())) {
            Map<String, Object> variable = bundle.variablesByCode.get(variableCode);
            if (variable != null) {
                result.add(variable);
            }
        }
        return result;
    }

    public List<Map<String, Object>> buildFeeRuleList(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        if (StringUtils.isEmpty(feeCode)) {
            return new ArrayList<>(bundle.rulesByCode.values());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ruleCode : bundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>())) {
            Map<String, Object> rule = bundle.rulesByCode.get(ruleCode);
            if (rule != null) {
                result.add(rule);
            }
        }
        return result;
    }

    public List<Map<String, Object>> buildFeeConditionList(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        Collection<String> ruleCodes = StringUtils.isEmpty(feeCode) ? bundle.rulesByCode.keySet() : bundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ruleCode : ruleCodes) {
            result.addAll(bundle.ruleConditionsByRuleCode.getOrDefault(ruleCode, Collections.emptyList()));
        }
        return result;
    }

    public List<Map<String, Object>> buildFeeTierList(PublishSnapshotBundle bundle, String feeCode) {
        bundle = normalizeBundle(bundle);
        Collection<String> ruleCodes = StringUtils.isEmpty(feeCode) ? bundle.rulesByCode.keySet() : bundle.feeRuleCodes.getOrDefault(feeCode, new TreeSet<>());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ruleCode : ruleCodes) {
            result.addAll(bundle.ruleTiersByRuleCode.getOrDefault(ruleCode, Collections.emptyList()));
        }
        return result;
    }

    private String determineChangeType(Map<String, Object> fromObject, Map<String, Object> toObject) {
        if (fromObject == null && toObject == null) {
            return "UNCHANGED";
        }
        if (fromObject == null) {
            return "ADDED";
        }
        if (toObject == null) {
            return "REMOVED";
        }
        return isEquivalentValue(fromObject, toObject) ? "UNCHANGED" : "CHANGED";
    }

    private List<String> compareChangedFields(Map<String, Object> fromObject, Map<String, Object> toObject) {
        if (fromObject == null || toObject == null) {
            return Collections.emptyList();
        }
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(fromObject.keySet());
        fields.retainAll(toObject.keySet());
        List<String> changed = new ArrayList<>();
        for (String field : fields) {
            Object fromValue = fromObject == null ? null : fromObject.get(field);
            Object toValue = toObject == null ? null : toObject.get(field);
            if (!isEquivalentValue(fromValue, toValue)) {
                changed.add(field);
            }
        }
        return changed;
    }

    private String buildFeeSummaryText(String changeType, boolean feeDirectChanged, int ruleChangeCount, int variableChangeCount) {
        if ("ADDED".equals(changeType)) {
            return "该费用首次进入当前版本快照。";
        }
        if ("REMOVED".equals(changeType)) {
            return "该费用未再进入当前版本快照。";
        }
        List<String> parts = new ArrayList<>();
        if (feeDirectChanged) parts.add("费用主数据有变更");
        if (ruleChangeCount > 0) parts.add(String.format(Locale.ROOT, "%d条规则发生变化", ruleChangeCount));
        if (variableChangeCount > 0)
            parts.add(String.format(Locale.ROOT, "%d个引用变量口径发生变化", variableChangeCount));
        return parts.isEmpty() ? "费用快照有变化。" : String.join("，", parts) + "。";
    }

    private String resolveSceneFieldLabel(String field) {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("sceneCode", "场景编码");
        labels.put("sceneName", "场景名称");
        labels.put("businessDomain", "业务域");
        labels.put("orgCode", "适用组织");
        labels.put("sceneType", "场景类型");
        labels.put("status", "状态");
        labels.put("remark", "说明");
        return labels.getOrDefault(field, field);
    }

    private List<Map<String, Object>> normalizeCollection(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().sorted(Comparator.comparing(PublishJsonSupport::canonicalJson)).collect(Collectors.toList());
    }

    private boolean isEquivalentValue(Object fromValue, Object toValue) {
        if (fromValue == null && toValue == null) {
            return true;
        }
        if (fromValue == null || toValue == null) {
            return false;
        }
        if (fromValue instanceof Map && toValue instanceof Map) {
            Map<?, ?> fromMap = (Map<?, ?>) fromValue;
            Map<?, ?> toMap = (Map<?, ?>) toValue;
            Set<String> comparableKeys = new TreeSet<>();
            for (Object key : fromMap.keySet()) {
                if (key != null && toMap.containsKey(key)) {
                    comparableKeys.add(String.valueOf(key));
                }
            }
            if (comparableKeys.isEmpty()) {
                return fromMap.isEmpty() && toMap.isEmpty();
            }
            for (String key : comparableKeys) {
                if (!isEquivalentValue(fromMap.get(key), toMap.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (fromValue instanceof Collection && toValue instanceof Collection) {
            List<?> fromList = new ArrayList<>((Collection<?>) fromValue);
            List<?> toList = new ArrayList<>((Collection<?>) toValue);
            if (fromList.size() != toList.size()) {
                return false;
            }
            for (int index = 0; index < fromList.size(); index++) {
                if (!isEquivalentValue(fromList.get(index), toList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(PublishJsonSupport.canonicalJson(fromValue),
                PublishJsonSupport.canonicalJson(toValue));
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
