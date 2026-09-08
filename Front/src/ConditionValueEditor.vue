<template>
  <div class="condition-value-editor">
    <el-input
      v-if="!requiresValue"
      model-value="该操作符不需要条件值"
      disabled
    />
    <div v-else-if="isNumber && isBetweenOperator" class="condition-value-editor__range">
      <el-input-number
        :model-value="numberRangeValue[0]"
        controls-position="right"
        placeholder="起始值"
        @update:model-value="value => handleRangeChange(0, value)"
      />
      <span>至</span>
      <el-input-number
        :model-value="numberRangeValue[1]"
        controls-position="right"
        placeholder="截止值"
        @update:model-value="value => handleRangeChange(1, value)"
      />
    </div>
    <el-date-picker
      v-else-if="isDate && isBetweenOperator"
      :model-value="dateRangeValue"
      type="daterange"
      value-format="YYYY-MM-DD"
      start-placeholder="起始日期"
      end-placeholder="截止日期"
      @update:model-value="handleDateRangeChange"
    />
    <el-select
      v-else-if="usesDictSelect"
      :model-value="dictModelValue"
      clearable
      filterable
      :multiple="isMultiValueOperator"
      collapse-tags
      collapse-tags-tooltip
      placeholder="请选择条件值"
      @update:model-value="handleDictChange"
    >
      <el-option
        v-for="item in dictOptions"
        :key="item.value"
        :label="item.label"
        :value="item.value"
      />
    </el-select>
    <el-input-number
      v-else-if="isNumber"
      :model-value="numberValue"
      controls-position="right"
      placeholder="请输入数值"
      @update:model-value="handleNumberChange"
    />
    <el-date-picker
      v-else-if="isDate"
      :model-value="modelValue || ''"
      type="date"
      value-format="YYYY-MM-DD"
      placeholder="请选择日期"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <el-select
      v-else-if="isBoolean"
      :model-value="modelValue"
      clearable
      placeholder="请选择是/否"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <el-option label="是 / true" value="true" />
      <el-option label="否 / false" value="false" />
    </el-select>
    <el-input
      v-else
      :model-value="modelValue"
      :placeholder="placeholder"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <div class="condition-value-editor__hint">{{ editorHint }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { CostLiteDictionary, CostLiteDictionaryOption, CostLiteRecord } from "./costLiteApi";

interface Props {
  modelValue?: string | number | string[] | null;
  variableMeta?: CostLiteRecord;
  dictOptionsMap?: CostLiteDictionary;
  operatorCode?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: "",
  variableMeta: () => ({}),
  dictOptionsMap: () => ({}),
  operatorCode: "",
});

const emit = defineEmits<{
  (event: "update:modelValue", value: string | number | string[] | null): void;
}>();

const normalizedOperator = computed(() => String(props.operatorCode || "").toUpperCase());
const normalizedDataType = computed(() => String(
  props.variableMeta?.dataType || props.variableMeta?.variableType || "",
).toUpperCase());
const isMultiValueOperator = computed(() => ["IN", "NOT_IN"].includes(normalizedOperator.value));
const isBetweenOperator = computed(() => normalizedOperator.value === "BETWEEN");
const requiresValue = computed(() => !["IS_NULL", "IS_NOT_NULL"].includes(normalizedOperator.value));
const isNumber = computed(() => ["NUMBER", "DECIMAL", "INTEGER", "LONG", "DOUBLE", "BIGDECIMAL"]
  .includes(normalizedDataType.value));
const isDate = computed(() => ["DATE", "DATETIME", "LOCALDATE", "LOCALDATETIME"]
  .includes(normalizedDataType.value));
const isBoolean = computed(() => ["BOOLEAN", "BOOL"].includes(normalizedDataType.value));
const usesDictSelect = computed(() => Boolean(props.variableMeta?.dictType));
const dictOptions = computed<CostLiteDictionaryOption[]>(() => (
  props.dictOptionsMap?.[String(props.variableMeta?.dictType || "")] || []
));

const dictModelValue = computed(() => {
  if (!isMultiValueOperator.value) return props.modelValue;
  if (Array.isArray(props.modelValue)) return props.modelValue;
  if (props.modelValue === undefined || props.modelValue === null || props.modelValue === "") return [];
  return String(props.modelValue).split(",").map(item => item.trim()).filter(Boolean);
});

const numberValue = computed(() => {
  if (props.modelValue === undefined || props.modelValue === null || props.modelValue === "") return undefined;
  const value = Number(props.modelValue);
  return Number.isFinite(value) ? value : undefined;
});

function parseRangeValue(): [string, string] {
  if (Array.isArray(props.modelValue)) {
    return [String(props.modelValue[0] ?? ""), String(props.modelValue[1] ?? "")];
  }
  const parts = String(props.modelValue ?? "").split(",");
  return [parts[0]?.trim() || "", parts[1]?.trim() || ""];
}

const numberRangeValue = computed(() => parseRangeValue().map(item => {
  if (!item) return undefined;
  const value = Number(item);
  return Number.isFinite(value) ? value : undefined;
}) as [number | undefined, number | undefined]);
const dateRangeValue = computed(() => {
  const range = parseRangeValue();
  return range[0] || range[1] ? range : [];
});

const placeholder = computed(() => {
  if (["IN", "NOT_IN"].includes(normalizedOperator.value)) return "多个值请用英文逗号分隔";
  if (normalizedOperator.value === "BETWEEN") return "请按 起始值,截止值 录入";
  if (normalizedOperator.value === "EXPR") return "请输入表达式条件";
  return "请输入条件值";
});

const editorHint = computed(() => {
  if (!requiresValue.value) return "IS NULL / IS NOT NULL 会忽略条件值。";
  if (usesDictSelect.value) return isMultiValueOperator.value
    ? "字典变量支持多选，保存时自动用英文逗号拼接。"
    : "字典变量按绑定字典显示下拉选项。";
  if (isNumber.value && isBetweenOperator.value) return "数值区间会保存为“起始值,截止值”。";
  if (isNumber.value) return "数值变量使用数字输入框，避免录入非数字。";
  if (isDate.value && isBetweenOperator.value) return "日期区间会保存为“起始日期,截止日期”。";
  if (isDate.value) return "日期变量使用日期选择器。";
  if (isBoolean.value) return "布尔变量使用是/否下拉。";
  return isMultiValueOperator.value ? "多个文本值请用英文逗号分隔。" : "文本变量按普通输入处理。";
});

function handleNumberChange(value: number | undefined): void {
  emit("update:modelValue", value === undefined || value === null ? "" : String(value));
}

function handleRangeChange(index: number, value: number | undefined): void {
  const range = parseRangeValue();
  range[index] = value === undefined || value === null ? "" : String(value);
  emit("update:modelValue", range.filter((item, itemIndex) => item || itemIndex === 0).join(","));
}

function handleDateRangeChange(value: string[] | null): void {
  const normalized = Array.isArray(value) ? value : [];
  emit("update:modelValue", normalized.filter(Boolean).join(","));
}

function handleDictChange(value: string | string[] | undefined): void {
  if (isMultiValueOperator.value) {
    const normalized = Array.isArray(value) ? value.filter(Boolean) : [];
    emit("update:modelValue", normalized.join(","));
    return;
  }
  emit("update:modelValue", value ?? "");
}
</script>

<style scoped>
.condition-value-editor {
  display: grid;
  gap: 6px;
  text-align: left;
}

.condition-value-editor__range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.condition-value-editor__range :deep(.el-input-number),
.condition-value-editor > :deep(.el-select),
.condition-value-editor > :deep(.el-input),
.condition-value-editor > :deep(.el-date-editor) {
  width: 100%;
}

.condition-value-editor__range span,
.condition-value-editor__hint {
  color: var(--muted, var(--el-text-color-secondary));
  font-size: 12px;
}

.condition-value-editor__hint {
  line-height: 1.5;
}
</style>
