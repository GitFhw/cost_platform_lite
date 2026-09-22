<template>
  <div class="rate-matrix-editor">
    <el-alert
      title="动态列按要素类型渲染：字典支持等于/属于任一多选，布尔使用选项，数值使用严格十进制和行级操作符，文本使用文本输入；“全部（不限）”不会生成条件。日期、时间区间、阶梯、公式和高基数字典请在高级规则中维护。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="rate-matrix-toolbar">
      <div class="rate-matrix-toolbar__summary">
        <strong>费目级动态费率矩阵</strong>
        <span>{{ rows.length }} 条可编辑规则 · {{ columns.length }} 个动态要素列</span>
      </div>
      <el-button type="primary" plain v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" @click="emit('add')">新增矩阵行</el-button>
    </div>

    <el-empty v-if="!columns.length && !rows.length" description="当前费目没有满足矩阵安全边界的要素或规则，请使用高级规则维护。" />
    <el-table v-else :data="rows" border stripe height="520" class="rate-matrix-table">
      <el-table-column fixed="left" label="规则编码" width="170">
        <template #default="{ row }">
          <el-input v-model="row.ruleCode" maxlength="64" placeholder="必填" v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" />
        </template>
      </el-table-column>
      <el-table-column fixed="left" label="规则名称" width="180">
        <template #default="{ row }">
          <el-input v-model="row.ruleName" maxlength="128" placeholder="必填" v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" />
        </template>
      </el-table-column>
      <el-table-column label="规则类型" width="150">
        <template #default="{ row }">
          <el-select v-model="row.ruleType" v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" @change="emit('change', row)">
            <el-option label="固定费率" value="FIXED_RATE" />
            <el-option label="固定金额" value="FIXED_AMOUNT" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.priority" :min="0" :max="999999" controls-position="right" v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" @change="emit('change', row)" />
        </template>
      </el-table-column>
      <el-table-column label="计价值" width="160">
        <template #default="{ row }">
          <el-input-number
            :model-value="priceValue(row)"
            :precision="row.ruleType === 'FIXED_RATE' ? 6 : 2"
            :min="0"
            :controls="false"
            placeholder="必填"
            v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']"
            @update:model-value="setPriceValue(row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column
        v-for="column in columns"
        :key="column.variableCode"
        :label="column.variableName || column.variableCode"
        :min-width="column.matrixType === 'number' ? 220 : column.matrixType === 'text' ? 240 : 220"
      >
        <template #header>
          <span class="rate-matrix-column-title">{{ column.variableName || column.variableCode }}</span>
          <small>{{ column.variableCode }} · {{ matrixTypeLabel(column) }}</small>
        </template>
        <template #default="{ row }">
          <div class="rate-matrix-cell">
            <el-select
              v-if="hasMultipleOperators(column)"
              class="rate-matrix-operator"
              :model-value="operatorValue(row, column)"
              :teleported="false"
              v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']"
              @update:model-value="setOperator(row, column, $event)"
            >
              <el-option v-for="operator in column.operators || []" :key="operator.value" :label="operator.label" :value="operator.value" />
            </el-select>
            <el-tag v-else size="small" effect="plain" class="rate-matrix-fixed-operator">{{ fixedOperatorLabel(column) }}</el-tag>

            <el-select
              v-if="column.matrixType === 'option' || column.matrixType === 'boolean'"
              :model-value="cellEditorValue(row, column)"
              filterable
              :multiple="isMultiValueOperator(row, column)"
              collapse-tags
              collapse-tags-tooltip
              :teleported="false"
              v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']"
              @update:model-value="setCellValue(row, column, $event)"
            >
              <el-option v-if="!isMultiValueOperator(row, column)" label="全部（不限）" :value="MATRIX_WILDCARD_VALUE" />
              <el-option
                v-for="option in column.options || []"
                :key="`${column.variableCode}-${option.value}`"
                :label="option.label"
                :value="option.value"
                :disabled="option.disabled === true"
              />
              <el-option
                v-if="isStaleValue(row, column)"
                :label="`已失效：${cellValue(row, column)}`"
                :value="cellValue(row, column)"
                disabled
              />
            </el-select>
            <el-input
              v-else
              :model-value="cellValue(row, column) === MATRIX_WILDCARD_VALUE ? '' : cellValue(row, column)"
              :disabled="isWildcard(row, column)"
              :maxlength="column.maxLength || undefined"
              :inputmode="column.matrixType === 'number' ? 'decimal' : 'text'"
              :placeholder="column.matrixType === 'number' ? '请输入数值' : '请输入文本'"
              v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']"
              @update:model-value="setCellValue(row, column, $event)"
            />
            <el-checkbox :model-value="isWildcard(row, column)" v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" @update:model-value="setWildcard(row, column, $event)">不限</el-checkbox>
            <el-tag v-if="isStaleValue(row, column)" type="danger" size="small" class="rate-matrix-stale-tag">选项已失效</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="145">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" @click="emit('copy', row)">复制</el-button>
          <el-button link @click="emit('advanced', row)">高级</el-button>
          <el-button link type="danger" v-hasPermi="['cost:lite:rule:write', 'cost:lite:manage']" @click="emit('remove', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="advancedRules.length" class="rate-matrix-advanced">
      <div class="rate-matrix-advanced__header">
        <strong>高级规则（{{ advancedRules.length }}）</strong>
        <span>复杂条件不强行压缩到矩阵；可逐条打开原规则编辑器。</span>
      </div>
      <div class="rate-matrix-advanced__list">
        <el-tag v-for="rule in advancedRules" :key="rule.ruleId || rule.ruleCode" effect="plain" @click="emit('advanced-rule', rule)">
          {{ rule.ruleName || rule.ruleCode }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { MATRIX_WILDCARD_OPERATOR, MATRIX_WILDCARD_VALUE, normalizeMatrixCell } from "./rateMatrix.js";

interface MatrixOption {
  label: string;
  value: string;
  disabled?: boolean;
}

interface MatrixOperator {
  value: string;
  label: string;
}

interface MatrixColumn {
  variableCode: string;
  variableName?: string;
  matrixType?: string;
  options?: MatrixOption[];
  operators?: MatrixOperator[];
  maxLength?: number;
}

interface MatrixRow {
  ruleType?: string;
  rateValue?: number | string;
  amountValue?: number | string;
  cells?: Record<string, unknown>;
  [key: string]: unknown;
}

const props = withDefaults(defineProps<{
  columns: MatrixColumn[];
  rows: MatrixRow[];
  advancedRules?: MatrixRow[];
  loading?: boolean;
  saving?: boolean;
}>(), {
  advancedRules: () => [],
  loading: false,
  saving: false,
});

const emit = defineEmits<{
  (event: "add"): void;
  (event: "change", row: MatrixRow): void;
  (event: "copy", row: MatrixRow): void;
  (event: "advanced", row: MatrixRow): void;
  (event: "advanced-rule", row: MatrixRow): void;
  (event: "remove", row: MatrixRow): void;
}>();

function cellState(row: MatrixRow, column: MatrixColumn): { operatorCode: string; value: string } {
  return normalizeMatrixCell(row.cells?.[column.variableCode]);
}

function cellValue(row: MatrixRow, column: MatrixColumn): string {
  return cellState(row, column).value;
}

function splitCellValues(value: unknown): string[] {
  const rawValues = Array.isArray(value) ? value : String(value ?? "").split(",");
  return [...new Set(rawValues.map(item => String(item ?? "").trim()).filter(Boolean))];
}

function isMultiValueOperator(row: MatrixRow, column: MatrixColumn): boolean {
  return cellState(row, column).operatorCode === "IN"
    && column.matrixType === "option";
}

function cellEditorValue(row: MatrixRow, column: MatrixColumn): string | string[] {
  const cell = cellState(row, column);
  return isMultiValueOperator(row, column) ? splitCellValues(cell.value) : cell.value;
}

function operatorValue(row: MatrixRow, column: MatrixColumn): string {
  const cell = cellState(row, column);
  return cell.operatorCode === MATRIX_WILDCARD_OPERATOR ? (column.operators?.[0]?.value || "EQ") : cell.operatorCode;
}

function setCellValue(row: MatrixRow, column: MatrixColumn, value: unknown): void {
  if (!row.cells) row.cells = {};
  const current = cellState(row, column);
  const nextValue = Array.isArray(value)
    ? splitCellValues(value).join(",")
    : value === undefined || value === null ? "" : String(value);
  row.cells[column.variableCode] = nextValue === MATRIX_WILDCARD_VALUE
    ? { operatorCode: MATRIX_WILDCARD_OPERATOR, value: MATRIX_WILDCARD_VALUE }
    : { operatorCode: current.operatorCode === MATRIX_WILDCARD_OPERATOR ? "EQ" : current.operatorCode, value: nextValue };
  emit("change", row);
}

function setOperator(row: MatrixRow, column: MatrixColumn, value: unknown): void {
  if (!row.cells) row.cells = {};
  const current = cellState(row, column);
  const operatorCode = String(value || "EQ").toUpperCase();
  const currentValues = splitCellValues(current.value);
  const nextValue = current.value === MATRIX_WILDCARD_VALUE
    ? ""
    : operatorCode === "IN"
      ? currentValues.join(",")
      : currentValues[0] || "";
  row.cells[column.variableCode] = {
    operatorCode,
    value: nextValue,
  };
  emit("change", row);
}

function isWildcard(row: MatrixRow, column: MatrixColumn): boolean {
  return cellState(row, column).value === MATRIX_WILDCARD_VALUE;
}

function setWildcard(row: MatrixRow, column: MatrixColumn, wildcard: boolean): void {
  if (wildcard) {
    if (!row.cells) row.cells = {};
    row.cells[column.variableCode] = { operatorCode: MATRIX_WILDCARD_OPERATOR, value: MATRIX_WILDCARD_VALUE };
    emit("change", row);
    return;
  }
  setCellValue(row, column, "");
}

function hasMultipleOperators(column: MatrixColumn): boolean {
  return (column.operators?.length || 0) > 1;
}

function fixedOperatorLabel(column: MatrixColumn): string {
  return column.operators?.[0]?.label || "等于";
}

function matrixTypeLabel(column: MatrixColumn): string {
  return ({
    option: "字典/枚举",
    boolean: "布尔",
    number: "数值",
    text: "文本",
  } as Record<string, string>)[String(column.matrixType || "")] || "高级";
}

function isStaleValue(row: MatrixRow, column: MatrixColumn): boolean {
  const value = cellValue(row, column);
  return value !== MATRIX_WILDCARD_VALUE
    && value !== ""
    && Boolean(column.options?.length)
    && !splitCellValues(value).every(item => column.options.some(
      option => String(option.value) === item && option.disabled !== true,
    ));
}

function priceValue(row: MatrixRow): number | undefined {
  const value = row.ruleType === "FIXED_AMOUNT" ? row.amountValue : row.rateValue;
  if (value === undefined || value === null || value === "") return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function setPriceValue(row: MatrixRow, value: number | undefined): void {
  if (row.ruleType === "FIXED_AMOUNT") row.amountValue = value;
  else row.rateValue = value;
  emit("change", row);
}
</script>

<style scoped>
.rate-matrix-editor {
  display: grid;
  gap: 14px;
}

.rate-matrix-toolbar,
.rate-matrix-toolbar__summary,
.rate-matrix-advanced__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rate-matrix-toolbar__summary {
  justify-content: flex-start;
}

.rate-matrix-toolbar__summary span,
.rate-matrix-advanced__header span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.rate-matrix-table :deep(.el-input-number),
.rate-matrix-table :deep(.el-select),
.rate-matrix-table :deep(.el-input) {
  width: 100%;
}

.rate-matrix-column-title {
  display: block;
  line-height: 18px;
}

.rate-matrix-column-title + small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 400;
}

.rate-matrix-cell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
}

.rate-matrix-cell > :deep(.rate-matrix-operator),
.rate-matrix-cell > :deep(.rate-matrix-fixed-operator) {
  grid-column: 1 / -1;
}

.rate-matrix-fixed-operator {
  justify-self: start;
}

.rate-matrix-cell :deep(.el-checkbox) {
  margin-right: 0;
}

.rate-matrix-stale-tag {
  grid-column: 1 / -1;
  margin-top: 2px;
}

.rate-matrix-advanced {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.rate-matrix-advanced__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rate-matrix-advanced__list :deep(.el-tag) {
  cursor: pointer;
}
</style>
