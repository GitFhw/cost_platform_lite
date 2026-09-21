import {
  DATE_DATA_TYPES,
  FORMULA_TYPES,
  OPTION_SOURCE_TYPES,
  isMatrixSupportedVariable,
  matrixTypeForVariable,
  normalizedOptionSourceType,
  normalizedVariableDataType,
  operatorOptionsForVariable,
  variableKind,
} from "./operatorPolicy.js";

export const MATRIX_WILDCARD_VALUE = "__ALL__";
export const MATRIX_OPTION_LIMIT = 100;
export const MATRIX_WILDCARD_OPERATOR = "ALL";

const MATRIX_RULE_TYPES = new Set(["FIXED_RATE", "FIXED_AMOUNT"]);
const DECIMAL_PATTERN = /^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/;

function normalized(value) {
  return String(value || "").trim().toUpperCase();
}
export function isStrictFiniteDecimal(value) {
  if (typeof value === "number") return Number.isFinite(value);
  if (typeof value !== "string") return false;
  const text = value.trim();
  return Boolean(text) && DECIMAL_PATTERN.test(text) && Number.isFinite(Number(text));
}

function decimalParts(value) {
  const text = String(value ?? "").trim();
  if (!isStrictFiniteDecimal(text)) return null;
  const sign = text.startsWith("-") ? -1 : 1;
  const unsigned = /^[+-]/.test(text) ? text.slice(1) : text;
  const [integer = "0", fraction = ""] = unsigned.split(".");
  const digitsText = `${integer}${fraction}`.replace(/^0+(?=\d)/, "") || "0";
  const digits = BigInt(digitsText);
  return {
    sign: digits === 0n ? 0 : sign,
    digits,
    scale: fraction.length,
  };
}

function compareDecimals(left, right) {
  const leftParts = decimalParts(left);
  const rightParts = decimalParts(right);
  if (!leftParts || !rightParts) return 0;
  if (leftParts.sign !== rightParts.sign) return leftParts.sign < rightParts.sign ? -1 : 1;
  if (leftParts.sign === 0) return 0;
  const scale = Math.max(leftParts.scale, rightParts.scale);
  const leftScaled = leftParts.digits * (10n ** BigInt(scale - leftParts.scale));
  const rightScaled = rightParts.digits * (10n ** BigInt(scale - rightParts.scale));
  if (leftScaled === rightScaled) return 0;
  const absoluteResult = leftScaled < rightScaled ? -1 : 1;
  return leftParts.sign < 0 ? -absoluteResult : absoluteResult;
}

function clone(value) {
  if (value === undefined || value === null) return value;
  return JSON.parse(JSON.stringify(value));
}

function variableCode(variable) {
  return String(variable?.variableCode || variable?.code || "").trim();
}

function optionRows(value) {
  if (Array.isArray(value)) return value;
  if (value && Array.isArray(value.rows)) return value.rows;
  if (value && Array.isArray(value.options)) return value.options;
  return [];
}

function optionStateFor(optionState, code) {
  if (!optionState || !code) return undefined;
  return optionState[code] || optionState[String(code)] || undefined;
}

function matrixOptionInfo(variable, optionState) {
  const code = variableCode(variable);
  const state = optionStateFor(optionState, code);
  const options = optionRows(state ?? variable?.options ?? variable?.optionItems);
  const rawTotal = state?.total ?? variable?.optionTotal ?? options.length;
  const total = Number(rawTotal);
  const normalizedOptions = options
    .filter((item) => item && item.value !== undefined && item.value !== null)
    .map((item) => ({
      label: String(item.label ?? item.value),
      value: String(item.value),
      ...(item.disabled === true ? { disabled: true } : {}),
    }));
  const hasMore = Boolean(state?.hasMore) || (Number.isFinite(total) && total > normalizedOptions.length);
  return {
    options: normalizedOptions,
    total: Number.isFinite(total) ? total : normalizedOptions.length,
    hasMore,
    // __ALL__ is a structural wildcard, not a user value. A real dictionary
    // value with the same code cannot be represented safely in this matrix.
    hasReservedWildcardCollision: normalizedOptions.some(
      (item) => item.value === MATRIX_WILDCARD_VALUE,
    ),
    hasOptionSnapshot: state
      ? state.hasOptionSnapshot === true
      : variable?.hasOptionSnapshot === true,
  };
}

function isExcludedVariable(variable) {
  const relationType = normalized(variable?.relationType || variable?.relation || "");
  const sourceType = normalized(variable?.sourceType || variable?.variableSourceType || "");
  const variableType = normalized(variable?.variableType);
  const optionSource = normalizedOptionSourceType(variable);
  const dataType = normalizedVariableDataType(variable);
  return relationType === "TIER_BASIS"
    || relationType === "TIER"
    || FORMULA_TYPES.has(relationType)
    || FORMULA_TYPES.has(sourceType)
    || FORMULA_TYPES.has(variableType)
    || optionSource === "BUSINESS_MASTER"
    || sourceType === "BUSINESS_MASTER"
    || DATE_DATA_TYPES.has(dataType)
    || dataType === "JSON";
}

function isMatrixableVariable(variable, optionState) {
  const code = variableCode(variable);
  if (!code || isExcludedVariable(variable) || !isMatrixSupportedVariable(variable)) return false;
  const kind = variableKind(variable);
  const source = normalizedOptionSourceType(variable);
  if (kind === "option") {
    const info = matrixOptionInfo(variable, optionState);
    // A dictionary-backed column is safe only when the workbench or the
    // variable itself provides an authoritative option snapshot. Missing or
    // incomplete state must fail closed for numeric and boolean dictionaries too.
    if (!info.hasOptionSnapshot) return false;
    if (info.hasMore || info.total > MATRIX_OPTION_LIMIT || info.options.length > MATRIX_OPTION_LIMIT) return false;
    if (info.hasReservedWildcardCollision) return false;
  }
  return true;
}

/**
 * Return the bounded, user-facing dimensions that may be represented by a
 * matrix. Date/time windows, tiers, formulas, and high-cardinality sources
 * intentionally remain in the advanced rule editor.
 */
export function matrixColumnsFromVariables(variables = [], optionState = {}) {
  const seen = new Set();
  return (Array.isArray(variables) ? variables : [])
    .slice()
    .sort((left, right) => Number(left?.sortNo || 0) - Number(right?.sortNo || 0))
    .filter((variable) => {
      const code = variableCode(variable);
      if (!code || seen.has(code) || !isMatrixableVariable(variable, optionState)) return false;
      seen.add(code);
      return true;
    })
    .map((variable) => {
      const code = variableCode(variable);
      const source = normalizedOptionSourceType(variable);
      const info = matrixOptionInfo(variable, optionState);
      const type = normalizedVariableDataType(variable);
      const matrixType = matrixTypeForVariable(variable);
      const options = matrixType === "boolean"
        ? [
          { label: "是", value: "true" },
          { label: "否", value: "false" },
        ]
        : info.options;
      return {
        variableId: variable.variableId,
        variableCode: code,
        variableName: String(variable.variableName || variable.name || code),
        dataType: type,
        variableType: normalized(variable?.variableType),
        optionSourceType: source,
        dictType: variable.dictType,
        sortNo: Number(variable.sortNo || 0),
        matrixType,
        variableKind: variableKind(variable),
        operators: operatorOptionsForVariable(variable, "matrix"),
        defaultOperator: "EQ",
        options,
        optionsTotal: info.total,
        optionsComplete: !info.hasMore,
        maxLength: Number(variable.maxLength || variable.length || 0) || undefined,
        matrixable: true,
      };
    });
}

function columnMap(columns) {
  return new Map((Array.isArray(columns) ? columns : [])
    .map((column) => [String(column?.variableCode || ""), column])
    .filter(([code]) => code));
}

function normalizedConditions(rule) {
  return (Array.isArray(rule?.conditions) ? rule.conditions : [])
    .map((condition, index) => ({
      ...condition,
      groupNo: Number(condition?.groupNo || 1),
      sortNo: Number(condition?.sortNo || index + 1),
      variableCode: String(condition?.variableCode || "").trim(),
      operatorCode: normalized(condition?.operatorCode),
      compareValue: condition?.compareValue,
    }));
}

function columnOperatorCodes(column) {
  if (Array.isArray(column?.operators) && column.operators.length) {
    return column.operators.map((item) => normalized(item?.value ?? item));
  }
  if (column?.matrixType === "number") return ["EQ", "GT", "GE", "LT", "LE"];
  return ["EQ"];
}

function activeOption(column, value) {
  const options = Array.isArray(column?.options) ? column.options : [];
  return options.some((option) => String(option.value) === String(value) && option.disabled !== true);
}

export function isMatrixCompatibleRule(rule, columns = []) {
  if (!rule || !MATRIX_RULE_TYPES.has(normalized(rule.ruleType))) return false;
  if (rule.matrixDetailUnavailable === true || !Array.isArray(rule.conditions)) return false;
  if (normalized(rule.pricingMode || "TYPED") !== "TYPED") return false;
  if (normalized(rule.conditionLogic || "AND") !== "AND") return false;
  const available = columnMap(columns);
  const used = new Set();
  return normalizedConditions(rule).every((condition) => {
    if (!condition.variableCode || condition.groupNo !== 1 || !condition.operatorCode) return false;
    if (used.has(condition.variableCode) || !available.has(condition.variableCode)) return false;
    const compareValue = String(condition.compareValue ?? "").trim();
    if (!compareValue) return false;
    const column = available.get(condition.variableCode);
    if (!columnOperatorCodes(column).includes(condition.operatorCode)) return false;
    if (column?.matrixType === "option" || column?.matrixType === "boolean") {
      if (condition.operatorCode !== "EQ" || compareValue.includes(",") || !activeOption(column, compareValue)) return false;
    }
    if (column?.matrixType === "number" && !isStrictFiniteDecimal(compareValue)) return false;
    if (column?.matrixType === "text" && condition.operatorCode !== "EQ") return false;
    used.add(condition.variableCode);
    return true;
  });
}

export function normalizeMatrixCell(value) {
  let operatorCode = "EQ";
  let rawValue = value;
  if (value && typeof value === "object" && !Array.isArray(value)) {
    rawValue = value.value ?? value.compareValue;
    operatorCode = normalized(value.operatorCode || value.operator || "EQ") || "EQ";
  }
  if (rawValue === MATRIX_WILDCARD_VALUE || operatorCode === MATRIX_WILDCARD_OPERATOR) {
    return { operatorCode: MATRIX_WILDCARD_OPERATOR, value: MATRIX_WILDCARD_VALUE };
  }
  return {
    operatorCode,
    value: rawValue === undefined || rawValue === null ? "" : String(rawValue),
  };
}

function isWildcardCell(value) {
  const cell = normalizeMatrixCell(value);
  return cell.operatorCode === MATRIX_WILDCARD_OPERATOR || cell.value === MATRIX_WILDCARD_VALUE;
}

export function createEmptyMatrixRow(columns = [], seed = {}) {
  const cells = {};
  const seedCells = seed.cells || {};
  (Array.isArray(columns) ? columns : []).forEach((column) => {
    if (column?.variableCode) {
      cells[column.variableCode] = normalizeMatrixCell(
        seedCells[column.variableCode] ?? MATRIX_WILDCARD_VALUE,
      );
    }
  });
  const ruleType = normalized(seed.ruleType) === "FIXED_AMOUNT" ? "FIXED_AMOUNT" : "FIXED_RATE";
  return {
    ...clone(seed),
    ruleId: seed.ruleId,
    ruleCode: seed.ruleCode || "",
    ruleName: seed.ruleName || "",
    ruleType,
    pricingMode: seed.pricingMode || "TYPED",
    priority: Number(seed.priority ?? 100),
    status: seed.status ?? "0",
    quantityVariableCode: seed.quantityVariableCode || "",
    rateValue: seed.rateValue ?? seed.pricingConfig?.rateValue,
    amountValue: seed.amountValue ?? seed.pricingConfig?.amountValue,
    cells,
  };
}

export function ruleToMatrixRow(rule, columns = []) {
  if (!isMatrixCompatibleRule(rule, columns)) return null;
  const row = createEmptyMatrixRow(columns, {
    ruleId: rule.ruleId,
    sceneId: rule.sceneId,
    feeId: rule.feeId,
    ruleCode: rule.ruleCode,
    ruleName: rule.ruleName,
    ruleType: rule.ruleType,
    pricingMode: rule.pricingMode || "TYPED",
    priority: Number(rule.priority ?? 100),
    quantityVariableCode: rule.quantityVariableCode,
    status: rule.status ?? "0",
    rateValue: rule.pricingConfig?.rateValue ?? rule.pricingConfig?.unitPrice,
    amountValue: rule.pricingConfig?.amountValue ?? rule.pricingConfig?.amount,
    sourceRule: clone(rule),
  });
  normalizedConditions(rule).forEach((condition) => {
    row.cells[condition.variableCode] = {
      operatorCode: condition.operatorCode,
      value: String(condition.compareValue),
    };
  });
  return row;
}

export function matrixRowToRulePayload(row, columns = [], context = {}) {
  const columnList = Array.isArray(columns) ? columns : [];
  const source = row?.sourceRule || {};
  const payload = {
    ...clone(source),
    ...clone(context),
    ...clone(row),
  };
  delete payload.cells;
  delete payload.sourceRule;
  delete payload.rateValue;
  delete payload.amountValue;
  payload.ruleId = row?.ruleId ?? source.ruleId;
  payload.ruleType = normalized(row?.ruleType || source.ruleType) === "FIXED_AMOUNT"
    ? "FIXED_AMOUNT"
    : "FIXED_RATE";
  payload.pricingMode = "TYPED";
  payload.conditionLogic = "AND";
  payload.priority = Number(row?.priority ?? source.priority ?? 100);
  payload.status = row?.status ?? source.status ?? "0";
  payload.pricingConfig = {
    ...(clone(source.pricingConfig) || {}),
    ...(clone(row?.pricingConfig) || {}),
  };
  if (payload.ruleType === "FIXED_RATE") {
    payload.pricingConfig.rateValue = row?.rateValue ?? payload.pricingConfig.rateValue;
    delete payload.pricingConfig.amountValue;
  } else {
    payload.pricingConfig.amountValue = row?.amountValue ?? payload.pricingConfig.amountValue;
    delete payload.pricingConfig.rateValue;
  }
  const sortBy = new Map(columnList.map((column, index) => [String(column.variableCode), Number(column.sortNo ?? index + 1)]));
  payload.conditions = columnList
    .filter((column) => !isWildcardCell(row?.cells?.[column.variableCode]))
    .sort((left, right) => (sortBy.get(String(left.variableCode)) || 0) - (sortBy.get(String(right.variableCode)) || 0))
    .map((column, index) => {
      const cell = normalizeMatrixCell(row.cells[column.variableCode]);
      return {
        sceneId: context.sceneId ?? row?.sceneId ?? source.sceneId,
        groupNo: 1,
        sortNo: index + 1,
        variableCode: column.variableCode,
        displayName: column.variableName,
        operatorCode: cell.operatorCode,
        compareValue: cell.value,
        status: "0",
      };
    });
  return payload;
}

function cellFor(row, column) {
  return normalizeMatrixCell(row?.cells?.[column?.variableCode]);
}

function specificity(row, columns) {
  return (Array.isArray(columns) ? columns : [])
    .map((column) => cellFor(row, column))
    .filter((cell) => !isWildcardCell(cell)).length;
}

function numericInterval(cell) {
  if (isWildcardCell(cell)) {
    return { lower: null, lowerInclusive: false, upper: null, upperInclusive: false };
  }
  if (!isStrictFiniteDecimal(cell.value)) return null;
  const decimal = String(cell.value).trim();
  switch (cell.operatorCode) {
    case "EQ":
      return { lower: decimal, lowerInclusive: true, upper: decimal, upperInclusive: true };
    case "GT":
      return { lower: decimal, lowerInclusive: false, upper: null, upperInclusive: false };
    case "GE":
      return { lower: decimal, lowerInclusive: true, upper: null, upperInclusive: false };
    case "LT":
      return { lower: null, lowerInclusive: false, upper: decimal, upperInclusive: false };
    case "LE":
      return { lower: null, lowerInclusive: false, upper: decimal, upperInclusive: true };
    default:
      return null;
  }
}

function intervalsOverlap(left, right) {
  if (!left || !right) return true;
  if (left.upper !== null && right.lower !== null) {
    const comparison = compareDecimals(left.upper, right.lower);
    if (comparison < 0) return false;
    if (comparison === 0 && !(left.upperInclusive && right.lowerInclusive)) return false;
  }
  if (right.upper !== null && left.lower !== null) {
    const comparison = compareDecimals(right.upper, left.lower);
    if (comparison < 0) return false;
    if (comparison === 0 && !(right.upperInclusive && left.lowerInclusive)) return false;
  }
  return true;
}

function cellsOverlap(left, right, column) {
  const leftCell = cellFor(left, column);
  const rightCell = cellFor(right, column);
  if (column?.matrixType === "number") return intervalsOverlap(numericInterval(leftCell), numericInterval(rightCell));
  if (isWildcardCell(leftCell) || isWildcardCell(rightCell)) return true;
  // Matrix option/text/boolean cells are EQ-only. Unknown operators fail
  // closed and remain conflicting until they are repaired in advanced mode.
  if (leftCell.operatorCode !== "EQ" || rightCell.operatorCode !== "EQ") return true;
  return leftCell.value === rightCell.value;
}

function overlaps(left, right, columns) {
  return (Array.isArray(columns) ? columns : []).every((column) => cellsOverlap(left, right, column));
}

function cellSignature(value) {
  const cell = normalizeMatrixCell(value);
  return isWildcardCell(cell)
    ? MATRIX_WILDCARD_VALUE
    : `${cell.operatorCode}:${cell.value}`;
}

function sameSignature(left, right, columns) {
  return (Array.isArray(columns) ? columns : []).every((column) => (
    cellSignature(left?.cells?.[column.variableCode]) === cellSignature(right?.cells?.[column.variableCode])
  ));
}

export function validateMatrixRows(rows = [], columns = []) {
  const errors = [];
  const warnings = [];
  const rowList = Array.isArray(rows) ? rows : [];
  const columnList = Array.isArray(columns) ? columns : [];
  rowList.forEach((row, index) => {
    columnList.forEach((column) => {
      const cell = cellFor(row, column);
      const wildcard = isWildcardCell(cell);
      const options = Array.isArray(column.options) ? column.options : [];
      if (!wildcard && (!cell.value || String(cell.value).trim() === "")) {
        errors.push({
          code: "MISSING_CELL",
          rowIndexes: [index],
          variableCode: column.variableCode,
          message: `第 ${index + 1} 行的${column.variableName || column.variableCode}必须选择具体值或显式选择全部（不限）。`,
        });
        return;
      }
      if (wildcard) return;
      if (!columnOperatorCodes(column).includes(cell.operatorCode)) {
        errors.push({
          code: "INVALID_OPERATOR",
          rowIndexes: [index],
          variableCode: column.variableCode,
          message: `第 ${index + 1} 行的${column.variableName || column.variableCode}不支持“${cell.operatorCode}”操作符。`,
        });
        return;
      }
      if (column.matrixType === "number" && !isStrictFiniteDecimal(cell.value)) {
        errors.push({
          code: "INVALID_NUMBER",
          rowIndexes: [index],
          variableCode: column.variableCode,
          message: `第 ${index + 1} 行的${column.variableName || column.variableCode}必须填写有效数值或选择不限。`,
        });
        return;
      }
      if (column.matrixType === "text" && column.maxLength && String(cell.value).length > column.maxLength) {
        errors.push({
          code: "TEXT_TOO_LONG",
          rowIndexes: [index],
          variableCode: column.variableCode,
          message: `第 ${index + 1} 行的${column.variableName || column.variableCode}不能超过 ${column.maxLength} 个字符。`,
        });
        return;
      }
      if ((column.matrixType === "option" || column.matrixType === "boolean")
        && options.length
        && !activeOption(column, cell.value)) {
        errors.push({
          code: "STALE_OPTION",
          rowIndexes: [index],
          variableCode: column.variableCode,
          message: `第 ${index + 1} 行的${column.variableName || column.variableCode}选项已失效，请重新选择或改为全部。`,
        });
      }
    });
  });
  for (let leftIndex = 0; leftIndex < rowList.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < rowList.length; rightIndex += 1) {
      const left = rowList[leftIndex];
      const right = rowList[rightIndex];
      if (!overlaps(left, right, columnList)) continue;
      const samePriority = Number(left?.priority ?? 0) === Number(right?.priority ?? 0);
      const sameSpecificity = specificity(left, columnList) === specificity(right, columnList);
      if (sameSignature(left, right, columnList)) {
        errors.push({
          code: "DUPLICATE",
          rowIndexes: [leftIndex, rightIndex],
          message: `第 ${leftIndex + 1} 行与第 ${rightIndex + 1} 行条件完全重复。`,
        });
      } else if (samePriority || sameSpecificity) {
        errors.push({
          code: "OVERLAP",
          rowIndexes: [leftIndex, rightIndex],
          message: `第 ${leftIndex + 1} 行与第 ${rightIndex + 1} 行存在无法由优先级和条件具体度唯一裁决的重叠。`,
        });
      } else {
        warnings.push({
          code: "PRIORITY_OVERRIDE",
          rowIndexes: [leftIndex, rightIndex],
          message: `第 ${leftIndex + 1} 行与第 ${rightIndex + 1} 行存在重叠，将按优先级和条件具体度裁决，请确认业务口径。`,
        });
      }
    }
  }
  return { valid: errors.length === 0, errors, warnings };
}
