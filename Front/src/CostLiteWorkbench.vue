<script setup lang="ts">
import {
  computed,
  inject,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import {
  Check,
  Coin,
  Connection,
  Delete,
  DocumentChecked,
  Edit,
  Link,
  Plus,
  Promotion,
  Refresh,
  Search,
  Setting,
  Tickets,
  VideoPlay,
  View,
  Warning,
} from "@element-plus/icons-vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import {
  COST_LITE_API_KEY,
  type CostLiteApi,
  type CostLiteRecord,
  type CostLiteDictionary,
} from "./costLiteApi";

const DICTIONARY_TYPES = [
  "cost_business_domain",
  "cost_scene_status",
  "cost_scene_type",
  "cost_fee_status",
  "cost_unit_code",
  "cost_variable_group_status",
  "cost_variable_type",
  "cost_variable_source_type",
  "cost_variable_data_type",
  "cost_variable_auth_type",
  "cost_variable_sync_mode",
  "cost_variable_cache_policy",
  "cost_variable_fallback_policy",
  "cost_variable_status",
  "cost_rule_status",
  "cost_rule_type",
  "cost_rule_condition_logic",
  "cost_rule_operator",
  "cost_rule_interval_mode",
  "cost_publish_version_status",
  "cost_simulation_status",
  "cost_calc_task_type",
  "cost_calc_task_status",
  "cost_result_status",
] as const;

const props = withDefaults(
  defineProps<{
    api?: CostLiteApi;
    pageSize?: number;
  }>(),
  {
    pageSize: 100,
  },
);

const injectedApi = inject(COST_LITE_API_KEY, undefined);
const resolvedApi = computed(() => props.api || injectedApi);
const rollbackSupported = computed(() => Boolean(resolvedApi.value?.rollbackVersion));

function api(): CostLiteApi {
  if (!resolvedApi.value) {
    throw new Error("CostLiteWorkbench 未配置 CostLiteApi");
  }
  return resolvedApi.value;
}

const loading = reactive({
  bootstrap: false,
  scenes: false,
  context: false,
  rules: false,
  logs: false,
  results: false,
  detail: false,
  precheck: false,
  template: false,
  saving: false,
  running: false,
  publishing: false,
});

const bootstrap = ref<CostLiteRecord>({});
const connected = ref(false);
const dictionaryOptions = ref<CostLiteDictionary>({});
const sceneKeyword = ref("");
const scenes = ref<CostLiteRecord[]>([]);
const selectedSceneId = ref<number | string>();
const fees = ref<CostLiteRecord[]>([]);
const selectedFeeId = ref<number | string>();
const variables = ref<CostLiteRecord[]>([]);
const variableGroups = ref<CostLiteRecord[]>([]);
const rules = ref<CostLiteRecord[]>([]);
const versions = ref<CostLiteRecord[]>([]);
const formulaOptions = ref<CostLiteRecord[]>([]);
const feeGovernance = ref<CostLiteRecord>({});
const centerTab = ref<"fees" | "variables">("fees");
const bottomTab = ref<"simulation" | "logs" | "results">("simulation");
const detailVisible = ref(false);
const detailTitle = ref("详情");
const detailJson = ref<unknown>({});
const precheckResult = ref<CostLiteRecord>({});

const selectedScene = computed(() =>
  scenes.value.find((item) => String(item.sceneId) === String(selectedSceneId.value)),
);
const selectedFee = computed(() =>
  fees.value.find((item) => String(item.feeId) === String(selectedFeeId.value)),
);
const activeVersionId = computed(() => selectedScene.value?.activeVersionId);
const activeVersion = computed(() =>
  versions.value.find((item) => String(item.versionId) === String(activeVersionId.value)),
);
const filteredScenes = computed(() => {
  const keyword = sceneKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return scenes.value;
  }
  return scenes.value.filter((item) =>
    [item.sceneCode, item.sceneName, item.businessDomain]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword)),
  );
});

const linkedVariableCodes = computed(() => {
  const codes = new Set<string>();
  const governanceCandidates = [
    feeGovernance.value.variables,
    feeGovernance.value.variableList,
    feeGovernance.value.linkedVariables,
  ];
  for (const candidate of governanceCandidates) {
    if (Array.isArray(candidate)) {
      candidate.forEach((item) => {
        const code = item?.variableCode || item?.code;
        if (code) {
          codes.add(String(code));
        }
      });
    }
  }
  rules.value.forEach((rule) => {
    if (rule.quantityVariableCode) {
      codes.add(String(rule.quantityVariableCode));
    }
    const conditions = Array.isArray(rule.conditions) ? rule.conditions : [];
    conditions.forEach((condition: CostLiteRecord) => {
      if (condition.variableCode) {
        codes.add(String(condition.variableCode));
      }
    });
  });
  return Array.from(codes);
});

function dictOptions(dictType: string): Array<{ label: string; value: string }> {
  return dictionaryOptions.value[dictType] || [];
}

function dictLabel(dictType: string, value: unknown): string {
  if (value === undefined || value === null || value === "") return "-";
  const match = dictOptions(dictType).find((item) => String(item.value) === String(value));
  return match?.label || String(value);
}

function versionStatusText(version: CostLiteRecord): string {
  if (String(version.versionId) === String(activeVersionId.value)) return "生效中";
  const value = version.versionStatus || version.status;
  const mapping: Record<string, string> = {
    DRAFT: "草稿",
    PUBLISHED: "已发布",
    ACTIVE: "生效中",
    INACTIVE: "未生效",
    ROLLED_BACK: "已回退",
  };
  return dictLabel("cost_publish_version_status", value) || mapping[String(value)] || String(value || "已发布");
}

function ruleTypeText(type: unknown): string {
  const mapping: Record<string, string> = {
    FIXED_RATE: "固定费率",
    FIXED_AMOUNT: "固定金额",
    TIER_RATE: "阶梯费率",
    FORMULA: "金额公式",
  };
  return dictLabel("cost_rule_type", type) || mapping[String(type)] || String(type || "-");
}

function variableSourceText(sourceType: unknown): string {
  return dictLabel("cost_variable_source_type", sourceType);
}

function variableDataTypeText(dataType: unknown): string {
  return dictLabel("cost_variable_data_type", dataType);
}

function sceneTypeText(sceneType: unknown): string {
  return dictLabel("cost_scene_type", sceneType);
}

function statusTagType(dictType: string, value: unknown): "success" | "info" | "warning" | "danger" {
  const normalized = String(value ?? "");
  if (normalized === "0" || normalized === "SUCCESS" || normalized === "ACTIVE") return "success";
  if (normalized === "1" || normalized === "FAILED" || normalized === "ERROR") return "danger";
  if (normalized === "2" || normalized === "DRAFT" || normalized === "RUNNING") return "warning";
  return dictType.includes("status") ? "info" : "info";
}

function formatDateTime(value: unknown): string {
  if (!value) return "-";
  return String(value).replace("T", " ").replace(/\.\d+$/, "");
}

function errorMessage(error: unknown, fallback: string): string {
  const responseData = (error as CostLiteRecord)?.response?.data;
  if (responseData?.msg || responseData?.message) {
    return String(responseData.msg || responseData.message);
  }
  if ((error as CostLiteRecord)?.msg || (error as CostLiteRecord)?.message) {
    return String((error as CostLiteRecord).msg || (error as CostLiteRecord).message);
  }
  return error instanceof Error && error.message ? error.message : fallback;
}

async function confirmAction(message: string, title: string, options: CostLiteRecord = {}): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, { type: "warning", ...options });
    return true;
  } catch {
    return false;
  }
}

function openJsonDetail(title: string, value: unknown): void {
  detailTitle.value = title;
  detailJson.value = value;
  detailVisible.value = true;
}

function governanceSummary(governance: CostLiteRecord): string {
  const labels: Array<[string, string]> = [
    ["feeCount", "费目"],
    ["variableCount", "要素"],
    ["variableGroupCount", "要素分组"],
    ["ruleCount", "规则"],
    ["conditionCount", "条件"],
    ["tierCount", "阶梯"],
    ["publishedVersionCount", "发布版本"],
    ["resultLedgerCount", "结果台账"],
    ["simulationRecordCount", "调用日志"],
    ["inputBatchCount", "输入批次"],
    ["traceCount", "结果追溯"],
    ["taskCount", "正式任务"],
  ];
  const items = labels
    .filter(([key]) => Number(governance?.[key] || 0) > 0)
    .map(([key, label]) => `${label} ${governance[key]}`);
  return items.length ? `当前关联：${items.join("、")}` : "当前没有发现下游关联数据";
}

async function confirmGovernedDelete(
  label: string,
  governanceLoader?: () => Promise<CostLiteRecord>,
): Promise<boolean> {
  if (governanceLoader) {
    try {
      const governance = await governanceLoader();
      if (governance.canDelete === false) {
        openJsonDetail(`${label}删除影响`, governance);
        ElMessage.warning(governance.removeBlockingReason || `${label}存在下游关联，暂不允许删除`);
        return false;
      }
      const summary = governanceSummary(governance);
      return confirmAction(`确认删除${label}？\n\n${summary}`, `删除${label}`, {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
      });
    } catch (error) {
      ElMessage.error(errorMessage(error, `${label}治理检查失败`));
      return false;
    }
  }
  return confirmAction(`确认删除${label}？`, `删除${label}`, {
    confirmButtonText: "确认删除",
    cancelButtonText: "取消",
  });
}

async function initialize(): Promise<void> {
  loading.bootstrap = true;
  try {
    const [health, info, dictionaries] = await Promise.all([
      api().health(),
      api().bootstrap(),
      api().listDictionaries([...DICTIONARY_TYPES]),
    ]);
    connected.value = health.service === "UP" || health.database === "UP";
    bootstrap.value = info;
    dictionaryOptions.value = dictionaries;
    await loadScenes();
    if (bottomTab.value === "logs") await loadBillingLogs();
    if (bottomTab.value === "results") await loadResults();
  } catch (error) {
    connected.value = false;
    ElMessage.error(errorMessage(error, "轻量计费服务连接失败"));
  } finally {
    loading.bootstrap = false;
  }
}

let sceneRequestId = 0;
let contextRequestId = 0;
let rulesRequestId = 0;

async function loadScenes(
  preferredSceneId?: number | string,
  preferredSceneCode?: string,
): Promise<void> {
  const requestId = ++sceneRequestId;
  loading.scenes = true;
  try {
    const page = await api().listScenes({ pageNum: 1, pageSize: props.pageSize });
    if (requestId !== sceneRequestId) return;
    scenes.value = page.rows;
    const codeTarget = preferredSceneCode
      ? scenes.value.find((item) => String(item.sceneCode) === String(preferredSceneCode))?.sceneId
      : undefined;
    const target = preferredSceneId ?? codeTarget ?? selectedSceneId.value;
    const exists = scenes.value.some((item) => String(item.sceneId) === String(target));
    const previousSceneId = selectedSceneId.value;
    selectedSceneId.value = exists ? target : scenes.value[0]?.sceneId;
    if (!selectedSceneId.value) {
      clearSceneContext();
    } else if (String(previousSceneId) === String(selectedSceneId.value)) {
      await loadSceneContext();
    }
  } catch (error) {
    if (requestId === sceneRequestId) {
      ElMessage.error(errorMessage(error, "场景列表加载失败"));
    }
  } finally {
    if (requestId === sceneRequestId) loading.scenes = false;
  }
}

function clearSceneContext(): void {
  fees.value = [];
  variables.value = [];
  variableGroups.value = [];
  rules.value = [];
  versions.value = [];
  formulaOptions.value = [];
  selectedFeeId.value = undefined;
  feeGovernance.value = {};
}

async function loadSceneContext(
  preferredFeeId?: number | string,
  preferredFeeCode?: string,
): Promise<void> {
  const sceneId = selectedSceneId.value;
  if (!sceneId) {
    clearSceneContext();
    return;
  }
  const requestId = ++contextRequestId;
  loading.context = true;
  try {
    const [feePage, variablePage, groups, versionPage, formulas] = await Promise.all([
      api().listFees(sceneId, { pageNum: 1, pageSize: props.pageSize }),
      api().listVariables(sceneId, { pageNum: 1, pageSize: props.pageSize }),
      api().listVariableGroups(sceneId),
      api().listVersions(sceneId, { pageNum: 1, pageSize: props.pageSize }),
      api().listFormulaOptions(sceneId),
    ]);
    if (requestId !== contextRequestId || String(sceneId) !== String(selectedSceneId.value)) return;
    fees.value = feePage.rows;
    variables.value = variablePage.rows;
    variableGroups.value = groups;
    versions.value = versionPage.rows;
    formulaOptions.value = formulas;
    const codeTarget = preferredFeeCode
      ? fees.value.find((item) => String(item.feeCode) === String(preferredFeeCode))?.feeId
      : undefined;
    const targetFeeId = preferredFeeId ?? codeTarget ?? selectedFeeId.value;
    const feeExists = fees.value.some((item) => String(item.feeId) === String(targetFeeId));
    const previousFeeId = selectedFeeId.value;
    selectedFeeId.value = feeExists ? targetFeeId : fees.value[0]?.feeId;
    if (!selectedFeeId.value) {
      rules.value = [];
      feeGovernance.value = {};
    } else if (String(previousFeeId) === String(selectedFeeId.value)) {
      await loadRules();
    }
  } catch (error) {
    if (requestId === contextRequestId) {
      ElMessage.error(errorMessage(error, "场景配置加载失败"));
    }
  } finally {
    if (requestId === contextRequestId) loading.context = false;
  }
}

async function loadRules(): Promise<void> {
  if (!selectedSceneId.value || !selectedFeeId.value) {
    rules.value = [];
    feeGovernance.value = {};
    return;
  }
  const requestId = ++rulesRequestId;
  loading.rules = true;
  try {
    const [page, governance] = await Promise.all([
      api().listRules(selectedSceneId.value, selectedFeeId.value, {
        pageNum: 1,
        pageSize: props.pageSize,
      }),
      api().getFeeGovernance(selectedFeeId.value),
    ]);
    if (requestId !== rulesRequestId) return;
    rules.value = page.rows;
    feeGovernance.value = governance;
  } catch (error) {
    if (requestId === rulesRequestId) {
      ElMessage.error(errorMessage(error, "费率规则加载失败"));
    }
  } finally {
    if (requestId === rulesRequestId) loading.rules = false;
  }
}

function selectScene(scene: CostLiteRecord): void {
  selectedSceneId.value = scene.sceneId;
  clearSceneContext();
}

function selectFee(fee: CostLiteRecord): void {
  selectedFeeId.value = fee.feeId;
}

watch(selectedSceneId, () => {
  clearSceneContext();
  logQuery.pageNum = 1;
  resultQuery.pageNum = 1;
  void loadSceneContext();
  if (bottomTab.value === "logs") void loadBillingLogs();
  if (bottomTab.value === "results") void loadResults();
});
watch(selectedFeeId, () => void loadRules());

const sceneDialogVisible = ref(false);
const sceneFormRef = ref<FormInstance>();
const sceneForm = reactive<CostLiteRecord>({});
const sceneRules: FormRules = {
  sceneCode: [{ required: true, message: "请输入场景编码", trigger: "blur" }],
  sceneName: [{ required: true, message: "请输入场景名称", trigger: "blur" }],
  businessDomain: [{ required: true, message: "请输入业务域", trigger: "blur" }],
  sceneType: [{ required: true, message: "请选择场景类型", trigger: "change" }],
};

function resetObject(target: CostLiteRecord, value: CostLiteRecord): void {
  Object.keys(target).forEach((key) => delete target[key]);
  Object.assign(target, value);
}

async function openSceneDialog(scene?: CostLiteRecord): Promise<void> {
  resetObject(sceneForm, {
    sceneId: scene?.sceneId,
    sceneCode: scene?.sceneCode || "",
    sceneName: scene?.sceneName || "",
    businessDomain: scene?.businessDomain || "",
    orgCode: scene?.orgCode || "",
    sceneType: scene?.sceneType || "CONTRACT",
    defaultObjectDimension: scene?.defaultObjectDimension || "",
    status: scene ? (scene.status ?? "0") : "0",
    remark: scene?.remark || "",
  });
  sceneDialogVisible.value = true;
  if (!scene?.sceneId) return;
  loading.detail = true;
  try {
    const detail = await api().getScene(scene.sceneId);
    resetObject(sceneForm, { ...sceneForm, ...detail });
  } catch (error) {
    ElMessage.error(errorMessage(error, "场景详情加载失败"));
  } finally {
    loading.detail = false;
  }
}

async function saveScene(): Promise<void> {
  if (!(await sceneFormRef.value?.validate().catch(() => false))) return;
  loading.saving = true;
  try {
    const sceneId = sceneForm.sceneId;
    const sceneCode = String(sceneForm.sceneCode || "");
    if (sceneId) await api().updateScene({ ...sceneForm });
    else await api().createScene({ ...sceneForm });
    ElMessage.success("场景已保存");
    sceneDialogVisible.value = false;
    await loadScenes(sceneId, sceneCode);
    await loadSceneContext();
  } catch (error) {
    ElMessage.error(errorMessage(error, "场景保存失败"));
  } finally {
    loading.saving = false;
  }
}

async function deleteScene(scene: CostLiteRecord): Promise<void> {
  const confirmed = await confirmGovernedDelete(
    `场景“${scene.sceneName || scene.sceneCode}”`,
    api().getSceneGovernance
      ? () => api().getSceneGovernance!(scene.sceneId)
      : undefined,
  );
  if (!confirmed) return;
  try {
    await api().deleteScenes([scene.sceneId]);
    ElMessage.success("场景已删除");
    await loadScenes();
  } catch (error) {
    ElMessage.error(errorMessage(error, "场景删除失败"));
  }
}

const feeDialogVisible = ref(false);
const feeFormRef = ref<FormInstance>();
const feeForm = reactive<CostLiteRecord>({});
const feeRules: FormRules = {
  feeCode: [{ required: true, message: "请输入费目编码", trigger: "blur" }],
  feeName: [{ required: true, message: "请输入费目名称", trigger: "blur" }],
};

async function openFeeDialog(fee?: CostLiteRecord): Promise<void> {
  if (!selectedSceneId.value) return;
  resetObject(feeForm, {
    feeId: fee?.feeId,
    sceneId: selectedSceneId.value,
    feeCode: fee?.feeCode || "",
    feeName: fee?.feeName || "",
    feeCategory: fee?.feeCategory || "",
    unitCode: fee?.unitCode || "",
    factorSummary: fee?.factorSummary || "",
    scopeDescription: fee?.scopeDescription || "",
    objectDimension: fee?.objectDimension || selectedScene.value?.defaultObjectDimension || "",
    sortNo: Number(fee?.sortNo ?? 10),
    status: fee?.status ?? "0",
    remark: fee?.remark || "",
  });
  feeDialogVisible.value = true;
  if (!fee?.feeId) return;
  loading.detail = true;
  try {
    const detail = await api().getFee(fee.feeId);
    resetObject(feeForm, { ...feeForm, ...detail, sceneId: selectedSceneId.value });
  } catch (error) {
    ElMessage.error(errorMessage(error, "费目详情加载失败"));
  } finally {
    loading.detail = false;
  }
}

async function saveFee(): Promise<void> {
  if (!(await feeFormRef.value?.validate().catch(() => false))) return;
  loading.saving = true;
  try {
    const feeId = feeForm.feeId;
    const feeCode = String(feeForm.feeCode || "");
    if (feeId) await api().updateFee({ ...feeForm });
    else await api().createFee({ ...feeForm });
    ElMessage.success("费目已保存");
    feeDialogVisible.value = false;
    await loadSceneContext(feeId, feeCode);
  } catch (error) {
    ElMessage.error(errorMessage(error, "费目保存失败"));
  } finally {
    loading.saving = false;
  }
}

async function deleteFee(fee: CostLiteRecord): Promise<void> {
  const confirmed = await confirmGovernedDelete(
    `费目“${fee.feeName || fee.feeCode}”`,
    () => api().getFeeGovernance(fee.feeId),
  );
  if (!confirmed) return;
  try {
    await api().deleteFees([fee.feeId]);
    ElMessage.success("费目已删除");
    await loadSceneContext();
  } catch (error) {
    ElMessage.error(errorMessage(error, "费目删除失败"));
  }
}

async function disableFee(fee: CostLiteRecord): Promise<void> {
  const confirmed = await confirmAction(
    `确认停用费目“${fee.feeName || fee.feeCode}”？停用后将不再参与新的计费。`,
    "停用费目",
    { confirmButtonText: "确认停用", cancelButtonText: "取消" },
  );
  if (!confirmed) return;
  try {
    if (api().disableFees) await api().disableFees!([fee.feeId]);
    else await api().updateFee({ ...fee, status: "1" });
    ElMessage.success("费目已停用");
    await loadSceneContext(fee.feeId);
  } catch (error) {
    ElMessage.error(errorMessage(error, "费目停用失败"));
  }
}

const variableDialogVisible = ref(false);
const variableFormRef = ref<FormInstance>();
const variableForm = reactive<CostLiteRecord>({});
const variableRules: FormRules = {
  variableCode: [{ required: true, message: "请输入要素编码", trigger: "blur" }],
  variableName: [{ required: true, message: "请输入要素名称", trigger: "blur" }],
  variableType: [{ required: true, message: "请选择要素类型", trigger: "change" }],
  sourceType: [{ required: true, message: "请选择来源类型", trigger: "change" }],
};

async function openVariableDialog(variable?: CostLiteRecord): Promise<void> {
  if (!selectedSceneId.value) return;
  resetObject(variableForm, {
    variableId: variable?.variableId,
    sceneId: selectedSceneId.value,
    groupId: variable?.groupId,
    variableCode: variable?.variableCode || "",
    variableName: variable?.variableName || "",
    variableType: variable?.variableType || "NUMBER",
    sourceType: variable?.sourceType || "INPUT",
    sourceSystem: variable?.sourceSystem || "",
    dictType: variable?.dictType || "",
    dataPath: variable?.dataPath || "",
    remoteApi: variable?.remoteApi || "",
    requestMethod: variable?.requestMethod || "GET",
    contentType: variable?.contentType || "application/json",
    queryConfigJson: variable?.queryConfigJson || "",
    requestHeadersJson: variable?.requestHeadersJson || "",
    bodyTemplateJson: variable?.bodyTemplateJson || "",
    authType: variable?.authType || "NONE",
    authConfigJson: variable?.authConfigJson || "",
    responseConfigJson: variable?.responseConfigJson || "",
    mappingConfigJson: variable?.mappingConfigJson || "",
    pageConfigJson: variable?.pageConfigJson || "",
    adapterType: variable?.adapterType || "STANDARD",
    adapterConfigJson: variable?.adapterConfigJson || "",
    syncMode: variable?.syncMode || "REALTIME",
    cachePolicy: variable?.cachePolicy || "MANUAL_REFRESH",
    fallbackPolicy: variable?.fallbackPolicy || "FAIL_FAST",
    dataType: variable?.dataType || "NUMBER",
    defaultValue: variable?.defaultValue || "",
    precisionScale: Number(variable?.precisionScale ?? 2),
    formulaCode: variable?.formulaCode || "",
    formulaExpr: variable?.formulaExpr || "",
    sortNo: Number(variable?.sortNo ?? 10),
    status: variable?.status ?? "0",
    remark: variable?.remark || "",
  });
  variableDialogVisible.value = true;
  if (!variable?.variableId) return;
  loading.detail = true;
  try {
    const detail = await api().getVariable(variable.variableId);
    resetObject(variableForm, { ...variableForm, ...detail, sceneId: selectedSceneId.value });
  } catch (error) {
    ElMessage.error(errorMessage(error, "要素详情加载失败"));
  } finally {
    loading.detail = false;
  }
}

async function saveVariable(): Promise<void> {
  if (!(await variableFormRef.value?.validate().catch(() => false))) return;
  loading.saving = true;
  try {
    if (variableForm.variableId) await api().updateVariable({ ...variableForm });
    else await api().createVariable({ ...variableForm });
    ElMessage.success("要素已保存");
    variableDialogVisible.value = false;
    await loadSceneContext();
  } catch (error) {
    ElMessage.error(errorMessage(error, "要素保存失败"));
  } finally {
    loading.saving = false;
  }
}

async function deleteVariable(variable: CostLiteRecord): Promise<void> {
  const confirmed = await confirmGovernedDelete(
    `要素“${variable.variableName || variable.variableCode}”`,
    api().getVariableGovernance
      ? () => api().getVariableGovernance!(variable.variableId)
      : undefined,
  );
  if (!confirmed) return;
  try {
    await api().deleteVariables([variable.variableId]);
    ElMessage.success("要素已删除");
    await loadSceneContext();
  } catch (error) {
    ElMessage.error(errorMessage(error, "要素删除失败"));
  }
}

const groupDialogVisible = ref(false);
const groupEditorVisible = ref(false);
const groupFormRef = ref<FormInstance>();
const groupForm = reactive<CostLiteRecord>({});
const groupRules: FormRules = {
  groupCode: [{ required: true, message: "请输入分组编码", trigger: "blur" }],
  groupName: [{ required: true, message: "请输入分组名称", trigger: "blur" }],
};

function openGroupEditor(group?: CostLiteRecord): void {
  resetObject(groupForm, {
    groupId: group?.groupId,
    sceneId: selectedSceneId.value,
    groupCode: group?.groupCode || "",
    groupName: group?.groupName || "",
    sortNo: Number(group?.sortNo ?? 10),
    status: group?.status ?? "0",
    remark: group?.remark || "",
  });
  groupEditorVisible.value = true;
}

async function saveVariableGroup(): Promise<void> {
  if (!(await groupFormRef.value?.validate().catch(() => false))) return;
  loading.saving = true;
  try {
    if (groupForm.groupId) await api().updateVariableGroup({ ...groupForm });
    else await api().createVariableGroup({ ...groupForm });
    ElMessage.success("要素分组已保存");
    groupEditorVisible.value = false;
    variableGroups.value = await api().listVariableGroups(selectedSceneId.value!);
  } catch (error) {
    ElMessage.error(errorMessage(error, "要素分组保存失败"));
  } finally {
    loading.saving = false;
  }
}

async function deleteVariableGroup(group: CostLiteRecord): Promise<void> {
  const confirmed = await confirmAction(
    `确认删除分组“${group.groupName || group.groupCode}”？分组内仍有要素时，服务端会阻止删除。`,
    "删除分组",
    { confirmButtonText: "确认删除", cancelButtonText: "取消" },
  );
  if (!confirmed) return;
  try {
    await api().deleteVariableGroups([group.groupId]);
    variableGroups.value = await api().listVariableGroups(selectedSceneId.value!);
    ElMessage.success("要素分组已删除");
  } catch (error) {
    ElMessage.error(errorMessage(error, "要素分组删除失败"));
  }
}

const ruleDialogVisible = ref(false);
const ruleFormRef = ref<FormInstance>();
const ruleForm = reactive<CostLiteRecord>({});
const ruleRules: FormRules = {
  ruleCode: [{ required: true, message: "请输入规则编码", trigger: "blur" }],
  ruleName: [{ required: true, message: "请输入规则名称", trigger: "blur" }],
  ruleType: [{ required: true, message: "请选择规则类型", trigger: "change" }],
};

const conditionGroups = computed(() => {
  const groups = new Map<number, CostLiteRecord[]>();
  (ruleForm.conditions || []).forEach((condition: CostLiteRecord, conditionIndex: number) => {
    const groupNo = Math.max(1, Number(condition.groupNo || 1));
    const items = groups.get(groupNo) || [];
    items.push({ condition, conditionIndex });
    groups.set(groupNo, items);
  });
  return Array.from(groups.entries())
    .sort(([left], [right]) => left - right)
    .map(([groupNo, items]) => ({
      groupNo,
      items: items.sort((left, right) => Number(left.condition.sortNo || 0) - Number(right.condition.sortNo || 0)),
    }));
});

const groupPricingRows = computed(() => {
  const prices = Array.isArray(ruleForm.pricingConfig?.groupPrices)
    ? ruleForm.pricingConfig.groupPrices
    : [];
  const priceMap = new Map(prices.map((item: CostLiteRecord) => [Number(item.groupNo), item]));
  const valueKey = ruleForm.ruleType === "FIXED_RATE" ? "rateValue" : "amountValue";
  return conditionGroups.value.map((group) => ({
    groupNo: group.groupNo,
    conditionCount: group.items.length,
    value: priceMap.get(group.groupNo)?.[valueKey],
  }));
});

function newCondition(groupNo = 1): CostLiteRecord {
  const groupItems = (ruleForm.conditions || []).filter(
    (item: CostLiteRecord) => Number(item.groupNo || 1) === Number(groupNo),
  );
  const sortNo = groupItems.length
    ? Math.max(...groupItems.map((item: CostLiteRecord) => Number(item.sortNo || 0))) + 1
    : 1;
  return {
    groupNo: Number(groupNo),
    sortNo,
    variableCode: "",
    displayName: "",
    operatorCode: "EQ",
    compareValue: "",
    status: "0",
  };
}

function newTier(index: number): CostLiteRecord {
  return {
    tierNo: index + 1,
    startValue: undefined,
    endValue: undefined,
    rateValue: undefined,
    intervalMode: "LEFT_CLOSED_RIGHT_OPEN",
    status: "0",
  };
}

async function openRuleDialog(rule?: CostLiteRecord): Promise<void> {
  if (!selectedSceneId.value || !selectedFeeId.value) return;
  loading.detail = true;
  try {
    const detail = rule?.ruleId ? await api().getRule(rule.ruleId) : {};
    const source = Object.keys(detail).length ? detail : rule || {};
    resetObject(ruleForm, {
      ruleId: source.ruleId,
      sceneId: selectedSceneId.value,
      feeId: selectedFeeId.value,
      ruleCode: source.ruleCode || "",
      ruleName: source.ruleName || "",
      ruleType: source.ruleType || "FIXED_RATE",
      conditionLogic: source.pricingMode === "GROUPED" ? "OR" : (source.conditionLogic || "AND"),
      priority: Number(source.priority ?? 100),
      quantityVariableCode: source.quantityVariableCode || "",
      pricingMode: source.pricingMode || "TYPED",
      pricingConfig: {
        ...(source.pricingConfig || {}),
        rateValue: source.pricingConfig?.rateValue ?? source.pricingConfig?.unitPrice,
        amountValue: source.pricingConfig?.amountValue ?? source.pricingConfig?.amount,
      },
      amountFormulaCode: source.amountFormulaCode || "",
      amountFormula: source.amountFormula || "",
      noteTemplate: source.noteTemplate || "",
      status: source.status ?? "0",
      sortNo: Number(source.sortNo ?? 10),
      remark: source.remark || "",
      conditions: Array.isArray(source.conditions)
        ? source.conditions.map((item: CostLiteRecord, index: number) => ({
          ...item,
          groupNo: Math.max(1, Number(item.groupNo || 1)),
          sortNo: Number(item.sortNo ?? index + 1),
          status: item.status ?? "0",
        }))
        : [],
      tiers: Array.isArray(source.tiers)
        ? source.tiers.map((item: CostLiteRecord, index: number) => ({
          ...item,
          tierNo: Number(item.tierNo ?? index + 1),
          status: item.status ?? "0",
        }))
        : [],
    });
    ruleDialogVisible.value = true;
    if (ruleForm.pricingMode === "GROUPED") syncGroupedPricingConfig();
  } catch (error) {
    ElMessage.error(errorMessage(error, "规则详情加载失败"));
  } finally {
    loading.detail = false;
  }
}

function variableName(code: unknown): string {
  return variables.value.find((item) => item.variableCode === code)?.variableName || String(code || "");
}

function addCondition(targetGroupNo?: number): void {
  const groupNo = targetGroupNo ?? (conditionGroups.value.length
    ? conditionGroups.value[conditionGroups.value.length - 1].groupNo
    : 1);
  ruleForm.conditions.push(newCondition(groupNo));
  syncGroupedPricingConfig();
}

function addConditionGroup(): void {
  const groupNo = conditionGroups.value.length
    ? Math.max(...conditionGroups.value.map((group) => Number(group.groupNo))) + 1
    : 1;
  ruleForm.conditions.push(newCondition(groupNo));
  syncGroupedPricingConfig();
}

function removeConditionFromGroup(groupNo: number, index: number): void {
  const group = conditionGroups.value.find((item) => item.groupNo === Number(groupNo));
  const target = group?.items[index];
  if (target?.conditionIndex == null) return;
  ruleForm.conditions.splice(target.conditionIndex, 1);
  syncGroupedPricingConfig();
}

async function removeConditionGroup(groupNo: number): Promise<void> {
  const group = conditionGroups.value.find((item) => item.groupNo === Number(groupNo));
  if (!group) return;
  const confirmed = await confirmAction(
    `确认删除组合组 ${groupNo} 及其中的 ${group.items.length} 个条件？`,
    "删除条件组合组",
    { confirmButtonText: "确认删除", cancelButtonText: "取消" },
  );
  if (!confirmed) return;
  ruleForm.conditions = (ruleForm.conditions || []).filter(
    (item: CostLiteRecord) => Number(item.groupNo || 1) !== Number(groupNo),
  );
  syncGroupedPricingConfig();
}

function addTier(): void {
  ruleForm.tiers.push(newTier(ruleForm.tiers.length));
}

function removeTier(index: number): void {
  ruleForm.tiers.splice(index, 1);
  ruleForm.tiers.forEach((item: CostLiteRecord, itemIndex: number) => (item.tierNo = itemIndex + 1));
}

function buildRulePayload(): CostLiteRecord {
  const payload = JSON.parse(JSON.stringify(ruleForm));
  payload.pricingMode = payload.pricingMode || "TYPED";
  if (payload.pricingMode === "GROUPED") payload.conditionLogic = "OR";
  payload.conditions = (payload.conditions || [])
    .map((item: CostLiteRecord, index: number) => ({
      ...item,
      sceneId: selectedSceneId.value,
      groupNo: Math.max(1, Number(item.groupNo || 1)),
      sortNo: Number(item.sortNo || index + 1),
      displayName: item.displayName || variableName(item.variableCode),
      compareValue: item.compareValue == null ? "" : String(item.compareValue),
      status: item.status || "0",
    }));
  const groupSortCounters = new Map<number, number>();
  payload.conditions.forEach((item: CostLiteRecord) => {
    const next = (groupSortCounters.get(item.groupNo) || 0) + 1;
    groupSortCounters.set(item.groupNo, next);
    item.sortNo = next;
  });
  payload.tiers = (payload.tiers || []).map((item: CostLiteRecord, index: number) => ({
    ...item,
    sceneId: selectedSceneId.value,
    tierNo: index + 1,
    status: item.status || "0",
  }));
  if (payload.pricingMode === "GROUPED") {
    const valueKey = payload.ruleType === "FIXED_RATE" ? "rateValue" : "amountValue";
    payload.pricingConfig = {
      ...(payload.pricingConfig || {}),
      groupPrices: (payload.pricingConfig?.groupPrices || []).map((item: CostLiteRecord) => ({
        groupNo: Number(item.groupNo),
        [valueKey]: item[valueKey],
      })),
    };
  }
  return payload;
}

function syncGroupedPricingConfig(): void {
  if (!ruleForm.pricingConfig) ruleForm.pricingConfig = {};
  if (!['FIXED_RATE', 'FIXED_AMOUNT'].includes(ruleForm.ruleType) || ruleForm.pricingMode !== 'GROUPED') {
    return;
  }
  const valueKey = ruleForm.ruleType === "FIXED_RATE" ? "rateValue" : "amountValue";
  const existing = Array.isArray(ruleForm.pricingConfig.groupPrices)
    ? ruleForm.pricingConfig.groupPrices
    : [];
  const existingMap = new Map(existing.map((item: CostLiteRecord) => [Number(item.groupNo), item]));
  ruleForm.pricingConfig.groupPrices = conditionGroups.value.map((group) => ({
    groupNo: Number(group.groupNo),
    [valueKey]: existingMap.get(Number(group.groupNo))?.[valueKey],
  }));
}

function setGroupPricingValue(groupNo: number, value: unknown): void {
  syncGroupedPricingConfig();
  const valueKey = ruleForm.ruleType === "FIXED_RATE" ? "rateValue" : "amountValue";
  const item = (ruleForm.pricingConfig.groupPrices || []).find(
    (candidate: CostLiteRecord) => Number(candidate.groupNo) === Number(groupNo),
  );
  if (item) item[valueKey] = value;
}

function validateRuleEditor(payload: CostLiteRecord): boolean {
  const conditions = payload.conditions || [];
  if (conditions.some((item: CostLiteRecord) => !item.variableCode)) {
    ElMessage.warning("每条条件都必须选择变量；如果不需要条件，请删除空白条件行");
    return false;
  }
  for (const [index, condition] of conditions.entries()) {
    const operator = String(condition.operatorCode || "").toUpperCase();
    if (!operator) {
      ElMessage.warning(`第 ${index + 1} 条条件请选择操作符`);
      return false;
    }
    if (!["IS_NULL", "IS_NOT_NULL"].includes(operator) && !String(condition.compareValue || "").trim()) {
      ElMessage.warning(`第 ${index + 1} 条条件请填写比较值`);
      return false;
    }
    if (["BETWEEN"].includes(operator) && String(condition.compareValue).split(",").filter(Boolean).length < 2) {
      ElMessage.warning(`第 ${index + 1} 条区间条件请按“起始值,截止值”填写`);
      return false;
    }
  }
  if (payload.ruleType === "FIXED_RATE" && payload.pricingMode !== "GROUPED" && payload.pricingConfig?.rateValue == null) {
    ElMessage.warning("固定费率规则必须填写费率");
    return false;
  }
  if (payload.ruleType === "FIXED_AMOUNT" && payload.pricingMode !== "GROUPED" && payload.pricingConfig?.amountValue == null) {
    ElMessage.warning("固定金额规则必须填写金额");
    return false;
  }
  if (payload.pricingMode === "GROUPED") {
    if (!conditions.length) {
      ElMessage.warning("组合定价至少需要配置一个条件组合组");
      return false;
    }
    const valueKey = payload.ruleType === "FIXED_RATE" ? "rateValue" : "amountValue";
    const priceMap = new Map((payload.pricingConfig?.groupPrices || []).map((item: CostLiteRecord) => [Number(item.groupNo), item]));
    for (const group of conditionGroups.value) {
      if (priceMap.get(group.groupNo)?.[valueKey] == null) {
        ElMessage.warning(`组合组 ${group.groupNo} 未填写${valueKey === "rateValue" ? "费率" : "固定金额"}`);
        return false;
      }
    }
  }
  if (payload.ruleType === "TIER_RATE") {
    if (!payload.tiers?.length) {
      ElMessage.warning("阶梯规则至少需要配置一档阶梯");
      return false;
    }
    for (const [index, tier] of payload.tiers.entries()) {
      if (tier.rateValue == null) {
        ElMessage.warning(`第 ${index + 1} 档阶梯请填写费率`);
        return false;
      }
      if (tier.startValue != null && tier.endValue != null && Number(tier.startValue) >= Number(tier.endValue)) {
        ElMessage.warning(`第 ${index + 1} 档阶梯起始值必须小于截止值`);
        return false;
      }
      const previous = payload.tiers[index - 1];
      if (previous?.endValue != null && tier.startValue != null && Number(previous.endValue) !== Number(tier.startValue)) {
        ElMessage.warning(`第 ${index + 1} 档阶梯与上一档不连续，请检查区间`);
        return false;
      }
    }
  }
  if (payload.ruleType === "FORMULA" && !payload.amountFormulaCode && !String(payload.amountFormula || "").trim()) {
    ElMessage.warning("公式规则请选择公式编码或填写手写公式");
    return false;
  }
  return true;
}

async function saveRule(): Promise<void> {
  if (!(await ruleFormRef.value?.validate().catch(() => false))) return;
  if (["FIXED_RATE", "TIER_RATE"].includes(ruleForm.ruleType) && !ruleForm.quantityVariableCode) {
    ElMessage.warning("请选择计量要素");
    return;
  }
  const payload = buildRulePayload();
  if (!validateRuleEditor(payload)) return;
  loading.saving = true;
  try {
    const warnings = await api().previewRuleConflict(payload);
    if (warnings.length) {
      const confirmed = await confirmAction(
        warnings.slice(0, 3).map((item) => item.message || item.summary || JSON.stringify(item)).join("\n"),
        "发现规则冲突",
        { confirmButtonText: "仍然保存", cancelButtonText: "取消保存" },
      );
      if (!confirmed) return;
    }
    if (payload.ruleId) await api().updateRule(payload);
    else await api().createRule(payload);
    ElMessage.success("费率规则已保存");
    ruleDialogVisible.value = false;
    await loadRules();
  } catch (error) {
    ElMessage.error(errorMessage(error, "费率规则保存失败"));
  } finally {
    loading.saving = false;
  }
}

async function previewRule(): Promise<void> {
  const rule = buildRulePayload();
  if (!validateRuleEditor(rule)) return;
  loading.detail = true;
  try {
    const result = await api().previewRule({ rule, inputValues: {} });
    openJsonDetail("规则预览", result);
  } catch (error) {
    ElMessage.error(errorMessage(error, "规则预览失败"));
  } finally {
    loading.detail = false;
  }
}

async function deleteRule(rule: CostLiteRecord): Promise<void> {
  const confirmed = await confirmGovernedDelete(
    `规则“${rule.ruleName || rule.ruleCode}”`,
    api().getRuleGovernance
      ? () => api().getRuleGovernance!(rule.ruleId)
      : undefined,
  );
  if (!confirmed) return;
  try {
    await api().deleteRules([rule.ruleId]);
    ElMessage.success("规则已删除");
    await loadRules();
  } catch (error) {
    ElMessage.error(errorMessage(error, "规则删除失败"));
  }
}

const publishDialogVisible = ref(false);
const publishFormRef = ref<FormInstance>();
const publishForm = reactive<CostLiteRecord>({ publishDesc: "", activateNow: true });
const publishRules: FormRules = {
  publishDesc: [{ required: true, message: "请输入发布说明", trigger: "blur" }],
};

async function runPublishPrecheck(): Promise<void> {
  if (!selectedSceneId.value) return;
  loading.precheck = true;
  try {
    const result = await api().precheckVersion(selectedSceneId.value);
    precheckResult.value = result;
    openJsonDetail("发布检查", result);
    if (result.publishable === false) {
      ElMessage.warning(`发布检查存在 ${result.blockingCount ?? ""} 项阻断项，请处理后再发布`);
    } else {
      ElMessage.success("发布检查通过");
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, "发布检查失败"));
  } finally {
    loading.precheck = false;
  }
}

function openPublishDialog(): void {
  publishForm.publishDesc = "";
  publishForm.activateNow = true;
  publishDialogVisible.value = true;
}

async function publishVersion(): Promise<void> {
  if (!(await publishFormRef.value?.validate().catch(() => false)) || !selectedSceneId.value) return;
  loading.publishing = true;
  try {
    const precheck = await api().precheckVersion(selectedSceneId.value);
    precheckResult.value = precheck;
    if (precheck.publishable === false) {
      openJsonDetail("发布检查", precheck);
      ElMessage.warning("发布检查存在阻断项，当前版本未发布");
      return;
    }
    await api().createVersion({
      sceneId: selectedSceneId.value,
      publishDesc: publishForm.publishDesc,
      activateNow: publishForm.activateNow,
    });
    ElMessage.success(publishForm.activateNow ? "版本已发布并生效" : "版本已发布");
    publishDialogVisible.value = false;
    await loadScenes(selectedSceneId.value);
    await loadSceneContext();
  } catch (error) {
    ElMessage.error(errorMessage(error, "版本发布失败"));
  } finally {
    loading.publishing = false;
  }
}

async function activateVersion(version: CostLiteRecord): Promise<void> {
  const confirmed = await confirmAction(
    `确认将版本 ${version.versionNo || version.versionId} 设为生效？`,
    "版本生效",
    { confirmButtonText: "确认生效", cancelButtonText: "取消" },
  );
  if (!confirmed) return;
  try {
    await api().activateVersion(version.versionId);
    ElMessage.success("版本已生效");
    await loadScenes(selectedSceneId.value);
    await loadSceneContext();
  } catch (error) {
    ElMessage.error(errorMessage(error, "版本生效失败"));
  }
}

async function rollbackVersion(version: CostLiteRecord): Promise<void> {
  if (!api().rollbackVersion) return;
  const confirmed = await confirmAction(
    `确认回退版本 ${version.versionNo || version.versionId}？回退后该版本不再作为可生效版本。`,
    "版本回退",
    { confirmButtonText: "确认回退", cancelButtonText: "取消" },
  );
  if (!confirmed) return;
  try {
    await api().rollbackVersion!(version.versionId);
    ElMessage.success("版本已回退");
    await loadScenes(selectedSceneId.value);
    await loadSceneContext();
  } catch (error) {
    ElMessage.error(errorMessage(error, "版本回退失败"));
  }
}

const simulationForm = reactive<CostLiteRecord>({
  versionId: undefined,
  billMonth: new Date().toISOString().slice(0, 7),
  includeExplain: true,
  inputJson: "{\n  \"bizNo\": \"DEMO-001\"\n}",
});
const simulationResult = ref<CostLiteRecord>({});

async function fillInputTemplate(): Promise<void> {
  if (!selectedSceneId.value) return;
  loading.template = true;
  try {
    const template = await api().getInputTemplate(
      selectedSceneId.value,
      selectedFeeId.value ? [selectedFeeId.value] : undefined,
    );
    const value = template.inputJson || template.sampleInputJson || template.template || template;
    simulationForm.inputJson = typeof value === "string" ? value : JSON.stringify(value, null, 2);
    ElMessage.success("输入模板已生成");
  } catch (error) {
    ElMessage.error(errorMessage(error, "输入模板生成失败"));
  } finally {
    loading.template = false;
  }
}

function validateInputJson(): unknown {
  try {
    return JSON.parse(simulationForm.inputJson);
  } catch {
    throw new Error("输入数据不是合法 JSON");
  }
}

async function runCalculate(mode: "calculate" | "simulation" | "task"): Promise<void> {
  if (!selectedSceneId.value) return;
  let parsed: unknown;
  try {
    parsed = validateInputJson();
  } catch (error) {
    ElMessage.error((error as Error).message);
    return;
  }
  loading.running = true;
  try {
    if (mode === "calculate") {
      simulationResult.value = await api().calculate({
        sceneId: selectedSceneId.value,
        versionId: simulationForm.versionId || activeVersionId.value,
        snapshotMode: simulationForm.versionId || activeVersionId.value ? "ACTIVE" : "DRAFT",
        feeIds: selectedFeeId.value ? [selectedFeeId.value] : [],
        billMonth: simulationForm.billMonth,
        inputJson: JSON.stringify(parsed),
        includeExplain: simulationForm.includeExplain,
      });
      ElMessage.success("同步计费完成，调用日志已留存");
    } else if (mode === "simulation") {
      simulationResult.value = await api().executeSimulation({
        sceneId: selectedSceneId.value,
        versionId: simulationForm.versionId || activeVersionId.value,
        billMonth: simulationForm.billMonth,
        inputJson: JSON.stringify(parsed),
      });
      ElMessage.success("试算完成");
    } else {
      if (!simulationForm.billMonth) throw new Error("正式任务必须选择账期");
      const taskPayload = {
        sceneId: selectedSceneId.value,
        versionId: simulationForm.versionId || activeVersionId.value,
        taskType: Array.isArray(parsed) ? "FORMAL_BATCH" : "FORMAL_SINGLE",
        billMonth: simulationForm.billMonth,
        requestNo: `COST-LITE-${Date.now()}`,
        inputSourceType: "INLINE_JSON",
        inputJson: JSON.stringify(parsed),
        remark: "Cost Lite 工作台提交",
      };
      if (api().precheckTask) {
        const precheck = await api().precheckTask!(taskPayload);
        if (precheck.passed === false) {
          openJsonDetail("正式任务检查", precheck);
          ElMessage.warning(precheck.message || "正式任务检查存在阻断项");
          return;
        }
      }
      simulationResult.value = await api().submitTask(taskPayload);
      ElMessage.success("正式任务已提交");
    }
    await Promise.all([loadBillingLogs(), loadResults()]);
  } catch (error) {
    ElMessage.error(errorMessage(error, "计费执行失败"));
  } finally {
    loading.running = false;
  }
}

const logQuery = reactive({ pageNum: 1, pageSize: 20, status: "" });
const billingLogs = ref<CostLiteRecord[]>([]);
const billingLogTotal = ref(0);

async function loadBillingLogs(): Promise<void> {
  loading.logs = true;
  try {
    const page = await api().listBillingLogs({
      ...logQuery,
      sceneId: selectedSceneId.value,
    });
    billingLogs.value = page.rows;
    billingLogTotal.value = page.total;
  } catch (error) {
    ElMessage.error(errorMessage(error, "调用日志加载失败"));
  } finally {
    loading.logs = false;
  }
}

const resultQuery = reactive({
  pageNum: 1,
  pageSize: 20,
  billMonth: simulationForm.billMonth,
});
const results = ref<CostLiteRecord[]>([]);
const resultTotal = ref(0);

async function loadResults(): Promise<void> {
  loading.results = true;
  try {
    const billMonth = resultQuery.billMonth || simulationForm.billMonth;
    if (!billMonth) {
      results.value = [];
      resultTotal.value = 0;
      return;
    }
    resultQuery.billMonth = billMonth;
    const page = await api().listResults({
      ...resultQuery,
      sceneId: selectedSceneId.value,
      billMonth,
    });
    results.value = page.rows;
    resultTotal.value = page.total;
  } catch (error) {
    ElMessage.error(errorMessage(error, "正式结果加载失败"));
  } finally {
    loading.results = false;
  }
}

watch(bottomTab, (tab) => {
  if (tab === "logs") void loadBillingLogs();
  if (tab === "results") void loadResults();
});

async function showBillingLog(row: CostLiteRecord): Promise<void> {
  loading.detail = true;
  try {
    openJsonDetail("调用日志", await api().getBillingLog(row.simulationId));
  } catch (error) {
    ElMessage.error(errorMessage(error, "调用日志详情加载失败"));
  } finally {
    loading.detail = false;
  }
}

async function showResult(row: CostLiteRecord): Promise<void> {
  loading.detail = true;
  try {
    const detail = await api().getResult(row.resultId);
    if (detail.traceId) {
      detail.trace = await api().getTrace(detail.traceId);
    }
    openJsonDetail("计费结果", detail);
  } catch (error) {
    ElMessage.error(errorMessage(error, "计费结果详情加载失败"));
  } finally {
    loading.detail = false;
  }
}

onMounted(initialize);
</script>

<template>
  <div class="cost-lite-workbench" v-loading="loading.bootstrap">
    <header class="workbench-header">
      <div class="header-main">
        <div>
          <h1>轻量计费工作台</h1>
          <div class="header-meta">
            <span class="connection-state" :class="{ online: connected }">
              <span class="state-dot" />
              {{ connected ? "服务已连接" : "服务未连接" }}
            </span>
            <span v-if="selectedScene">{{ selectedScene.sceneCode }} · {{ selectedScene.sceneName }}</span>
            <span v-if="activeVersion">生效版本 {{ activeVersion.versionNo || activeVersion.versionId }}</span>
          </div>
        </div>
        <div class="header-actions">
          <el-button :icon="Refresh" circle title="刷新" @click="initialize" />
          <el-button :icon="DocumentChecked" :disabled="!selectedSceneId" @click="runPublishPrecheck">
            发布检查
          </el-button>
          <el-button type="primary" :icon="Promotion" :disabled="!selectedSceneId" @click="openPublishDialog">
            发布版本
          </el-button>
        </div>
      </div>
      <div v-if="selectedScene" class="version-strip">
        <span class="strip-label">版本</span>
        <div
          v-for="version in versions.slice(0, 8)"
          :key="version.versionId"
          class="version-item"
          :class="{ active: String(version.versionId) === String(activeVersionId) }"
          role="button"
          tabindex="0"
          @click="simulationForm.versionId = version.versionId"
          @keydown.enter="simulationForm.versionId = version.versionId"
        >
          <strong>{{ version.versionNo || `#${version.versionId}` }}</strong>
          <small>{{ versionStatusText(version) }}</small>
          <span class="version-item-actions" @click.stop>
            <el-button
              v-if="String(version.versionId) !== String(activeVersionId) && version.versionStatus !== 'ROLLED_BACK'"
              link
              type="primary"
              size="small"
              @click="activateVersion(version)"
            >
              生效
            </el-button>
            <el-button
              v-if="rollbackSupported && version.versionStatus !== 'ROLLED_BACK'"
              link
              type="warning"
              size="small"
              @click="rollbackVersion(version)"
            >
              回退
            </el-button>
          </span>
        </div>
        <span v-if="!versions.length" class="empty-inline">暂无发布版本</span>
      </div>
    </header>

    <main class="configuration-grid">
      <section class="work-panel scene-panel">
        <div class="panel-header">
          <div class="panel-title">
            <Connection class="panel-icon" />
            <span>场景</span>
            <el-tag size="small" effect="plain">{{ scenes.length }}</el-tag>
          </div>
          <el-button type="primary" :icon="Plus" circle title="新增场景" @click="openSceneDialog()" />
        </div>
        <div class="panel-filter">
          <el-input v-model="sceneKeyword" :prefix-icon="Search" clearable placeholder="搜索场景" />
        </div>
        <div class="scene-list" v-loading="loading.scenes">
          <div
            v-for="scene in filteredScenes"
            :key="scene.sceneId"
            class="scene-row"
            :class="{ selected: String(scene.sceneId) === String(selectedSceneId) }"
            role="button"
            tabindex="0"
            @click="selectScene(scene)"
            @keydown.enter="selectScene(scene)"
          >
            <span class="scene-row-main">
              <strong>{{ scene.sceneName }}</strong>
              <small>{{ scene.sceneCode }}</small>
            </span>
            <span class="scene-row-side">
              <el-tag :type="statusTagType('cost_scene_status', scene.status)" size="small">{{ dictLabel('cost_scene_status', scene.status) }}</el-tag>
              <span class="row-actions" @click.stop>
                <el-button link :icon="Edit" title="编辑" @click="openSceneDialog(scene)" />
                <el-button link type="danger" :icon="Delete" title="删除" @click="deleteScene(scene)" />
              </span>
            </span>
          </div>
          <el-empty v-if="!filteredScenes.length && !loading.scenes" description="暂无场景" :image-size="64" />
        </div>
      </section>

      <section class="work-panel master-panel" v-loading="loading.context">
        <div class="panel-header tab-header">
          <el-tabs v-model="centerTab" class="master-tabs">
            <el-tab-pane name="fees">
              <template #label><Coin class="tab-icon" />费目</template>
            </el-tab-pane>
            <el-tab-pane name="variables">
              <template #label><Link class="tab-icon" />要素</template>
            </el-tab-pane>
          </el-tabs>
          <div class="panel-actions">
            <el-button
              v-if="centerTab === 'variables'"
              :icon="Setting"
              circle
              title="要素分组"
              :disabled="!selectedSceneId"
              @click="groupDialogVisible = true"
            />
            <el-button
              type="primary"
              :icon="Plus"
              circle
              :title="centerTab === 'fees' ? '新增费目' : '新增要素'"
              :disabled="!selectedSceneId"
              @click="centerTab === 'fees' ? openFeeDialog() : openVariableDialog()"
            />
          </div>
        </div>

        <div v-if="centerTab === 'fees'" class="master-list">
          <div
            v-for="fee in fees"
            :key="fee.feeId"
            class="master-row"
            :class="{ selected: String(fee.feeId) === String(selectedFeeId) }"
            role="button"
            tabindex="0"
            @click="selectFee(fee)"
            @keydown.enter="selectFee(fee)"
          >
            <span class="master-row-content">
              <span class="master-row-title">
                <strong>{{ fee.feeName }}</strong>
                <el-tag :type="statusTagType('cost_fee_status', fee.status)" size="small">{{ dictLabel('cost_fee_status', fee.status) }}</el-tag>
              </span>
              <small>{{ fee.feeCode }} · {{ fee.feeCategory || '未分类' }} · {{ fee.unitCode || '未设单位' }}</small>
              <span class="row-summary">{{ fee.factorSummary || fee.scopeDescription || '暂无影响因素摘要' }}</span>
            </span>
            <span class="row-actions" @click.stop>
              <el-button link :icon="Edit" title="编辑" @click="openFeeDialog(fee)" />
              <el-button v-if="String(fee.status) === '0'" link type="warning" :icon="Warning" title="停用" @click="disableFee(fee)" />
              <el-button link type="danger" :icon="Delete" title="删除" @click="deleteFee(fee)" />
            </span>
          </div>
          <el-empty v-if="!fees.length" description="当前场景暂无费目" :image-size="64" />
        </div>

        <div v-else class="variable-table-wrap">
          <el-table :data="variables" height="100%" size="small" border>
            <el-table-column prop="variableName" label="要素" min-width="150">
              <template #default="{ row }">
                <strong>{{ row.variableName }}</strong>
                <div class="cell-subtitle">{{ row.variableCode }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="groupName" label="分组" min-width="100" />
            <el-table-column label="来源" width="100">
              <template #default="{ row }">{{ variableSourceText(row.sourceType) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ variableDataTypeText(row.dataType) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="78">
              <template #default="{ row }">
                <el-tag :type="statusTagType('cost_variable_status', row.status)" size="small">{{ dictLabel('cost_variable_status', row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联" width="72" align="center">
              <template #default="{ row }">
                <el-tag v-if="linkedVariableCodes.includes(String(row.variableCode))" type="success" size="small">已用</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link :icon="Edit" title="编辑" @click="openVariableDialog(row)" />
                <el-button link type="danger" :icon="Delete" title="删除" @click="deleteVariable(row)" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="work-panel rule-panel" v-loading="loading.rules">
        <div class="panel-header">
          <div class="panel-title">
            <Tickets class="panel-icon" />
            <span>规则与费率</span>
            <el-tag size="small" effect="plain">{{ rules.length }}</el-tag>
          </div>
          <el-button type="primary" :icon="Plus" :disabled="!selectedFeeId" @click="openRuleDialog()">新增规则</el-button>
        </div>
        <div v-if="selectedFee" class="fee-context">
          <div>
            <strong>{{ selectedFee.feeName }}</strong>
            <span>{{ selectedFee.feeCode }}</span>
          </div>
          <div class="linked-tags">
            <span>关联要素</span>
            <el-tag v-for="code in linkedVariableCodes.slice(0, 6)" :key="code" size="small" effect="plain">
              {{ variableName(code) }}
            </el-tag>
            <small v-if="!linkedVariableCodes.length">未关联</small>
          </div>
        </div>
        <div class="rule-list">
          <article v-for="rule in rules" :key="rule.ruleId" class="rule-row">
            <div class="rule-row-main">
              <div class="rule-heading">
                <strong>{{ rule.ruleName || rule.ruleCode }}</strong>
                <el-tag size="small" type="primary" effect="plain">{{ ruleTypeText(rule.ruleType) }}</el-tag>
                <el-tag :type="statusTagType('cost_rule_status', rule.status)" size="small">{{ dictLabel('cost_rule_status', rule.status) }}</el-tag>
              </div>
              <div class="rule-meta">
                <span>{{ rule.ruleCode }}</span>
                <span>优先级 {{ rule.priority ?? 0 }}</span>
                <span v-if="rule.quantityVariableCode">计量：{{ variableName(rule.quantityVariableCode) }}</span>
              </div>
              <p>{{ rule.conditionSummary || '无附加条件' }}</p>
            </div>
            <div class="rule-row-actions">
              <el-button :icon="Edit" circle title="编辑规则" @click="openRuleDialog(rule)" />
              <el-button type="danger" plain :icon="Delete" circle title="删除规则" @click="deleteRule(rule)" />
            </div>
          </article>
          <el-empty v-if="!rules.length" :description="selectedFeeId ? '当前费目暂无规则' : '请先选择费目'" :image-size="64" />
        </div>
      </section>
    </main>

    <section class="runtime-band">
      <el-tabs v-model="bottomTab" class="runtime-tabs">
        <el-tab-pane name="simulation">
          <template #label><VideoPlay class="tab-icon" />联调试算</template>
          <div class="simulation-layout">
            <div class="simulation-form">
              <div class="simulation-toolbar">
                <el-select v-model="simulationForm.versionId" clearable placeholder="使用生效版本">
                  <el-option
                    v-for="version in versions"
                    :key="version.versionId"
                    :label="`${version.versionNo || version.versionId} · ${versionStatusText(version)}`"
                    :value="version.versionId"
                  />
                </el-select>
                <el-date-picker
                  v-model="simulationForm.billMonth"
                  type="month"
                  value-format="YYYY-MM"
                  format="YYYY-MM"
                  placeholder="账期"
                />
                <el-checkbox v-model="simulationForm.includeExplain">返回解释</el-checkbox>
                <el-button :loading="loading.template" @click="fillInputTemplate">生成模板</el-button>
              </div>
              <el-input
                v-model="simulationForm.inputJson"
                type="textarea"
                :autosize="{ minRows: 8, maxRows: 16 }"
                spellcheck="false"
              />
              <div class="simulation-actions">
                <el-button type="primary" :icon="VideoPlay" :loading="loading.running" @click="runCalculate('calculate')">
                  同步计费
                </el-button>
                <el-button :icon="Check" :loading="loading.running" @click="runCalculate('simulation')">保存试算</el-button>
                <el-button type="success" :icon="Promotion" :loading="loading.running" @click="runCalculate('task')">
                  提交正式任务
                </el-button>
              </div>
            </div>
            <div class="result-preview">
              <div class="preview-header">
                <strong>执行结果</strong>
                <el-button v-if="Object.keys(simulationResult).length" link :icon="View" @click="detailTitle = '执行结果'; detailJson = simulationResult; detailVisible = true">展开</el-button>
              </div>
              <pre>{{ Object.keys(simulationResult).length ? JSON.stringify(simulationResult, null, 2) : '等待执行' }}</pre>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="logs">
          <template #label><DocumentChecked class="tab-icon" />调用日志</template>
          <div class="runtime-table-toolbar">
            <el-select v-model="logQuery.status" clearable placeholder="全部状态" style="width: 140px" @change="logQuery.pageNum = 1; loadBillingLogs()">
              <el-option v-for="item in dictOptions('cost_simulation_status')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button :icon="Refresh" @click="loadBillingLogs">刷新</el-button>
          </div>
          <el-table :data="billingLogs" v-loading="loading.logs" size="small" border height="300">
            <el-table-column prop="simulationId" label="日志ID" width="90" />
            <el-table-column prop="sceneName" label="场景" min-width="140" />
            <el-table-column prop="feeName" label="费目" min-width="140" />
            <el-table-column prop="simulationStatus" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType('cost_simulation_status', row.simulationStatus || row.status)" size="small">
                  {{ dictLabel('cost_simulation_status', row.simulationStatus || row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
            <el-table-column prop="errorMessage" label="异常" min-width="180" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="72" fixed="right">
              <template #default="{ row }"><el-button link :icon="View" title="查看调用日志详情" @click="showBillingLog(row)" /></template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="logQuery.pageNum"
            v-model:page-size="logQuery.pageSize"
            :total="billingLogTotal"
            layout="total, prev, pager, next"
            @current-change="loadBillingLogs"
            @size-change="logQuery.pageNum = 1; loadBillingLogs()"
          />
        </el-tab-pane>

        <el-tab-pane name="results">
          <template #label><Coin class="tab-icon" />正式结果</template>
          <div class="runtime-table-toolbar">
            <span>正式任务写入结果台账与追溯；同步试算保存在调用日志。</span>
            <el-date-picker
              v-model="resultQuery.billMonth"
              type="month"
              value-format="YYYY-MM"
              format="YYYY-MM"
              :clearable="false"
              placeholder="账期"
              style="width: 140px"
              @change="resultQuery.pageNum = 1; loadResults()"
            />
            <el-button :icon="Refresh" @click="loadResults">刷新</el-button>
          </div>
          <el-table :data="results" v-loading="loading.results" size="small" border height="300">
            <el-table-column prop="resultId" label="结果ID" width="90" />
            <el-table-column prop="taskNo" label="任务号" min-width="150" />
            <el-table-column prop="bizNo" label="业务号" min-width="130" />
            <el-table-column prop="feeName" label="费目" min-width="140" />
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">{{ row.amountValue ?? row.amount ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="billMonth" label="账期" width="90" />
            <el-table-column prop="createTime" label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="72" fixed="right">
              <template #default="{ row }"><el-button link :icon="View" title="查看计费结果详情" @click="showResult(row)" /></template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="resultQuery.pageNum"
            v-model:page-size="resultQuery.pageSize"
            :total="resultTotal"
            layout="total, prev, pager, next"
            @current-change="loadResults"
            @size-change="resultQuery.pageNum = 1; loadResults()"
          />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="sceneDialogVisible" :title="sceneForm.sceneId ? '编辑场景' : '新增场景'" width="640px" destroy-on-close>
      <el-form ref="sceneFormRef" :model="sceneForm" :rules="sceneRules" label-width="104px">
        <div class="dialog-grid">
          <el-form-item label="场景编码" prop="sceneCode"><el-input v-model="sceneForm.sceneCode" /></el-form-item>
          <el-form-item label="场景名称" prop="sceneName"><el-input v-model="sceneForm.sceneName" /></el-form-item>
          <el-form-item label="业务域" prop="businessDomain">
            <el-select v-model="sceneForm.businessDomain" filterable clearable>
              <el-option v-for="item in dictOptions('cost_business_domain')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="适用组织"><el-input v-model="sceneForm.orgCode" /></el-form-item>
          <el-form-item label="场景类型" prop="sceneType">
            <el-select v-model="sceneForm.sceneType" filterable>
              <el-option v-for="item in dictOptions('cost_scene_type')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象维度"><el-input v-model="sceneForm.defaultObjectDimension" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="sceneForm.status">
              <el-option v-for="item in dictOptions('cost_scene_status')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明" class="span-2"><el-input v-model="sceneForm.remark" type="textarea" :rows="3" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="sceneDialogVisible = false">取消</el-button><el-button type="primary" :loading="loading.saving" @click="saveScene">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="feeDialogVisible" :title="feeForm.feeId ? '编辑费目' : '新增费目'" width="700px" destroy-on-close>
      <el-form ref="feeFormRef" :model="feeForm" :rules="feeRules" label-width="104px">
        <div class="dialog-grid">
          <el-form-item label="费目编码" prop="feeCode"><el-input v-model="feeForm.feeCode" /></el-form-item>
          <el-form-item label="费目名称" prop="feeName"><el-input v-model="feeForm.feeName" /></el-form-item>
          <el-form-item label="费用分类"><el-input v-model="feeForm.feeCategory" /></el-form-item>
          <el-form-item label="计价单位">
            <el-select v-model="feeForm.unitCode" filterable clearable>
              <el-option v-for="item in dictOptions('cost_unit_code')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象维度"><el-input v-model="feeForm.objectDimension" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="feeForm.sortNo" :min="0" /></el-form-item>
          <el-form-item label="影响因素" class="span-2"><el-input v-model="feeForm.factorSummary" /></el-form-item>
          <el-form-item label="适用范围" class="span-2"><el-input v-model="feeForm.scopeDescription" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="feeForm.status">
              <el-option v-for="item in dictOptions('cost_fee_status')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注" class="span-2"><el-input v-model="feeForm.remark" type="textarea" :rows="2" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="feeDialogVisible = false">取消</el-button><el-button type="primary" :loading="loading.saving" @click="saveFee">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="variableDialogVisible" :title="variableForm.variableId ? '编辑要素' : '新增要素'" width="900px" destroy-on-close>
      <el-form ref="variableFormRef" :model="variableForm" :rules="variableRules" label-width="104px">
        <div class="dialog-grid">
          <el-form-item label="要素编码" prop="variableCode"><el-input v-model="variableForm.variableCode" /></el-form-item>
          <el-form-item label="要素名称" prop="variableName"><el-input v-model="variableForm.variableName" /></el-form-item>
          <el-form-item label="要素分组">
            <el-select v-model="variableForm.groupId" clearable><el-option v-for="group in variableGroups" :key="group.groupId" :label="group.groupName" :value="group.groupId" /></el-select>
          </el-form-item>
          <el-form-item label="要素类型" prop="variableType">
            <el-select v-model="variableForm.variableType">
              <el-option v-for="item in dictOptions('cost_variable_type')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源类型" prop="sourceType">
            <el-select v-model="variableForm.sourceType">
              <el-option v-for="item in dictOptions('cost_variable_source_type')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据类型">
            <el-select v-model="variableForm.dataType">
              <el-option v-for="item in dictOptions('cost_variable_data_type')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源系统"><el-input v-model="variableForm.sourceSystem" /></el-form-item>
          <el-form-item label="取值路径"><el-input v-model="variableForm.dataPath" /></el-form-item>
          <template v-if="variableForm.sourceType === 'DICT' || variableForm.variableType === 'DICT'">
            <el-form-item label="字典类型"><el-input v-model="variableForm.dictType" placeholder="例如 cost_business_domain" /></el-form-item>
          </template>
          <template v-if="variableForm.sourceType === 'REMOTE'">
            <el-form-item label="远程接口" class="span-2"><el-input v-model="variableForm.remoteApi" /></el-form-item>
            <el-form-item label="请求方式">
              <el-select v-model="variableForm.requestMethod">
                <el-option label="GET" value="GET" /><el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" /><el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
            <el-form-item label="内容类型"><el-input v-model="variableForm.contentType" /></el-form-item>
            <el-form-item label="鉴权方式">
              <el-select v-model="variableForm.authType">
                <el-option v-for="item in dictOptions('cost_variable_auth_type')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="同步方式">
              <el-select v-model="variableForm.syncMode">
                <el-option v-for="item in dictOptions('cost_variable_sync_mode')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="缓存策略">
              <el-select v-model="variableForm.cachePolicy">
                <el-option v-for="item in dictOptions('cost_variable_cache_policy')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="失败兜底">
              <el-select v-model="variableForm.fallbackPolicy">
                <el-option v-for="item in dictOptions('cost_variable_fallback_policy')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="适配器类型">
              <el-select v-model="variableForm.adapterType">
                <el-option label="标准响应" value="STANDARD" /><el-option label="根数组" value="ROOT_ARRAY" />
                <el-option label="分页响应" value="PAGE_ENVELOPE" /><el-option label="单对象" value="SINGLE_OBJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="鉴权配置" class="span-2"><el-input v-model="variableForm.authConfigJson" type="textarea" :rows="2" placeholder="JSON；已配置密钥编辑时显示为掩码" /></el-form-item>
            <el-form-item label="查询参数" class="span-2"><el-input v-model="variableForm.queryConfigJson" type="textarea" :rows="2" placeholder="JSON" /></el-form-item>
            <el-form-item label="请求头配置" class="span-2"><el-input v-model="variableForm.requestHeadersJson" type="textarea" :rows="2" placeholder="JSON" /></el-form-item>
            <el-form-item label="请求体模板" class="span-2"><el-input v-model="variableForm.bodyTemplateJson" type="textarea" :rows="2" placeholder="JSON" /></el-form-item>
            <el-form-item label="响应提取" class="span-2"><el-input v-model="variableForm.responseConfigJson" type="textarea" :rows="2" placeholder="JSON" /></el-form-item>
            <el-form-item label="字段映射" class="span-2"><el-input v-model="variableForm.mappingConfigJson" type="textarea" :rows="2" placeholder="JSON" /></el-form-item>
            <el-form-item label="分页策略" class="span-2"><el-input v-model="variableForm.pageConfigJson" type="textarea" :rows="2" placeholder="JSON" /></el-form-item>
            <el-form-item label="适配器配置" class="span-2"><el-input v-model="variableForm.adapterConfigJson" type="textarea" :rows="2" placeholder="JSON" /></el-form-item>
          </template>
          <template v-if="variableForm.sourceType === 'FORMULA' || variableForm.variableType === 'FORMULA'">
            <el-form-item label="公式编码"><el-input v-model="variableForm.formulaCode" /></el-form-item>
            <el-form-item label="公式表达式" class="span-2"><el-input v-model="variableForm.formulaExpr" type="textarea" :rows="3" /></el-form-item>
          </template>
          <el-form-item label="默认值"><el-input v-model="variableForm.defaultValue" /></el-form-item>
          <el-form-item label="小数精度"><el-input-number v-model="variableForm.precisionScale" :min="0" :max="12" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="variableForm.sortNo" :min="0" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="variableForm.status">
              <el-option v-for="item in dictOptions('cost_variable_status')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注" class="span-2"><el-input v-model="variableForm.remark" type="textarea" :rows="2" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="variableDialogVisible = false">取消</el-button><el-button type="primary" :loading="loading.saving" @click="saveVariable">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="groupDialogVisible" title="要素分组" width="680px">
      <div class="dialog-toolbar"><el-button type="primary" :icon="Plus" @click="openGroupEditor()">新增分组</el-button></div>
      <el-table :data="variableGroups" border size="small" max-height="420">
        <el-table-column prop="groupCode" label="分组编码" />
        <el-table-column prop="groupName" label="分组名称" />
        <el-table-column prop="variableCount" label="要素数" width="80" />
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link :icon="Edit" title="编辑分组" @click="openGroupEditor(row)" /><el-button link type="danger" :icon="Delete" title="删除分组" @click="deleteVariableGroup(row)" /></template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="groupEditorVisible" :title="groupForm.groupId ? '编辑分组' : '新增分组'" width="520px" append-to-body>
      <el-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-width="96px">
        <el-form-item label="分组编码" prop="groupCode"><el-input v-model="groupForm.groupCode" /></el-form-item>
        <el-form-item label="分组名称" prop="groupName"><el-input v-model="groupForm.groupName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="groupForm.sortNo" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="groupForm.status">
            <el-option v-for="item in dictOptions('cost_variable_group_status')" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="groupEditorVisible = false">取消</el-button><el-button type="primary" :loading="loading.saving" @click="saveVariableGroup">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleDialogVisible" :title="ruleForm.ruleId ? '编辑费率规则' : '新增费率规则'" width="980px" destroy-on-close>
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="96px">
        <div class="dialog-grid rule-base-grid">
          <el-form-item label="规则编码" prop="ruleCode"><el-input v-model="ruleForm.ruleCode" /></el-form-item>
          <el-form-item label="规则名称" prop="ruleName"><el-input v-model="ruleForm.ruleName" /></el-form-item>
          <el-form-item label="规则类型" prop="ruleType">
            <el-select v-model="ruleForm.ruleType">
              <el-option v-for="item in dictOptions('cost_rule_type')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="0" /></el-form-item>
          <el-form-item label="定价模式">
            <el-select v-model="ruleForm.pricingMode" @change="syncGroupedPricingConfig">
              <el-option label="统一定价" value="TYPED" />
              <el-option label="按条件组定价" value="GROUPED" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="['FIXED_RATE','TIER_RATE'].includes(ruleForm.ruleType)" label="计量要素"><el-select v-model="ruleForm.quantityVariableCode" filterable><el-option v-for="item in variables" :key="item.variableCode" :label="`${item.variableName} (${item.variableCode})`" :value="item.variableCode" /></el-select></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FIXED_RATE' && ruleForm.pricingMode !== 'GROUPED'" label="费率"><el-input-number v-model="ruleForm.pricingConfig.rateValue" :precision="6" :min="0" /></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FIXED_AMOUNT' && ruleForm.pricingMode !== 'GROUPED'" label="固定金额"><el-input-number v-model="ruleForm.pricingConfig.amountValue" :precision="2" /></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FORMULA'" label="公式编码"><el-select v-model="ruleForm.amountFormulaCode" clearable filterable><el-option v-for="item in formulaOptions" :key="item.formulaCode" :label="`${item.formulaName || item.formulaCode} (${item.formulaCode})`" :value="item.formulaCode" /></el-select></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FORMULA'" label="手写公式" class="span-2"><el-input v-model="ruleForm.amountFormula" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="条件逻辑">
            <el-select v-model="ruleForm.conditionLogic" :disabled="ruleForm.pricingMode === 'GROUPED'">
              <el-option v-for="item in dictOptions('cost_rule_condition_logic')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="ruleForm.status">
              <el-option v-for="item in dictOptions('cost_rule_status')" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>

      <div class="editor-section condition-editor">
        <div class="editor-section-header">
          <div>
            <strong>条件组合</strong>
            <span class="condition-group-note">组内条件为“且”，组间逻辑：{{ dictLabel('cost_rule_condition_logic', ruleForm.pricingMode === 'GROUPED' ? 'OR' : ruleForm.conditionLogic) }}</span>
          </div>
          <div class="editor-actions">
            <el-button :icon="Plus" size="small" @click="addCondition">添加条件</el-button>
            <el-button type="primary" :icon="Plus" size="small" @click="addConditionGroup">添加条件组</el-button>
          </div>
        </div>
        <div v-if="conditionGroups.length" class="condition-groups">
          <section v-for="group in conditionGroups" :key="group.groupNo" class="condition-group">
            <div class="condition-group-header">
              <div>
                <strong>条件组 {{ group.groupNo }}</strong>
                <el-tag size="small" effect="plain">{{ group.items.length }} 条条件</el-tag>
              </div>
              <div class="editor-actions">
                <el-button link :icon="Plus" @click="addCondition(group.groupNo)">添加条件</el-button>
                <el-button link type="danger" :icon="Delete" @click="removeConditionGroup(group.groupNo)">删除条件组</el-button>
              </div>
            </div>
            <el-table :data="group.items" size="small" border>
              <el-table-column label="要素" min-width="230">
                <template #default="{ row }">
                  <el-select v-model="row.condition.variableCode" filterable>
                    <el-option v-for="item in variables" :key="item.variableCode" :label="`${item.variableName} (${item.variableCode})`" :value="item.variableCode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作符" width="160">
                <template #default="{ row }">
                  <el-select v-model="row.condition.operatorCode">
                    <el-option v-for="item in dictOptions('cost_rule_operator')" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="比较值" min-width="190">
                <template #default="{ row }">
                  <el-input v-model="row.condition.compareValue" :disabled="['IS_NULL','IS_NOT_NULL'].includes(row.condition.operatorCode)" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="64">
                <template #default="{ $index }"><el-button link type="danger" :icon="Delete" title="删除条件" @click="removeConditionFromGroup(group.groupNo, $index)" /></template>
              </el-table-column>
            </el-table>
          </section>
        </div>
        <el-empty v-else description="暂无条件组，点击“添加条件”开始配置" :image-size="54" />
      </div>

      <div v-if="['FIXED_RATE', 'FIXED_AMOUNT'].includes(ruleForm.ruleType) && ruleForm.pricingMode === 'GROUPED'" class="editor-section">
        <div class="editor-section-header"><strong>条件组定价</strong><span class="condition-group-note">每个条件组单独配置{{ ruleForm.ruleType === 'FIXED_RATE' ? '费率' : '金额' }}</span></div>
        <el-table :data="groupPricingRows" size="small" border class="group-pricing-table">
          <el-table-column prop="groupNo" label="条件组" width="100" />
          <el-table-column prop="conditionCount" label="条件数" width="100" />
          <el-table-column :label="ruleForm.ruleType === 'FIXED_RATE' ? '费率' : '固定金额'" min-width="220">
            <template #default="{ row }">
              <el-input-number
                :model-value="row.value"
                :precision="ruleForm.ruleType === 'FIXED_RATE' ? 6 : 2"
                :min="0"
                :controls="false"
                @update:model-value="setGroupPricingValue(row.groupNo, $event)"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="ruleForm.ruleType === 'TIER_RATE'" class="editor-section">
        <div class="editor-section-header"><strong>阶梯费率</strong><el-button :icon="Plus" size="small" @click="addTier">添加阶梯</el-button></div>
        <el-table :data="ruleForm.tiers" size="small" border>
          <el-table-column prop="tierNo" label="#" width="52" />
          <el-table-column label="起始值"><template #default="{ row }"><el-input-number v-model="row.startValue" :controls="false" /></template></el-table-column>
          <el-table-column label="截止值"><template #default="{ row }"><el-input-number v-model="row.endValue" :controls="false" /></template></el-table-column>
          <el-table-column label="费率"><template #default="{ row }"><el-input-number v-model="row.rateValue" :precision="6" :min="0" :controls="false" /></template></el-table-column>
          <el-table-column label="区间" width="190"><template #default="{ row }"><el-select v-model="row.intervalMode"><el-option v-for="item in dictOptions('cost_rule_interval_mode')" :key="item.value" :label="item.label" :value="item.value" /></el-select></template></el-table-column>
          <el-table-column label="操作" width="64"><template #default="{ $index }"><el-button link type="danger" :icon="Delete" title="删除阶梯" @click="removeTier($index)" /></template></el-table-column>
        </el-table>
      </div>
      <template #footer><el-button :icon="View" @click="previewRule">预览</el-button><el-button @click="ruleDialogVisible = false">取消</el-button><el-button type="primary" :loading="loading.saving" @click="saveRule">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="publishDialogVisible" title="发布版本" width="560px">
      <el-form ref="publishFormRef" :model="publishForm" :rules="publishRules" label-width="96px">
        <el-form-item label="场景"><el-input :model-value="selectedScene?.sceneName" disabled /></el-form-item>
        <el-form-item label="发布说明" prop="publishDesc"><el-input v-model="publishForm.publishDesc" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="立即生效"><el-switch v-model="publishForm.activateNow" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="publishDialogVisible = false">取消</el-button><el-button type="primary" :loading="loading.publishing" @click="publishVersion">确认发布</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="760px">
      <div class="json-detail"><Warning v-if="detailTitle === '发布检查'" class="detail-icon" /><pre>{{ JSON.stringify(detailJson, null, 2) }}</pre></div>
    </el-dialog>
  </div>
</template>

<style scoped>
.cost-lite-workbench {
  --line: #d8dee8;
  --line-strong: #b8c2d0;
  --surface: #ffffff;
  --surface-soft: #f5f7fa;
  --surface-active: #eef6ff;
  --text: #1f2937;
  --muted: #667085;
  --primary: #2563eb;
  width: 100%;
  min-width: 0;
  min-height: 760px;
  padding: 14px;
  color: var(--text);
  background: #edf1f5;
  box-sizing: border-box;
}

.workbench-header,
.work-panel,
.runtime-band {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}

.workbench-header {
  margin-bottom: 12px;
}

.header-main {
  min-height: 66px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.header-main h1 {
  margin: 0 0 7px;
  font-size: 20px;
  line-height: 1.2;
  letter-spacing: 0;
}

.header-meta,
.header-actions,
.panel-title,
.panel-actions,
.rule-heading,
.rule-meta,
.simulation-toolbar,
.simulation-actions,
.runtime-table-toolbar,
.linked-tags,
.editor-section-header,
.dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 9px;
}

.header-meta {
  flex-wrap: wrap;
  color: var(--muted);
  font-size: 12px;
}

.connection-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.state-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d92d20;
}

.connection-state.online .state-dot {
  background: #16a34a;
}

.version-strip {
  min-height: 54px;
  padding: 8px 12px;
  display: flex;
  align-items: stretch;
  gap: 8px;
  overflow-x: auto;
  border-top: 1px solid var(--line);
  background: var(--surface-soft);
}

.strip-label {
  min-width: 46px;
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 12px;
}

.version-item {
  min-width: 145px;
  padding: 7px 9px;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 2px 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--text);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
}

.version-item strong,
.version-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-item small {
  color: var(--muted);
  font-size: 11px;
}

.version-item-actions {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.version-item.active {
  border-color: #60a5fa;
  background: var(--surface-active);
}

.configuration-grid {
  height: 540px;
  display: grid;
  grid-template-columns: minmax(230px, 0.8fr) minmax(330px, 1.12fr) minmax(430px, 1.65fr);
  gap: 12px;
  margin-bottom: 12px;
}

.work-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  min-height: 50px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--line);
}

.panel-title {
  min-width: 0;
  font-weight: 600;
}

.panel-icon,
.tab-icon {
  width: 17px;
  height: 17px;
  color: #475467;
}

.tab-icon {
  margin-right: 5px;
  vertical-align: -3px;
}

.panel-filter {
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}

.scene-list,
.master-list,
.rule-list {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.scene-row,
.master-row {
  width: 100%;
  min-height: 68px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  border-bottom: 1px solid #e9edf2;
  color: var(--text);
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.scene-row:hover,
.master-row:hover,
.scene-row.selected,
.master-row.selected {
  background: var(--surface-active);
}

.scene-row.selected,
.master-row.selected {
  box-shadow: inset 3px 0 0 var(--primary);
}

.scene-row-main,
.master-row-content,
.scene-row-side {
  min-width: 0;
  display: flex;
}

.scene-row-main,
.master-row-content {
  flex: 1;
  flex-direction: column;
  gap: 5px;
}

.scene-row-main strong,
.scene-row-main small,
.master-row-content strong,
.master-row-content small,
.row-summary {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-row-main small,
.master-row-content small,
.row-summary,
.cell-subtitle,
.rule-meta,
.rule-row p,
.linked-tags > span,
.linked-tags > small,
.empty-inline {
  color: var(--muted);
  font-size: 12px;
}

.scene-row-side {
  align-items: flex-end;
  flex-direction: column;
  gap: 7px;
}

.row-actions {
  display: inline-flex;
  align-items: center;
}

.master-row-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tab-header {
  padding-bottom: 0;
}

.master-tabs {
  min-width: 0;
  flex: 1;
}

.master-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.master-tabs :deep(.el-tabs__content) {
  display: none;
}

.variable-table-wrap {
  min-height: 0;
  flex: 1;
  padding: 10px;
}

.fee-context {
  padding: 11px 12px;
  display: grid;
  gap: 8px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-soft);
}

.fee-context > div:first-child {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.fee-context > div:first-child span {
  color: var(--muted);
  font-size: 12px;
}

.linked-tags {
  min-width: 0;
  flex-wrap: wrap;
}

.rule-row {
  min-height: 112px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e9edf2;
}

.rule-row:hover {
  background: #fafcff;
}

.rule-row-main {
  min-width: 0;
  flex: 1;
}

.rule-heading,
.rule-meta {
  flex-wrap: wrap;
}

.rule-meta {
  margin: 8px 0 6px;
}

.rule-meta span + span::before {
  margin-right: 9px;
  color: #c0c7d1;
  content: "|";
}

.rule-row p {
  margin: 0;
  line-height: 1.5;
}

.rule-row-actions {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.runtime-band {
  padding: 0 14px 14px;
}

.runtime-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.simulation-layout {
  display: grid;
  grid-template-columns: minmax(480px, 1.35fr) minmax(320px, 0.9fr);
  gap: 14px;
}

.simulation-form,
.result-preview {
  min-width: 0;
}

.simulation-toolbar {
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.simulation-toolbar .el-select {
  width: 230px;
}

.simulation-actions {
  margin-top: 10px;
  justify-content: flex-end;
}

.result-preview {
  min-height: 260px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: #101828;
  overflow: hidden;
}

.preview-header {
  min-height: 40px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #f8fafc;
  border-bottom: 1px solid #344054;
}

.result-preview pre,
.json-detail pre {
  margin: 0;
  padding: 12px;
  overflow: auto;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.result-preview pre {
  max-height: 260px;
  color: #d0d5dd;
}

.runtime-table-toolbar {
  min-height: 36px;
  margin-bottom: 10px;
  justify-content: flex-end;
}

.runtime-table-toolbar > span {
  margin-right: auto;
  color: var(--muted);
  font-size: 12px;
}

.runtime-band .el-pagination {
  margin-top: 10px;
  justify-content: flex-end;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.dialog-grid .span-2 {
  grid-column: 1 / -1;
}

.dialog-grid :deep(.el-select),
.dialog-grid :deep(.el-input-number) {
  width: 100%;
}

.dialog-toolbar {
  margin-bottom: 10px;
  justify-content: flex-end;
}

.editor-section {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.editor-section-header {
  margin-bottom: 9px;
  justify-content: space-between;
  align-items: flex-start;
}

.editor-section-header > div:first-child {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.editor-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.condition-group-note {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}

.condition-groups {
  display: grid;
  gap: 10px;
}

.condition-group {
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface-soft);
}

.condition-group-header {
  min-height: 28px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.condition-group-header > div:first-child {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.condition-group :deep(.el-table) {
  background: var(--surface);
}

.group-pricing-table :deep(.el-input-number) {
  width: 100%;
}

.editor-section :deep(.el-select),
.editor-section :deep(.el-input-number) {
  width: 100%;
}

.json-detail {
  max-height: 620px;
  position: relative;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface-soft);
  overflow: auto;
}

.cost-lite-workbench :deep(.el-overlay-dialog) {
  overflow: auto;
}

.cost-lite-workbench :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow: auto;
}

.detail-icon {
  width: 18px;
  height: 18px;
  position: absolute;
  top: 12px;
  right: 12px;
  color: #d97706;
}

@media (max-width: 1280px) {
  .configuration-grid {
    grid-template-columns: minmax(220px, 0.8fr) minmax(300px, 1.05fr) minmax(390px, 1.5fr);
  }
}

@media (max-width: 900px) {
  .cost-lite-workbench {
    min-width: 0;
    padding: 8px;
  }

  .header-main,
  .header-actions {
    align-items: flex-start;
  }

  .header-main,
  .configuration-grid,
  .simulation-layout {
    display: flex;
    flex-direction: column;
  }

  .configuration-grid {
    height: auto;
  }

  .work-panel {
    min-height: 420px;
  }

  .dialog-grid {
    grid-template-columns: 1fr;
  }

  .dialog-grid .span-2 {
    grid-column: auto;
  }

  .simulation-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .simulation-actions .el-button {
    width: 100%;
    min-width: 0;
    margin-left: 0;
  }

  .simulation-actions .el-button:last-child {
    grid-column: 1 / -1;
  }
}
</style>
