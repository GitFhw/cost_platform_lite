import assert from "node:assert/strict";
import test from "node:test";

import {
  MATRIX_WILDCARD_VALUE,
  createEmptyMatrixRow,
  isMatrixCompatibleRule,
  matrixColumnsFromVariables,
  matrixRowToRulePayload,
  ruleToMatrixRow,
  isStrictFiniteDecimal,
  validateMatrixRows,
} from "./rateMatrix.js";
import { operatorOptionsForVariable } from "./operatorPolicy.js";

const variables = [
  {
    variableCode: "tradeType",
    variableName: "贸易类型",
    dataType: "STRING",
    optionSourceType: "PLATFORM_DICT",
    dictType: "cost_trade_type",
    options: [
      { label: "外贸", value: "OUT" },
      { label: "内贸", value: "IN" },
      { label: "停用", value: "DISABLED", disabled: true },
    ],
    hasOptionSnapshot: true,
    sortNo: 20,
  },
  {
    variableCode: "cargoType",
    variableName: "货类",
    dataType: "STRING",
    optionSourceType: "BUSINESS_MASTER",
    sortNo: 30,
  },
  {
    variableCode: "quantity",
    variableName: "数量",
    dataType: "NUMBER",
    relationType: "TIER_BASIS",
    sortNo: 10,
  },
  {
    variableCode: "occurDate",
    variableName: "作业日期",
    dataType: "DATE",
    optionSourceType: "NONE",
    sortNo: 40,
  },
];

const fixedRule = {
  ruleId: 11,
  ruleCode: "R-OUT",
  ruleName: "外贸费率",
  ruleType: "FIXED_RATE",
  pricingMode: "TYPED",
  conditionLogic: "AND",
  priority: 100,
  quantityVariableCode: "quantity",
  pricingConfig: { rateValue: 12.5 },
  conditions: [
    {
      groupNo: 1,
      operatorCode: "EQ",
      variableCode: "tradeType",
      displayName: "贸易类型",
      compareValue: "OUT",
    },
  ],
};

test("matrix columns follow configured fee variables and exclude master/date/tier dimensions", () => {
  const columns = matrixColumnsFromVariables(variables);
  assert.deepEqual(columns.map((item) => item.variableCode), ["tradeType"]);
  assert.equal(columns[0].matrixType, "option");
  assert.deepEqual(columns[0].operators.map((item) => item.value), ["EQ"]);
});

test("simple fixed rule maps to explicit wildcard cells and round-trips to EQ conditions", () => {
  const columns = matrixColumnsFromVariables(variables);
  const row = ruleToMatrixRow(fixedRule, columns);
  assert.ok(row);
  assert.deepEqual(row.cells.tradeType, { operatorCode: "EQ", value: "OUT" });
  assert.equal(row.cells.missing, undefined);

  const payload = matrixRowToRulePayload(row, columns, { sceneId: 7, feeId: 8 });
  assert.equal(payload.sceneId, 7);
  assert.equal(payload.feeId, 8);
  assert.deepEqual(payload.conditions.map((item) => [item.variableCode, item.operatorCode, item.compareValue]), [
    ["tradeType", "EQ", "OUT"],
  ]);
  assert.equal(payload.pricingConfig.rateValue, 12.5);
});

test("only the explicit ALL token is omitted from persisted conditions", () => {
  const columns = matrixColumnsFromVariables([variables[0]]);
  const row = createEmptyMatrixRow(columns, { ruleCode: "R-ALL", ruleName: "全部", priority: 10 });
  assert.deepEqual(row.cells.tradeType, { operatorCode: "ALL", value: MATRIX_WILDCARD_VALUE });
  const payload = matrixRowToRulePayload(row, columns, { sceneId: 7, feeId: 8 });
  assert.deepEqual(payload.conditions, []);

  row.cells.tradeType = "";
  const invalidPayload = matrixRowToRulePayload(row, columns, { sceneId: 7, feeId: 8 });
  assert.equal(invalidPayload.conditions[0].compareValue, "");
  assert.ok(validateMatrixRows([row], columns).errors.some((item) => item.code === "MISSING_CELL"));
});

test("grouped, tiered, IN and unknown-column rules stay in advanced mode", () => {
  const columns = matrixColumnsFromVariables([variables[0]]);
  assert.equal(isMatrixCompatibleRule({ ...fixedRule, pricingMode: "GROUPED" }, columns), false);
  assert.equal(isMatrixCompatibleRule({ ...fixedRule, ruleType: "TIER_RATE" }, columns), false);
  assert.equal(isMatrixCompatibleRule({
    ...fixedRule,
    conditions: [{ ...fixedRule.conditions[0], operatorCode: "IN", compareValue: "OUT,IN" }],
}, columns), false);
  assert.equal(isMatrixCompatibleRule({
    ...fixedRule,
    conditions: [{ ...fixedRule.conditions[0], variableCode: "cargoType" }],
  }, columns), false);
  assert.equal(ruleToMatrixRow({ ...fixedRule, pricingMode: "GROUPED" }, columns), null);
});

test("formula and structured date/time variables stay in advanced mode", () => {
  const columns = matrixColumnsFromVariables([
    {
      variableCode: "formulaAmount",
      variableName: "公式金额",
      dataType: "NUMBER",
      sourceType: "FORMULA_INPUT",
      sortNo: 10,
    },
    {
      variableCode: "workDateRange",
      variableName: "作业日期区间",
      dataType: "DATE_RANGE",
      optionSourceType: "PLATFORM_DICT",
      options: [{ label: "今天", value: "TODAY" }],
      sortNo: 20,
    },
    {
      variableCode: "workTimeRange",
      variableName: "作业时间区间",
      dataType: "TIME_OF_DAY_RANGE",
      optionSourceType: "PLATFORM_DICT",
      options: [{ label: "白班", value: "DAY" }],
      sortNo: 30,
    },
  ]);
  assert.deepEqual(columns, []);
});

test("temporal data types stay in advanced mode even with dictionary metadata", () => {
  const columns = matrixColumnsFromVariables([
    {
      variableCode: "workDateTime",
      variableName: "作业时间",
      dataType: "DATETIME",
      optionSourceType: "PLATFORM_DICT",
      dictType: "cost_datetime",
      sortNo: 10,
    },
    {
      variableCode: "workTime",
      variableName: "作业时段",
      dataType: "TIME_OF_DAY_RANGE",
      optionSourceType: "BUSINESS_DICT",
      optionSourceCode: "work_time",
      sortNo: 20,
    },
  ], {
    workDateTime: {
      options: [{ label: "今天", value: "TODAY" }],
      total: 1,
      hasMore: false,
      hasOptionSnapshot: true,
    },
    workTime: {
      options: [{ label: "白班", value: "DAY" }],
      total: 1,
      hasMore: false,
      hasOptionSnapshot: true,
    },
  });
  assert.deepEqual(columns, []);
});

test("platform dictionary without a complete option snapshot is excluded", () => {
  const variable = {
    variableCode: "platformTradeType",
    variableName: "平台贸易类型",
    dataType: "STRING",
    optionSourceType: "PLATFORM_DICT",
  };
  assert.deepEqual(matrixColumnsFromVariables([variable], {
    platformTradeType: {
      options: [],
      total: 0,
      hasMore: false,
      hasOptionSnapshot: false,
    },
  }), []);
});

test("dictionary variables without any option snapshot are excluded", () => {
  assert.deepEqual(matrixColumnsFromVariables([{
    variableCode: "missingDictionarySnapshot",
    variableName: "缺失字典快照",
    dataType: "STRING",
    optionSourceType: "PLATFORM_DICT",
  }]), []);
});

test("inline dictionary options require an explicit authoritative snapshot marker", () => {
  assert.deepEqual(matrixColumnsFromVariables([{
    variableCode: "inlineWithoutMarker",
    variableName: "未声明快照的内嵌字典",
    dataType: "STRING",
    optionSourceType: "BUSINESS_DICT",
    options: [{ label: "值", value: "VALUE" }],
  }]), []);
});

test("strict finite decimal validation rejects blank and non-numeric values", () => {
  assert.equal(isStrictFiniteDecimal(""), false);
  assert.equal(isStrictFiniteDecimal(" "), false);
  assert.equal(isStrictFiniteDecimal("NaN"), false);
  assert.equal(isStrictFiniteDecimal("Infinity"), false);
  assert.equal(isStrictFiniteDecimal("0x10"), false);
  assert.equal(isStrictFiniteDecimal("1.25"), true);
  assert.equal(isStrictFiniteDecimal(0), true);
});

test("dictionary-backed numeric variables use bounded options instead of numeric input", () => {
  const columns = matrixColumnsFromVariables([{
    variableCode: "numericDict",
    variableName: "数值字典",
    dataType: "NUMBER",
    optionSourceType: "BUSINESS_DICT",
  }], {
    numericDict: {
      options: [{ label: "一", value: "1" }],
      total: 1,
      hasMore: false,
      hasOptionSnapshot: true,
    },
  });
  assert.equal(columns[0]?.matrixType, "option");
  assert.deepEqual(columns[0]?.options, [{ label: "一", value: "1" }]);
});

test("a real dictionary value reserved for the wildcard keeps the column in advanced mode", () => {
  const columns = matrixColumnsFromVariables([{
    variableCode: "reservedCode",
    variableName: "保留字冲突",
    dataType: "STRING",
    optionSourceType: "PLATFORM_DICT",
  }], {
    reservedCode: {
      options: [{ label: "业务值", value: MATRIX_WILDCARD_VALUE }],
      total: 1,
      hasMore: false,
      hasOptionSnapshot: true,
    },
  });
  assert.deepEqual(columns, []);
});

test("a rule whose detail could not be loaded stays in advanced mode", () => {
  const columns = matrixColumnsFromVariables([variables[0]]);
  assert.equal(isMatrixCompatibleRule({
    ...fixedRule,
    matrixDetailUnavailable: true,
    conditions: [],
  }, columns), false);
});

test("invalid numeric cells and disabled dictionary options are blocked", () => {
  const optionColumns = matrixColumnsFromVariables([variables[0]]);
  assert.equal(optionColumns[0].options.find((item) => item.value === "DISABLED")?.disabled, true);
  const disabledRow = createEmptyMatrixRow(optionColumns, { ruleCode: "R-DISABLED", ruleName: "停用" });
  disabledRow.cells.tradeType = "DISABLED";
  assert.ok(validateMatrixRows([disabledRow], optionColumns).errors.some((item) => item.code === "STALE_OPTION"));

  const numericColumns = [{ variableCode: "quantity", variableName: "数量", matrixType: "number", options: [] }];
  const numericRow = createEmptyMatrixRow(numericColumns, { ruleCode: "R-NAN", ruleName: "非法数值" });
  numericRow.cells.quantity = "not-a-number";
  assert.ok(validateMatrixRows([numericRow], numericColumns).errors.some((item) => item.code === "INVALID_NUMBER"));
  numericRow.cells.quantity = "0x10";
  assert.ok(validateMatrixRows([numericRow], numericColumns).errors.some((item) => item.code === "INVALID_NUMBER"));
});

test("historical dictionary values and the structural wildcard stay in advanced mode", () => {
  const columns = matrixColumnsFromVariables([variables[0]]);
  assert.equal(isMatrixCompatibleRule({
    ...fixedRule,
    conditions: [{ ...fixedRule.conditions[0], compareValue: "REMOVED" }],
  }, columns), false);
  assert.equal(isMatrixCompatibleRule({
    ...fixedRule,
    conditions: [{ ...fixedRule.conditions[0], compareValue: MATRIX_WILDCARD_VALUE }],
  }, columns), false);
});

test("duplicate and same-priority wildcard overlaps are blocking conflicts", () => {
  const columns = matrixColumnsFromVariables([variables[0]]);
  const all = createEmptyMatrixRow(columns, { ruleId: 1, ruleCode: "ALL", priority: 100 });
  const specific = createEmptyMatrixRow(columns, { ruleId: 2, ruleCode: "OUT", priority: 100 });
  specific.cells.tradeType = "OUT";
  const duplicate = createEmptyMatrixRow(columns, { ruleId: 3, ruleCode: "ALL-2", priority: 100 });
  const duplicateResult = validateMatrixRows([all, duplicate], columns);
  assert.ok(duplicateResult.errors.some((item) => item.code === "DUPLICATE"));
  const overlapResult = validateMatrixRows([all, specific], columns);
  assert.ok(overlapResult.errors.some((item) => item.code === "OVERLAP"));
});

test("different priorities make an explicit fallback override a warning, not an ambiguous match", () => {
  const columns = matrixColumnsFromVariables([variables[0]]);
  const fallback = createEmptyMatrixRow(columns, { ruleId: 1, ruleCode: "ALL", priority: 10 });
  const specific = createEmptyMatrixRow(columns, { ruleId: 2, ruleCode: "OUT", priority: 100 });
  specific.cells.tradeType = "OUT";
  const result = validateMatrixRows([fallback, specific], columns);
  assert.equal(result.errors.length, 0);
  assert.ok(result.warnings.some((item) => item.code === "PRIORITY_OVERRIDE"));
});

test("plain text is a text input column and does not inherit dictionary operators", () => {
  const columns = matrixColumnsFromVariables([{
    variableCode: "jobRemark",
    variableName: "作业备注",
    dataType: "STRING",
    optionSourceType: "NONE",
    sortNo: 1,
  }]);
  assert.equal(columns[0].matrixType, "text");
  assert.deepEqual(columns[0].operators.map((item) => item.value), ["EQ"]);
  assert.equal(operatorOptionsForVariable({ dataType: "STRING" }).some((item) => item.value === "GT"), false);
});

test("numeric cells preserve the row-level comparison operator in the payload", () => {
  const columns = matrixColumnsFromVariables([{
    variableCode: "quantity",
    variableName: "数量",
    dataType: "NUMBER",
    sortNo: 1,
  }]);
  const row = createEmptyMatrixRow(columns, { ruleCode: "R-GT", ruleName: "大于十" });
  row.cells.quantity = { operatorCode: "GT", value: "10" };
  const validation = validateMatrixRows([row], columns);
  assert.equal(validation.errors.length, 0);
  const payload = matrixRowToRulePayload(row, columns, { sceneId: 7, feeId: 8 });
  assert.deepEqual(payload.conditions.map((item) => [item.operatorCode, item.compareValue]), [["GT", "10"]]);
});

test("numeric interval boundaries distinguish disjoint and overlapping rows", () => {
  const columns = matrixColumnsFromVariables([{
    variableCode: "quantity",
    variableName: "数量",
    dataType: "NUMBER",
    sortNo: 1,
  }]);
  const greaterThanTen = createEmptyMatrixRow(columns, { ruleCode: "R-GT", priority: 100 });
  greaterThanTen.cells.quantity = { operatorCode: "GT", value: "10" };
  const atMostTen = createEmptyMatrixRow(columns, { ruleCode: "R-LE", priority: 100 });
  atMostTen.cells.quantity = { operatorCode: "LE", value: "10" };
  assert.equal(validateMatrixRows([greaterThanTen, atMostTen], columns).errors.length, 0);

  const fromTen = createEmptyMatrixRow(columns, { ruleCode: "R-GE", priority: 100 });
  fromTen.cells.quantity = { operatorCode: "GE", value: "10" };
  assert.ok(validateMatrixRows([fromTen, atMostTen], columns).errors.some((item) => item.code === "OVERLAP"));
});

test("numeric interval comparisons preserve decimal precision beyond safe integers", () => {
  const columns = matrixColumnsFromVariables([{
    variableCode: "quantity",
    variableName: "数量",
    dataType: "NUMBER",
    sortNo: 1,
  }]);
  const first = createEmptyMatrixRow(columns, { ruleCode: "R-9007199254740992", priority: 100 });
  first.cells.quantity = { operatorCode: "EQ", value: "9007199254740992" };
  const second = createEmptyMatrixRow(columns, { ruleCode: "R-9007199254740993", priority: 100 });
  second.cells.quantity = { operatorCode: "EQ", value: "9007199254740993" };
  assert.equal(validateMatrixRows([first, second], columns).errors.length, 0);
});

test("unknown variable metadata fails closed for advanced operators", () => {
  assert.deepEqual(operatorOptionsForVariable({ variableCode: "missingType" }), []);
});

test("numeric aliases use the same typed matrix policy as NUMBER", () => {
  for (const dataType of ["NUMERIC", "FLOAT", "INT", "INT64", "BIGINT"]) {
    const columns = matrixColumnsFromVariables([{
      variableCode: `amount_${dataType}`,
      variableName: dataType,
      dataType,
    }]);
    assert.equal(columns[0]?.matrixType, "number", dataType);
    assert.deepEqual(columns[0]?.operators.map((item) => item.value), ["EQ", "GT", "GE", "LT", "LE"]);
  }
});
