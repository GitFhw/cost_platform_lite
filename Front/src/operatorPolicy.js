export const NUMERIC_DATA_TYPES = new Set([
  "NUMBER",
  "NUMERIC",
  "DECIMAL",
  "FLOAT",
  "INTEGER",
  "INT",
  "INT32",
  "INT64",
  "LONG",
  "BIGINT",
  "SMALLINT",
  "TINYINT",
  "DOUBLE",
  "REAL",
  "BIGDECIMAL",
]);

export const DATE_ONLY_DATA_TYPES = new Set([
  "DATE",
  "DATE_RANGE",
  "LOCALDATE",
  "LOCALDATE_RANGE",
  "YEAR_MONTH",
  "MONTH",
]);

export const DATETIME_DATA_TYPES = new Set([
  "DATETIME",
  "DATETIME_RANGE",
  "DATE_TIME",
  "DATE_TIME_RANGE",
  "LOCALDATETIME",
  "LOCALDATETIME_RANGE",
  "TIMESTAMP",
  "ZONEDDATETIME",
  "INSTANT",
]);

export const TIME_DATA_TYPES = new Set([
  "TIME",
  "TIME_OF_DAY",
  "TIME_OF_DAY_RANGE",
  "TIME_RANGE",
]);

export const DATE_DATA_TYPES = new Set([
  ...DATE_ONLY_DATA_TYPES,
  ...DATETIME_DATA_TYPES,
  ...TIME_DATA_TYPES,
]);

export const OPTION_SOURCE_TYPES = new Set(["PLATFORM_DICT", "BUSINESS_DICT"]);
export const FORMULA_TYPES = new Set(["FORMULA", "FORMULA_INPUT", "CALCULATED", "DERIVED"]);

const BOOLEAN_TYPES = new Set(["BOOLEAN", "BOOL"]);
const TEXT_TYPES = new Set(["STRING", "TEXT", "VARCHAR", "CHAR", "CODE", "ENUM"]);
const OPERATOR_LABELS = Object.freeze({
  EQ: "等于",
  NE: "不等于",
  GT: "大于",
  GE: "大于等于",
  LT: "小于",
  LE: "小于等于",
  IN: "属于任一",
  NOT_IN: "不属于任一",
  BETWEEN: "介于",
  EXPR: "表达式",
  IS_NULL: "为空",
  IS_NOT_NULL: "不为空",
});

const OPERATOR_VALUE_FLAGS = Object.freeze({
  IS_NULL: false,
  IS_NOT_NULL: false,
});

const MATRIX_OPERATORS = Object.freeze({
  option: ["EQ"],
  boolean: ["EQ"],
  number: ["EQ", "GT", "GE", "LT", "LE"],
  text: ["EQ"],
});

// Date comparisons are intentionally limited until the mother runtime has a
// typed date/time comparator. This prevents a date picker from promising a
// range that the execution chain still treats as BigDecimal.
const ADVANCED_OPERATORS = Object.freeze({
  option: ["EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"],
  boolean: ["EQ", "NE", "IS_NULL", "IS_NOT_NULL"],
  number: ["EQ", "NE", "GT", "GE", "LT", "LE", "BETWEEN", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"],
  text: ["EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"],
  date: ["EQ", "NE", "IS_NULL", "IS_NOT_NULL"],
  master: ["EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"],
  json: ["IS_NULL", "IS_NOT_NULL", "EXPR"],
  formula: ["EQ", "NE", "GT", "GE", "LT", "LE", "BETWEEN", "EXPR", "IS_NULL", "IS_NOT_NULL"],
});

function normalized(value) {
  return String(value || "").trim().toUpperCase();
}

export function normalizedVariableDataType(variable = {}) {
  return normalized(variable?.dataType || variable?.variableType);
}

export function normalizedOptionSourceType(variable = {}) {
  return normalized(variable?.optionSourceType || variable?.optionSource || "NONE");
}

export function variableKind(variable = {}) {
  const relationType = normalized(variable?.relationType || variable?.relation);
  const sourceType = normalized(variable?.sourceType || variable?.variableSourceType);
  const variableType = normalized(variable?.variableType);
  const dataType = normalizedVariableDataType(variable);
  const optionSourceType = normalizedOptionSourceType(variable);

  if (FORMULA_TYPES.has(relationType)
    || FORMULA_TYPES.has(sourceType)
    || FORMULA_TYPES.has(variableType)) return "formula";
  if (optionSourceType === "BUSINESS_MASTER"
    || sourceType === "BUSINESS_MASTER"
    || sourceType === "MASTER") return "master";
  // An explicit temporal data type owns the editor semantics. A dictionary
  // source on a date variable must not turn it into a selectable matrix key.
  if (DATE_DATA_TYPES.has(dataType)) return "date";
  if (OPTION_SOURCE_TYPES.has(optionSourceType)
    || variable?.dictType
    || ["DICT", "DICTIONARY", "ENUM"].includes(variableType)) return "option";
  if (NUMERIC_DATA_TYPES.has(dataType)) return "number";
  if (BOOLEAN_TYPES.has(dataType)) return "boolean";
  if (dataType === "JSON") return "json";
  if (TEXT_TYPES.has(dataType) || TEXT_TYPES.has(variableType)) return "text";
  return "unknown";
}

export function operatorCodesForVariable(variable, mode = "advanced") {
  const kind = variableKind(variable);
  if (mode === "matrix") return [...(MATRIX_OPERATORS[kind] || [])];
  // Unknown metadata must fail closed. A full operator list would let a
  // host-side metadata omission turn into an invalid, hand-entered rule.
  return [...(ADVANCED_OPERATORS[kind] || [])];
}

export function operatorOptionsForVariable(variable, mode = "advanced") {
  return operatorCodesForVariable(variable, mode).map((code) => ({
    value: code,
    label: OPERATOR_LABELS[code] || code,
    requiresValue: OPERATOR_VALUE_FLAGS[code] !== false,
    multiple: ["IN", "NOT_IN"].includes(code),
  }));
}

export function matrixTypeForVariable(variable) {
  const kind = variableKind(variable);
  return ["option", "number", "boolean", "text"].includes(kind) ? kind : "";
}

export function isMatrixSupportedVariable(variable) {
  return Boolean(matrixTypeForVariable(variable));
}

export function isOperatorAllowed(variable, operatorCode, mode = "advanced") {
  return operatorCodesForVariable(variable, mode).includes(normalized(operatorCode));
}

export function operatorRequiresValue(operatorCode) {
  return OPERATOR_VALUE_FLAGS[normalized(operatorCode)] !== false;
}

export function operatorLabel(operatorCode) {
  const code = normalized(operatorCode);
  return OPERATOR_LABELS[code] || code;
}
