package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.*;

/**
 * Maps third-party access-profile records into standard cost input objects.
 */
@Service
public class AccessProfileInputMappingService {
    private static final int DEFAULT_TASK_PARTITION_SIZE = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public InputBuildContext buildContext(Map<String, Object> template, String mappingJson) {
        InputBuildContext context = new InputBuildContext();
        context.template = template == null ? new LinkedHashMap<>() : template;
        context.mapping = parseOptionalJsonMap(mappingJson);
        context.fields = castFieldList(context.template.get("fields"));
        context.taskType = stringValue(context.template.get("taskType"));
        context.defaultObjectDimension = stringValue(castMap(context.template.get("fee")).get("objectDimension"));
        context.fieldMappings = buildFieldMappingSummary(context.fields, context.mapping);
        return context;
    }

    public Map<String, Object> buildMappedInputResult(InputBuildContext context, List<Map<String, Object>> rawRecords,
                                                      int startIndex) {
        List<Map<String, Object>> mappedRecords = new ArrayList<>();
        LinkedHashSet<String> missingPaths = new LinkedHashSet<>();
        for (int i = 0; i < rawRecords.size(); i++) {
            mappedRecords.add(buildMappedInputRecord(rawRecords.get(i), context.fields, context.mapping, context.taskType,
                    context.defaultObjectDimension, startIndex + i, missingPaths));
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneId", context.template.get("sceneId"));
        result.put("sceneCode", context.template.get("sceneCode"));
        result.put("sceneName", context.template.get("sceneName"));
        result.put("versionId", context.template.get("versionId"));
        result.put("versionNo", context.template.get("versionNo"));
        result.put("snapshotSource", context.template.get("snapshotSource"));
        result.put("taskType", context.taskType);
        result.put("fee", context.template.get("fee"));
        result.put("templateFieldCount", context.fields.size());
        result.put("rawRecordCount", rawRecords.size());
        result.put("mappedRecordCount", mappedRecords.size());
        result.put("fieldMappings", context.fieldMappings);
        result.put("mappedRecords", mappedRecords);
        result.put("missingPaths", new ArrayList<>(missingPaths));
        result.put("loadingGuide", buildInputBatchLoadingGuide(mappedRecords.size()));
        result.put("mappingJson", context.mapping);
        result.put("message", buildInputBuildPreviewMessage(mappedRecords.size(), missingPaths.size()));
        return result;
    }

    private List<Map<String, Object>> buildFieldMappingSummary(List<Map<String, Object>> fields, Map<String, Object> mapping) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            String variableCode = stringValue(field.get("variableCode"));
            String path = firstNonBlank(stringValue(field.get("path")), variableCode);
            Object mappingSpec = resolveInputBuildMappingSpec(mapping, path, variableCode);
            item.put("path", path);
            item.put("variableCode", variableCode);
            item.put("variableName", field.get("variableName"));
            item.put("sourceType", field.get("sourceType"));
            item.put("includedInTemplate", field.get("includedInTemplate"));
            item.put("mappingSpec", mappingSpec);
            item.put("mappingHint", describeMappingSpec(mappingSpec));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildMappedInputRecord(Map<String, Object> rawRecord, List<Map<String, Object>> fields,
                                                       Map<String, Object> mapping, String taskType, String defaultObjectDimension,
                                                       int index, Set<String> missingPaths) {
        LinkedHashMap<String, Object> target = new LinkedHashMap<>();
        populatePathValue(target, "bizNo",
                firstNonBlank(resolveMappedCoreField(rawRecord, mapping, "bizNo"), buildTemplateBizNo(taskType, index)));
        String objectDimension = firstNonBlank(
                firstNonBlank(resolveMappedCoreField(rawRecord, mapping, "objectDimension"),
                        resolveString(rawRecord, "objectDimension", "object_dimension", "objectType", "object_type")),
                defaultObjectDimension);
        if (StringUtils.isNotEmpty(objectDimension)) {
            populatePathValue(target, "objectDimension", objectDimension);
        }
        populatePathValue(target, "objectCode",
                firstNonBlank(resolveMappedCoreField(rawRecord, mapping, "objectCode"),
                        resolveString(rawRecord, "objectCode", "object_code", "teamCode", "team_code", "id")));
        populatePathValue(target, "objectName",
                firstNonBlank(resolveMappedCoreField(rawRecord, mapping, "objectName"),
                        resolveString(rawRecord, "objectName", "object_name", "teamName", "team_name", "name")));

        for (Map<String, Object> field : fields) {
            if (!Boolean.TRUE.equals(field.get("includedInTemplate"))) {
                continue;
            }
            String variableCode = stringValue(field.get("variableCode"));
            String path = firstNonBlank(stringValue(field.get("path")), variableCode);
            Object mappedValue = resolveMappedFieldValue(rawRecord, mapping, path, variableCode);
            if (mappedValue == null) {
                missingPaths.add(firstNonBlank(path, variableCode));
                continue;
            }
            populatePathValue(target, path, mappedValue);
        }
        return target;
    }

    private String resolveMappedCoreField(Map<String, Object> rawRecord, Map<String, Object> mapping, String targetKey) {
        Object mappingSpec = resolveInputBuildMappingSpec(mapping, targetKey, targetKey);
        Object resolved = resolveValueByMappingSpec(rawRecord, mappingSpec);
        return resolved == null ? "" : String.valueOf(resolved);
    }

    private Object resolveMappedFieldValue(Map<String, Object> rawRecord, Map<String, Object> mapping, String path,
                                           String variableCode) {
        Object mappingSpec = resolveInputBuildMappingSpec(mapping, path, variableCode);
        Object mappedValue = resolveValueByMappingSpec(rawRecord, mappingSpec);
        if (mappedValue != null) {
            return mappedValue;
        }
        if (StringUtils.isNotEmpty(path)) {
            mappedValue = resolveByPath(rawRecord, path);
            if (mappedValue != null) {
                return mappedValue;
            }
        }
        if (StringUtils.isNotEmpty(variableCode)) {
            return resolveByPath(rawRecord, variableCode);
        }
        return null;
    }

    private Object resolveInputBuildMappingSpec(Map<String, Object> mapping, String path, String variableCode) {
        if (mapping == null || mapping.isEmpty()) {
            return null;
        }
        if (StringUtils.isNotEmpty(path) && mapping.containsKey(path)) {
            return mapping.get(path);
        }
        if (StringUtils.isNotEmpty(variableCode) && mapping.containsKey(variableCode)) {
            return mapping.get(variableCode);
        }
        return null;
    }

    private Object resolveValueByMappingSpec(Map<String, Object> rawRecord, Object mappingSpec) {
        if (mappingSpec == null) {
            return null;
        }
        if (mappingSpec instanceof Map) {
            Map<String, Object> specMap = castMap(mappingSpec);
            if (specMap.containsKey("value")) {
                return specMap.get("value");
            }
            String sourcePath = firstNonBlank(stringValue(specMap.get("path")), stringValue(specMap.get("sourcePath")));
            return StringUtils.isEmpty(sourcePath) ? null : resolveByPath(rawRecord, sourcePath);
        }
        if (mappingSpec instanceof String) {
            String sourcePath = StringUtils.trim((String) mappingSpec);
            return StringUtils.isEmpty(sourcePath) ? null : resolveByPath(rawRecord, sourcePath);
        }
        return mappingSpec;
    }

    private String describeMappingSpec(Object mappingSpec) {
        if (mappingSpec == null) {
            return "AUTO";
        }
        if (mappingSpec instanceof Map) {
            Map<String, Object> specMap = castMap(mappingSpec);
            if (specMap.containsKey("value")) {
                return "CONST";
            }
            return firstNonBlank(stringValue(specMap.get("path")), stringValue(specMap.get("sourcePath")));
        }
        return String.valueOf(mappingSpec);
    }

    private Map<String, Object> buildInputBatchLoadingGuide(int itemTotal) {
        int safeTotal = Math.max(itemTotal, 0);
        int partitionCount = Math.max(1, (int) Math.ceil(safeTotal / (double) DEFAULT_TASK_PARTITION_SIZE));
        LinkedHashMap<String, Object> guide = new LinkedHashMap<>();
        guide.put("partitionSize", DEFAULT_TASK_PARTITION_SIZE);
        guide.put("estimatedPartitionCount", partitionCount);
        if (safeTotal <= 0) {
            guide.put("type", "info");
            guide.put("title", "当前批次暂无有效输入");
            guide.put("description", "请先检查导入内容或重新生成批次，再进入正式核算。");
            return guide;
        }
        if (safeTotal <= 50) {
            guide.put("type", "info");
            guide.put("title", String.format(Locale.ROOT, "当前批次共 %d 条，预计拆成 %d 个分片", safeTotal, partitionCount));
            guide.put("description", "当前规模适合联调和小批量复核；如果只是验证少量样例，也可以回到任务中心使用 JSON 直传。");
            return guide;
        }
        if (safeTotal <= DEFAULT_TASK_PARTITION_SIZE) {
            guide.put("type", "success");
            guide.put("title", String.format(Locale.ROOT, "当前批次共 %d 条，预计拆成 %d 个分片", safeTotal, partitionCount));
            guide.put("description", "当前规模适合按企业级批次流程直接提交正式核算，可保留装载台账、分片进度和失败恢复能力。");
            return guide;
        }
        guide.put("type", "warning");
        guide.put("title", String.format(Locale.ROOT, "当前批次共 %d 条，预计拆成 %d 个分片", safeTotal, partitionCount));
        guide.put("description", "当前已属于大批量任务，请继续使用导入批次提交流程，不建议回退为 JSON 直传，以免丢失分页预览、装载台账和恢复治理能力。");
        return guide;
    }

    private String buildInputBuildPreviewMessage(int mappedRecordCount, int missingCount) {
        if (missingCount <= 0) {
            return "已生成 " + mappedRecordCount + " 条标准计费对象，可直接复制到单费用取价或正式核算入口继续联调。";
        }
        return "已生成 " + mappedRecordCount + " 条标准计费对象，仍有 " + missingCount + " 个模板路径未命中，请继续补映射。";
    }

    private String buildTemplateBizNo(String taskType, int index) {
        String prefix;
        if (TASK_TYPE_FORMAL_BATCH.equalsIgnoreCase(taskType)) {
            prefix = "BATCH";
        } else if (TASK_TYPE_FORMAL_SINGLE.equalsIgnoreCase(taskType)) {
            prefix = "FORMAL";
        } else {
            prefix = "SIM";
        }
        return prefix + "-" + String.format(Locale.ROOT, "%03d", index);
    }

    private void populatePathValue(Map<String, Object> root, String path, Object value) {
        if (root == null || StringUtils.isEmpty(path)) {
            return;
        }
        String[] pieces = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < pieces.length - 1; i++) {
            Object child = current.get(pieces[i]);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                current.put(pieces[i], child);
            }
            current = castMap(child);
        }
        current.put(pieces[pieces.length - 1], value);
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

    private List<Map<String, Object>> castFieldList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add(castMap(item));
            }
        }
        return result;
    }

    private Map<String, Object> parseOptionalJsonMap(String json) {
        if (StringUtils.isEmpty(StringUtils.trim(json))) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
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

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static class InputBuildContext {
        public Map<String, Object> template = new LinkedHashMap<>();
        public Map<String, Object> mapping = new LinkedHashMap<>();
        public List<Map<String, Object>> fields = Collections.emptyList();
        public List<Map<String, Object>> fieldMappings = Collections.emptyList();
        public String taskType;
        public String defaultObjectDimension;
    }
}
