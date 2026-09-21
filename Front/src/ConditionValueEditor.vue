<template>
  <div class="condition-value-editor">
    <el-input
      v-if="!requiresValue"
      model-value="该操作符不需要条件值"
      disabled
    />
    <el-input
      v-else-if="isExpressionOperator"
      :model-value="modelValue"
      type="textarea"
      :rows="2"
      placeholder="请输入表达式条件"
      @update:model-value="emit('update:modelValue', $event)"
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
      v-else-if="isDateOnly && isBetweenOperator"
      :model-value="dateRangeValue"
      type="daterange"
      value-format="YYYY-MM-DD"
      start-placeholder="起始日期"
      end-placeholder="截止日期"
      @update:model-value="handleDateRangeChange"
    />
    <el-date-picker
      v-else-if="isDateTime && isBetweenOperator"
      :model-value="dateRangeValue"
      type="datetimerange"
      value-format="YYYY-MM-DD HH:mm:ss"
      start-placeholder="起始时间"
      end-placeholder="截止时间"
      @update:model-value="handleDateRangeChange"
    />
    <el-time-picker
      v-else-if="isTimeOnly && isBetweenOperator"
      :model-value="dateRangeValue"
      type="timerange"
      value-format="HH:mm:ss"
      start-placeholder="起始时刻"
      end-placeholder="截止时刻"
      @update:model-value="handleDateRangeChange"
    />
    <el-select
      v-else-if="usesOptionSelect"
      :model-value="dictModelValue"
      clearable
      filterable
      :multiple="isMultiValueOperator"
      :loading="optionsLoading"
      :remote="isRemoteOptionSource"
      collapse-tags
      collapse-tags-tooltip
      placeholder="请选择条件值"
      @remote-method="handleRemoteSearch"
      @visible-change="handleVisibleChange"
      @update:model-value="handleOptionChange"
    >
      <el-option
        v-for="item in optionItems"
        :key="item.value"
        :label="item.label"
        :value="item.value"
        :disabled="item.disabled === true"
      />
    </el-select>
    <el-input
      v-else-if="isNumber && isMultiValueOperator"
      :model-value="modelValue"
      placeholder="多个数值请用英文逗号分隔"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <el-input-number
      v-else-if="isNumber"
      :model-value="numberValue"
      controls-position="right"
      placeholder="请输入数值"
      @update:model-value="handleNumberChange"
    />
    <el-date-picker
      v-else-if="isDateOnly"
      :model-value="modelValue || ''"
      type="date"
      value-format="YYYY-MM-DD"
      placeholder="请选择日期"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <el-date-picker
      v-else-if="isDateTime"
      :model-value="modelValue || ''"
      type="datetime"
      value-format="YYYY-MM-DD HH:mm:ss"
      placeholder="请选择日期时间"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <el-time-picker
      v-else-if="isTimeOnly"
      :model-value="modelValue || ''"
      value-format="HH:mm:ss"
      placeholder="请选择时刻"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <el-input
      v-else-if="isDate"
      :model-value="modelValue"
      placeholder="请按系统约定格式输入日期时间"
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
import {
  DATE_ONLY_DATA_TYPES,
  DATETIME_DATA_TYPES,
  NUMERIC_DATA_TYPES,
  TIME_DATA_TYPES,
} from "./operatorPolicy.js";

interface Props {
  modelValue?: string | number | string[] | null;
  variableMeta?: CostLiteRecord;
  dictOptionsMap?: CostLiteDictionary;
  options?: CostLiteDictionaryOption[];
  optionsLoading?: boolean;
  operatorCode?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: "",
  variableMeta: () => ({}),
  dictOptionsMap: () => ({}),
  options: () => [],
  optionsLoading: false,
  operatorCode: "",
});

const emit = defineEmits<{
  (event: "update:modelValue", value: string | number | string[] | null): void;
  (event: "search-options", keyword: string): void;
}>();

const normalizedOperator = computed(() => String(props.operatorCode || "").toUpperCase());
const normalizedDataType = computed(() => String(
  props.variableMeta?.dataType || props.variableMeta?.variableType || "",
).toUpperCase());
const isMultiValueOperator = computed(() => ["IN", "NOT_IN"].includes(normalizedOperator.value));
const isBetweenOperator = computed(() => normalizedOperator.value === "BETWEEN");
const isExpressionOperator = computed(() => normalizedOperator.value === "EXPR");
const requiresValue = computed(() => !["IS_NULL", "IS_NOT_NULL"].includes(normalizedOperator.value));
const isNumber = computed(() => NUMERIC_DATA_TYPES.has(normalizedDataType.value));
const isDateOnly = computed(() => DATE_ONLY_DATA_TYPES.has(normalizedDataType.value));
const isDateTime = computed(() => DATETIME_DATA_TYPES.has(normalizedDataType.value));
const isTimeOnly = computed(() => TIME_DATA_TYPES.has(normalizedDataType.value));
const isDate = computed(() => isDateOnly.value || isDateTime.value || isTimeOnly.value);
const isBoolean = computed(() => ["BOOLEAN", "BOOL"].includes(normalizedDataType.value));
const optionSourceType = computed(() => String(props.variableMeta?.optionSourceType || "").toUpperCase());
const usesOptionSelect = computed(() => Boolean(
  !isDate.value && (props.variableMeta?.dictType
    || props.options?.length
    || ["PLATFORM_DICT", "BUSINESS_DICT", "BUSINESS_MASTER"].includes(optionSourceType.value)),
));
const isRemoteOptionSource = computed(() => ["BUSINESS_DICT", "BUSINESS_MASTER"].includes(optionSourceType.value));
const dictOptions = computed<CostLiteDictionaryOption[]>(() => (
  props.dictOptionsMap?.[String(props.variableMeta?.dictType || "")] || []
));
const optionItems = computed<CostLiteDictionaryOption[]>(() => (
  isRemoteOptionSource.value
    ? props.options
    : (props.options?.length ? props.options : dictOptions.value)
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
  if (isExpressionOperator.value) return "请输入表达式条件";
  return "请输入条件值";
});

const editorHint = computed(() => {
  if (!requiresValue.value) return "IS NULL / IS NOT NULL 会忽略条件值。";
  if (isExpressionOperator.value) return "表达式条件按既有规则执行链保存，不参与动态矩阵。";
  if (usesOptionSelect.value) {
    if (optionSourceType.value === "BUSINESS_MASTER") {
      return isMultiValueOperator.value
        ? "业务主数据支持按关键字远程多选，保存时只记录业务编码。"
        : "业务主数据按关键字远程检索，保存时只记录业务编码。";
    }
    if (optionSourceType.value === "BUSINESS_DICT") {
      return isMultiValueOperator.value
        ? "业务系统字典支持多选，保存时只记录业务编码。"
        : "业务系统字典显示业务名称，保存时只记录业务编码。";
    }
    return isMultiValueOperator.value
      ? "轻量平台字典支持多选，保存时自动用英文逗号拼接。"
      : "轻量平台字典按绑定字典显示下拉选项。";
  }
  if (isNumber.value && isBetweenOperator.value) return "数值区间会保存为“起始值,截止值”。";
  if (isNumber.value && isMultiValueOperator.value) return "多个数值会保存为逗号分隔的稳定数值编码。";
  if (isNumber.value) return "数值变量使用数字输入框，避免录入非数字。";
  if (isDateOnly.value && isBetweenOperator.value) return "日期区间会保存为“起始日期,截止日期”。";
  if (isDateTime.value && isBetweenOperator.value) return "日期时间区间保留到秒，保存为“起始时间,截止时间”。";
  if (isTimeOnly.value && isBetweenOperator.value) return "时刻区间保留到秒，跨日语义仍需母体执行链明确。";
  if (isDateTime.value) return "日期时间保留到秒；正式开放更多比较操作前需完成母体类型化执行回归。";
  if (isTimeOnly.value) return "时刻保留到秒；每日时段和跨日区间需由母体定义结构化语义。";
  if (isDateOnly.value) return "日期变量使用自然日选择器。";
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

function handleOptionChange(value: string | string[] | undefined): void {
  if (isMultiValueOperator.value) {
    const normalized = Array.isArray(value) ? value.filter(Boolean) : [];
    emit("update:modelValue", normalized.join(","));
    return;
  }
  emit("update:modelValue", value ?? "");
}

function handleRemoteSearch(keyword: string): void {
  if (isRemoteOptionSource.value) {
    emit("search-options", keyword || "");
  }
}

function handleVisibleChange(visible: boolean): void {
  if (visible && isRemoteOptionSource.value && !optionItems.value.length) {
    emit("search-options", "");
  }
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
