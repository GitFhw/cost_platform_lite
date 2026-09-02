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
} from "./costLiteApi";

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
  saving: false,
  running: false,
  publishing: false,
});

const bootstrap = ref<CostLiteRecord>({});
const connected = ref(false);
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

function statusText(status: unknown): string {
  if (String(status) === "0") return "启用";
  if (String(status) === "1") return "停用";
  if (String(status) === "2") return "草稿";
  return status ? String(status) : "未知";
}

function statusType(status: unknown): "success" | "info" | "warning" {
  if (String(status) === "0") return "success";
  if (String(status) === "2") return "warning";
  return "info";
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
  return mapping[String(value)] || String(value || "已发布");
}

function ruleTypeText(type: unknown): string {
  const mapping: Record<string, string> = {
    FIXED_RATE: "固定费率",
    FIXED_AMOUNT: "固定金额",
    TIER_RATE: "阶梯费率",
    FORMULA: "金额公式",
  };
  return mapping[String(type)] || String(type || "-");
}

function formatDateTime(value: unknown): string {
  if (!value) return "-";
  return String(value).replace("T", " ").replace(/\.\d+$/, "");
}

async function initialize(): Promise<void> {
  loading.bootstrap = true;
  try {
    const [health, info] = await Promise.all([api().health(), api().bootstrap()]);
    connected.value = health.service === "UP" || health.database === "UP";
    bootstrap.value = info;
    await loadScenes();
  } catch (error) {
    connected.value = false;
    ElMessage.error(error instanceof Error ? error.message : "轻量计费服务连接失败");
  } finally {
    loading.bootstrap = false;
  }
}

async function loadScenes(preferredSceneId?: number | string): Promise<void> {
  loading.scenes = true;
  try {
    const page = await api().listScenes({ pageNum: 1, pageSize: props.pageSize });
    scenes.value = page.rows;
    const target = preferredSceneId ?? selectedSceneId.value;
    const exists = scenes.value.some((item) => String(item.sceneId) === String(target));
    selectedSceneId.value = exists ? target : scenes.value[0]?.sceneId;
    if (!selectedSceneId.value) {
      clearSceneContext();
    }
  } finally {
    loading.scenes = false;
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

async function loadSceneContext(): Promise<void> {
  const sceneId = selectedSceneId.value;
  if (!sceneId) {
    clearSceneContext();
    return;
  }
  loading.context = true;
  try {
    const [feePage, variablePage, groups, versionPage, formulas] = await Promise.all([
      api().listFees(sceneId, { pageNum: 1, pageSize: props.pageSize }),
      api().listVariables(sceneId, { pageNum: 1, pageSize: props.pageSize }),
      api().listVariableGroups(sceneId),
      api().listVersions(sceneId, { pageNum: 1, pageSize: props.pageSize }),
      api().listFormulaOptions(sceneId),
    ]);
    fees.value = feePage.rows;
    variables.value = variablePage.rows;
    variableGroups.value = groups;
    versions.value = versionPage.rows;
    formulaOptions.value = formulas;
    const feeExists = fees.value.some((item) => String(item.feeId) === String(selectedFeeId.value));
    selectedFeeId.value = feeExists ? selectedFeeId.value : fees.value[0]?.feeId;
    if (!selectedFeeId.value) {
      rules.value = [];
      feeGovernance.value = {};
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "场景配置加载失败");
  } finally {
    loading.context = false;
  }
}

async function loadRules(): Promise<void> {
  if (!selectedSceneId.value || !selectedFeeId.value) {
    rules.value = [];
    feeGovernance.value = {};
    return;
  }
  loading.rules = true;
  try {
    const [page, governance] = await Promise.all([
      api().listRules(selectedSceneId.value, selectedFeeId.value, {
        pageNum: 1,
        pageSize: props.pageSize,
      }),
      api().getFeeGovernance(selectedFeeId.value),
    ]);
    rules.value = page.rows;
    feeGovernance.value = governance;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "费率规则加载失败");
  } finally {
    loading.rules = false;
  }
}

function selectScene(scene: CostLiteRecord): void {
  selectedSceneId.value = scene.sceneId;
}

function selectFee(fee: CostLiteRecord): void {
  selectedFeeId.value = fee.feeId;
}

watch(selectedSceneId, loadSceneContext);
watch(selectedFeeId, loadRules);

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

function openSceneDialog(scene?: CostLiteRecord): void {
  resetObject(sceneForm, {
    sceneId: scene?.sceneId,
    sceneCode: scene?.sceneCode || "",
    sceneName: scene?.sceneName || "",
    businessDomain: scene?.businessDomain || "",
    orgCode: scene?.orgCode || "",
    sceneType: scene?.sceneType || "CONTRACT",
    defaultObjectDimension: scene?.defaultObjectDimension || "",
    status: scene?.status ?? "2",
    remark: scene?.remark || "",
  });
  sceneDialogVisible.value = true;
}

async function saveScene(): Promise<void> {
  if (!(await sceneFormRef.value?.validate().catch(() => false))) return;
  loading.saving = true;
  try {
    if (sceneForm.sceneId) await api().updateScene({ ...sceneForm });
    else await api().createScene({ ...sceneForm });
    ElMessage.success("场景已保存");
    sceneDialogVisible.value = false;
    await loadScenes(sceneForm.sceneId);
    await loadSceneContext();
  } finally {
    loading.saving = false;
  }
}

async function deleteScene(scene: CostLiteRecord): Promise<void> {
  await ElMessageBox.confirm(`确认删除场景“${scene.sceneName}”？`, "删除场景", { type: "warning" });
  await api().deleteScenes([scene.sceneId]);
  ElMessage.success("场景已删除");
  await loadScenes();
}

const feeDialogVisible = ref(false);
const feeFormRef = ref<FormInstance>();
const feeForm = reactive<CostLiteRecord>({});
const feeRules: FormRules = {
  feeCode: [{ required: true, message: "请输入费目编码", trigger: "blur" }],
  feeName: [{ required: true, message: "请输入费目名称", trigger: "blur" }],
};

function openFeeDialog(fee?: CostLiteRecord): void {
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
}

async function saveFee(): Promise<void> {
  if (!(await feeFormRef.value?.validate().catch(() => false))) return;
  loading.saving = true;
  try {
    if (feeForm.feeId) await api().updateFee({ ...feeForm });
    else await api().createFee({ ...feeForm });
    ElMessage.success("费目已保存");
    feeDialogVisible.value = false;
    await loadSceneContext();
  } finally {
    loading.saving = false;
  }
}

async function deleteFee(fee: CostLiteRecord): Promise<void> {
  await ElMessageBox.confirm(`确认删除费目“${fee.feeName}”？`, "删除费目", { type: "warning" });
  await api().deleteFees([fee.feeId]);
  ElMessage.success("费目已删除");
  await loadSceneContext();
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

function openVariableDialog(variable?: CostLiteRecord): void {
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
    dataPath: variable?.dataPath || "",
    remoteApi: variable?.remoteApi || "",
    requestMethod: variable?.requestMethod || "GET",
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
  } finally {
    loading.saving = false;
  }
}

async function deleteVariable(variable: CostLiteRecord): Promise<void> {
  await ElMessageBox.confirm(`确认删除要素“${variable.variableName}”？`, "删除要素", { type: "warning" });
  await api().deleteVariables([variable.variableId]);
  ElMessage.success("要素已删除");
  await loadSceneContext();
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
  } finally {
    loading.saving = false;
  }
}

async function deleteVariableGroup(group: CostLiteRecord): Promise<void> {
  await ElMessageBox.confirm(`确认删除分组“${group.groupName}”？`, "删除分组", { type: "warning" });
  await api().deleteVariableGroups([group.groupId]);
  variableGroups.value = await api().listVariableGroups(selectedSceneId.value!);
  ElMessage.success("要素分组已删除");
}

const ruleDialogVisible = ref(false);
const ruleFormRef = ref<FormInstance>();
const ruleForm = reactive<CostLiteRecord>({});
const ruleRules: FormRules = {
  ruleCode: [{ required: true, message: "请输入规则编码", trigger: "blur" }],
  ruleName: [{ required: true, message: "请输入规则名称", trigger: "blur" }],
  ruleType: [{ required: true, message: "请选择规则类型", trigger: "change" }],
};

function newCondition(): CostLiteRecord {
  return { groupNo: 1, variableCode: "", operatorCode: "EQ", compareValue: "", status: "0" };
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
  const detail = rule?.ruleId ? await api().getRule(rule.ruleId) : {};
  const source = Object.keys(detail).length ? detail : rule || {};
  resetObject(ruleForm, {
    ruleId: source.ruleId,
    sceneId: selectedSceneId.value,
    feeId: selectedFeeId.value,
    ruleCode: source.ruleCode || "",
    ruleName: source.ruleName || "",
    ruleType: source.ruleType || "FIXED_RATE",
    conditionLogic: source.conditionLogic || "AND",
    priority: Number(source.priority ?? 100),
    quantityVariableCode: source.quantityVariableCode || "",
    pricingMode: "TYPED",
    pricingConfig: {
      rateValue: source.pricingConfig?.rateValue ?? source.pricingConfig?.unitPrice,
      amountValue: source.pricingConfig?.amountValue ?? source.pricingConfig?.amount,
    },
    amountFormulaCode: source.amountFormulaCode || "",
    amountFormula: source.amountFormula || "",
    noteTemplate: source.noteTemplate || "",
    status: source.status ?? "0",
    sortNo: Number(source.sortNo ?? 10),
    remark: source.remark || "",
    conditions: Array.isArray(source.conditions) ? source.conditions.map((item: CostLiteRecord) => ({ ...item })) : [],
    tiers: Array.isArray(source.tiers) ? source.tiers.map((item: CostLiteRecord) => ({ ...item })) : [],
  });
  ruleDialogVisible.value = true;
}

function variableName(code: unknown): string {
  return variables.value.find((item) => item.variableCode === code)?.variableName || String(code || "");
}

function addCondition(): void {
  ruleForm.conditions.push(newCondition());
}

function removeCondition(index: number): void {
  ruleForm.conditions.splice(index, 1);
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
  payload.conditions = (payload.conditions || [])
    .filter((item: CostLiteRecord) => item.variableCode)
    .map((item: CostLiteRecord, index: number) => ({
      ...item,
      sceneId: selectedSceneId.value,
      groupNo: Number(item.groupNo || 1),
      sortNo: index + 1,
      displayName: item.displayName || variableName(item.variableCode),
      compareValue: item.compareValue == null ? "" : String(item.compareValue),
      status: item.status || "0",
    }));
  payload.tiers = (payload.tiers || []).map((item: CostLiteRecord, index: number) => ({
    ...item,
    sceneId: selectedSceneId.value,
    tierNo: index + 1,
    status: item.status || "0",
  }));
  return payload;
}

async function saveRule(): Promise<void> {
  if (!(await ruleFormRef.value?.validate().catch(() => false))) return;
  if (["FIXED_RATE", "TIER_RATE"].includes(ruleForm.ruleType) && !ruleForm.quantityVariableCode) {
    ElMessage.warning("请选择计量要素");
    return;
  }
  if (ruleForm.ruleType === "FORMULA" && !ruleForm.amountFormulaCode && !ruleForm.amountFormula) {
    ElMessage.warning("请选择公式或填写金额公式");
    return;
  }
  const payload = buildRulePayload();
  loading.saving = true;
  try {
    const warnings = await api().previewRuleConflict(payload);
    if (warnings.length) {
      await ElMessageBox.confirm(
        warnings.slice(0, 3).map((item) => item.message || item.summary || JSON.stringify(item)).join("\n"),
        "发现规则冲突",
        { type: "warning", confirmButtonText: "仍然保存" },
      );
    }
    if (payload.ruleId) await api().updateRule(payload);
    else await api().createRule(payload);
    ElMessage.success("费率规则已保存");
    ruleDialogVisible.value = false;
    await loadRules();
  } finally {
    loading.saving = false;
  }
}

async function previewRule(): Promise<void> {
  const result = await api().previewRule({ rule: buildRulePayload(), inputValues: {} });
  detailTitle.value = "规则预览";
  detailJson.value = result;
  detailVisible.value = true;
}

async function deleteRule(rule: CostLiteRecord): Promise<void> {
  await ElMessageBox.confirm(`确认删除规则“${rule.ruleName || rule.ruleCode}”？`, "删除规则", { type: "warning" });
  await api().deleteRules([rule.ruleId]);
  ElMessage.success("规则已删除");
  await loadRules();
}

const publishDialogVisible = ref(false);
const publishFormRef = ref<FormInstance>();
const publishForm = reactive<CostLiteRecord>({ publishDesc: "", activateNow: true });
const publishRules: FormRules = {
  publishDesc: [{ required: true, message: "请输入发布说明", trigger: "blur" }],
};

async function runPublishPrecheck(): Promise<void> {
  if (!selectedSceneId.value) return;
  const result = await api().precheckVersion(selectedSceneId.value);
  detailTitle.value = "发布检查";
  detailJson.value = result;
  detailVisible.value = true;
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
    await api().createVersion({
      sceneId: selectedSceneId.value,
      publishDesc: publishForm.publishDesc,
      activateNow: publishForm.activateNow,
    });
    ElMessage.success(publishForm.activateNow ? "版本已发布并生效" : "版本已发布");
    publishDialogVisible.value = false;
    await loadScenes(selectedSceneId.value);
    await loadSceneContext();
  } finally {
    loading.publishing = false;
  }
}

async function activateVersion(version: CostLiteRecord): Promise<void> {
  await ElMessageBox.confirm(`确认将版本 ${version.versionNo || version.versionId} 设为生效？`, "版本生效");
  await api().activateVersion(version.versionId);
  ElMessage.success("版本已生效");
  await loadScenes(selectedSceneId.value);
  await loadSceneContext();
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
  const template = await api().getInputTemplate(
    selectedSceneId.value,
    selectedFeeId.value ? [selectedFeeId.value] : undefined,
  );
  const value = template.inputJson || template.sampleInputJson || template.template || template;
  simulationForm.inputJson = typeof value === "string" ? value : JSON.stringify(value, null, 2);
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
      simulationResult.value = await api().submitTask({
        sceneId: selectedSceneId.value,
        versionId: simulationForm.versionId || activeVersionId.value,
        taskType: Array.isArray(parsed) ? "FORMAL_BATCH" : "FORMAL_SINGLE",
        billMonth: simulationForm.billMonth,
        requestNo: `COST-LITE-${Date.now()}`,
        inputSourceType: "INLINE_JSON",
        inputJson: JSON.stringify(parsed),
        remark: "Cost Lite 工作台提交",
      });
      ElMessage.success("正式任务已提交");
    }
    await Promise.all([loadBillingLogs(), loadResults()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "计费执行失败");
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
  } finally {
    loading.logs = false;
  }
}

const resultQuery = reactive({ pageNum: 1, pageSize: 20 });
const results = ref<CostLiteRecord[]>([]);
const resultTotal = ref(0);

async function loadResults(): Promise<void> {
  loading.results = true;
  try {
    const page = await api().listResults({
      ...resultQuery,
      sceneId: selectedSceneId.value,
    });
    results.value = page.rows;
    resultTotal.value = page.total;
  } finally {
    loading.results = false;
  }
}

watch(bottomTab, (tab) => {
  if (tab === "logs") loadBillingLogs();
  if (tab === "results") loadResults();
});

const detailVisible = ref(false);
const detailTitle = ref("详情");
const detailJson = ref<unknown>({});

async function showBillingLog(row: CostLiteRecord): Promise<void> {
  detailTitle.value = "调用日志";
  detailJson.value = await api().getBillingLog(row.simulationId);
  detailVisible.value = true;
}

async function showResult(row: CostLiteRecord): Promise<void> {
  detailTitle.value = "计费结果";
  const detail = await api().getResult(row.resultId);
  if (detail.traceId) {
    detail.trace = await api().getTrace(detail.traceId);
  }
  detailJson.value = detail;
  detailVisible.value = true;
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
        <button
          v-for="version in versions.slice(0, 8)"
          :key="version.versionId"
          class="version-item"
          :class="{ active: String(version.versionId) === String(activeVersionId) }"
          type="button"
          @click="simulationForm.versionId = version.versionId"
        >
          <strong>{{ version.versionNo || `#${version.versionId}` }}</strong>
          <small>{{ versionStatusText(version) }}</small>
          <el-button
            v-if="String(version.versionId) !== String(activeVersionId)"
            link
            type="primary"
            size="small"
            @click.stop="activateVersion(version)"
          >
            生效
          </el-button>
        </button>
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
          <button
            v-for="scene in filteredScenes"
            :key="scene.sceneId"
            class="scene-row"
            :class="{ selected: String(scene.sceneId) === String(selectedSceneId) }"
            type="button"
            @click="selectScene(scene)"
          >
            <span class="scene-row-main">
              <strong>{{ scene.sceneName }}</strong>
              <small>{{ scene.sceneCode }}</small>
            </span>
            <span class="scene-row-side">
              <el-tag :type="statusType(scene.status)" size="small">{{ statusText(scene.status) }}</el-tag>
              <span class="row-actions" @click.stop>
                <el-button link :icon="Edit" title="编辑" @click="openSceneDialog(scene)" />
                <el-button link type="danger" :icon="Delete" title="删除" @click="deleteScene(scene)" />
              </span>
            </span>
          </button>
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
          <button
            v-for="fee in fees"
            :key="fee.feeId"
            class="master-row"
            :class="{ selected: String(fee.feeId) === String(selectedFeeId) }"
            type="button"
            @click="selectFee(fee)"
          >
            <span class="master-row-content">
              <span class="master-row-title">
                <strong>{{ fee.feeName }}</strong>
                <el-tag :type="statusType(fee.status)" size="small">{{ statusText(fee.status) }}</el-tag>
              </span>
              <small>{{ fee.feeCode }} · {{ fee.feeCategory || '未分类' }} · {{ fee.unitCode || '未设单位' }}</small>
              <span class="row-summary">{{ fee.factorSummary || fee.scopeDescription || '暂无影响因素摘要' }}</span>
            </span>
            <span class="row-actions" @click.stop>
              <el-button link :icon="Edit" title="编辑" @click="openFeeDialog(fee)" />
              <el-button link type="danger" :icon="Delete" title="删除" @click="deleteFee(fee)" />
            </span>
          </button>
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
            <el-table-column prop="sourceType" label="来源" width="90" />
            <el-table-column prop="dataType" label="类型" width="90" />
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
                <el-tag :type="statusType(rule.status)" size="small">{{ statusText(rule.status) }}</el-tag>
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
                <el-button @click="fillInputTemplate">生成模板</el-button>
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
            <el-select v-model="logQuery.status" clearable placeholder="全部状态" style="width: 140px" @change="loadBillingLogs">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button :icon="Refresh" @click="loadBillingLogs">刷新</el-button>
          </div>
          <el-table :data="billingLogs" v-loading="loading.logs" size="small" border height="300">
            <el-table-column prop="simulationId" label="日志ID" width="90" />
            <el-table-column prop="sceneName" label="场景" min-width="140" />
            <el-table-column prop="feeName" label="费目" min-width="140" />
            <el-table-column prop="simulationStatus" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.simulationStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.simulationStatus || row.status || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
            <el-table-column prop="errorMessage" label="异常" min-width="180" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="72" fixed="right">
              <template #default="{ row }"><el-button link :icon="View" @click="showBillingLog(row)" /></template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="logQuery.pageNum"
            v-model:page-size="logQuery.pageSize"
            :total="billingLogTotal"
            layout="total, prev, pager, next"
            @current-change="loadBillingLogs"
          />
        </el-tab-pane>

        <el-tab-pane name="results">
          <template #label><Coin class="tab-icon" />正式结果</template>
          <div class="runtime-table-toolbar">
            <span>正式任务写入结果台账与追溯；同步试算保存在调用日志。</span>
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
              <template #default="{ row }"><el-button link :icon="View" @click="showResult(row)" /></template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="resultQuery.pageNum"
            v-model:page-size="resultQuery.pageSize"
            :total="resultTotal"
            layout="total, prev, pager, next"
            @current-change="loadResults"
          />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="sceneDialogVisible" :title="sceneForm.sceneId ? '编辑场景' : '新增场景'" width="640px" destroy-on-close>
      <el-form ref="sceneFormRef" :model="sceneForm" :rules="sceneRules" label-width="104px">
        <div class="dialog-grid">
          <el-form-item label="场景编码" prop="sceneCode"><el-input v-model="sceneForm.sceneCode" /></el-form-item>
          <el-form-item label="场景名称" prop="sceneName"><el-input v-model="sceneForm.sceneName" /></el-form-item>
          <el-form-item label="业务域" prop="businessDomain"><el-input v-model="sceneForm.businessDomain" /></el-form-item>
          <el-form-item label="适用组织"><el-input v-model="sceneForm.orgCode" /></el-form-item>
          <el-form-item label="场景类型" prop="sceneType">
            <el-select v-model="sceneForm.sceneType">
              <el-option label="合同" value="CONTRACT" />
              <el-option label="方案" value="PLAN" />
              <el-option label="公司级" value="COMPANY" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象维度"><el-input v-model="sceneForm.defaultObjectDimension" /></el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="sceneForm.status">
              <el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio><el-radio value="2">草稿</el-radio>
            </el-radio-group>
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
          <el-form-item label="计价单位"><el-input v-model="feeForm.unitCode" /></el-form-item>
          <el-form-item label="对象维度"><el-input v-model="feeForm.objectDimension" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="feeForm.sortNo" :min="0" /></el-form-item>
          <el-form-item label="影响因素" class="span-2"><el-input v-model="feeForm.factorSummary" /></el-form-item>
          <el-form-item label="适用范围" class="span-2"><el-input v-model="feeForm.scopeDescription" /></el-form-item>
          <el-form-item label="状态"><el-radio-group v-model="feeForm.status"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
          <el-form-item label="备注" class="span-2"><el-input v-model="feeForm.remark" type="textarea" :rows="2" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="feeDialogVisible = false">取消</el-button><el-button type="primary" :loading="loading.saving" @click="saveFee">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="variableDialogVisible" :title="variableForm.variableId ? '编辑要素' : '新增要素'" width="760px" destroy-on-close>
      <el-form ref="variableFormRef" :model="variableForm" :rules="variableRules" label-width="104px">
        <div class="dialog-grid">
          <el-form-item label="要素编码" prop="variableCode"><el-input v-model="variableForm.variableCode" /></el-form-item>
          <el-form-item label="要素名称" prop="variableName"><el-input v-model="variableForm.variableName" /></el-form-item>
          <el-form-item label="要素分组">
            <el-select v-model="variableForm.groupId" clearable><el-option v-for="group in variableGroups" :key="group.groupId" :label="group.groupName" :value="group.groupId" /></el-select>
          </el-form-item>
          <el-form-item label="要素类型" prop="variableType">
            <el-select v-model="variableForm.variableType"><el-option v-for="item in ['NUMBER','TEXT','BOOLEAN','DATE','DICT','REMOTE','FORMULA']" :key="item" :label="item" :value="item" /></el-select>
          </el-form-item>
          <el-form-item label="来源类型" prop="sourceType">
            <el-select v-model="variableForm.sourceType"><el-option v-for="item in ['INPUT','DICT','REMOTE','FORMULA']" :key="item" :label="item" :value="item" /></el-select>
          </el-form-item>
          <el-form-item label="数据类型">
            <el-select v-model="variableForm.dataType"><el-option v-for="item in ['STRING','NUMBER','BOOLEAN','DATE']" :key="item" :label="item" :value="item" /></el-select>
          </el-form-item>
          <el-form-item label="来源系统"><el-input v-model="variableForm.sourceSystem" /></el-form-item>
          <el-form-item label="取值路径"><el-input v-model="variableForm.dataPath" /></el-form-item>
          <template v-if="variableForm.sourceType === 'REMOTE'">
            <el-form-item label="远程接口" class="span-2"><el-input v-model="variableForm.remoteApi" /></el-form-item>
            <el-form-item label="请求方式"><el-select v-model="variableForm.requestMethod"><el-option v-for="item in ['GET','POST','PUT']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
          </template>
          <template v-if="variableForm.sourceType === 'FORMULA' || variableForm.variableType === 'FORMULA'">
            <el-form-item label="公式编码"><el-input v-model="variableForm.formulaCode" /></el-form-item>
            <el-form-item label="公式表达式" class="span-2"><el-input v-model="variableForm.formulaExpr" type="textarea" :rows="3" /></el-form-item>
          </template>
          <el-form-item label="默认值"><el-input v-model="variableForm.defaultValue" /></el-form-item>
          <el-form-item label="小数精度"><el-input-number v-model="variableForm.precisionScale" :min="0" :max="12" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="variableForm.sortNo" :min="0" /></el-form-item>
          <el-form-item label="状态"><el-radio-group v-model="variableForm.status"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
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
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link :icon="Edit" @click="openGroupEditor(row)" /><el-button link type="danger" :icon="Delete" @click="deleteVariableGroup(row)" /></template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="groupEditorVisible" :title="groupForm.groupId ? '编辑分组' : '新增分组'" width="520px" append-to-body>
      <el-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-width="96px">
        <el-form-item label="分组编码" prop="groupCode"><el-input v-model="groupForm.groupCode" /></el-form-item>
        <el-form-item label="分组名称" prop="groupName"><el-input v-model="groupForm.groupName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="groupForm.sortNo" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="groupForm.status"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="groupEditorVisible = false">取消</el-button><el-button type="primary" :loading="loading.saving" @click="saveVariableGroup">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleDialogVisible" :title="ruleForm.ruleId ? '编辑费率规则' : '新增费率规则'" width="980px" destroy-on-close>
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="96px">
        <div class="dialog-grid rule-base-grid">
          <el-form-item label="规则编码" prop="ruleCode"><el-input v-model="ruleForm.ruleCode" /></el-form-item>
          <el-form-item label="规则名称" prop="ruleName"><el-input v-model="ruleForm.ruleName" /></el-form-item>
          <el-form-item label="规则类型" prop="ruleType"><el-select v-model="ruleForm.ruleType"><el-option label="固定费率" value="FIXED_RATE" /><el-option label="固定金额" value="FIXED_AMOUNT" /><el-option label="阶梯费率" value="TIER_RATE" /><el-option label="金额公式" value="FORMULA" /></el-select></el-form-item>
          <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="0" /></el-form-item>
          <el-form-item v-if="['FIXED_RATE','TIER_RATE'].includes(ruleForm.ruleType)" label="计量要素"><el-select v-model="ruleForm.quantityVariableCode" filterable><el-option v-for="item in variables" :key="item.variableCode" :label="`${item.variableName} (${item.variableCode})`" :value="item.variableCode" /></el-select></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FIXED_RATE'" label="费率"><el-input-number v-model="ruleForm.pricingConfig.rateValue" :precision="6" :min="0" /></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FIXED_AMOUNT'" label="固定金额"><el-input-number v-model="ruleForm.pricingConfig.amountValue" :precision="2" /></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FORMULA'" label="公式编码"><el-select v-model="ruleForm.amountFormulaCode" clearable filterable><el-option v-for="item in formulaOptions" :key="item.formulaCode" :label="`${item.formulaName || item.formulaCode} (${item.formulaCode})`" :value="item.formulaCode" /></el-select></el-form-item>
          <el-form-item v-if="ruleForm.ruleType === 'FORMULA'" label="手写公式" class="span-2"><el-input v-model="ruleForm.amountFormula" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="条件逻辑"><el-radio-group v-model="ruleForm.conditionLogic"><el-radio value="AND">全部满足</el-radio><el-radio value="OR">任一满足</el-radio></el-radio-group></el-form-item>
          <el-form-item label="状态"><el-radio-group v-model="ruleForm.status"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
        </div>
      </el-form>

      <div class="editor-section">
        <div class="editor-section-header"><strong>适用条件</strong><el-button :icon="Plus" size="small" @click="addCondition">添加条件</el-button></div>
        <el-table :data="ruleForm.conditions" size="small" border>
          <el-table-column label="要素" min-width="210"><template #default="{ row }"><el-select v-model="row.variableCode" filterable><el-option v-for="item in variables" :key="item.variableCode" :label="`${item.variableName} (${item.variableCode})`" :value="item.variableCode" /></el-select></template></el-table-column>
          <el-table-column label="操作符" width="150"><template #default="{ row }"><el-select v-model="row.operatorCode"><el-option v-for="item in ['EQ','NE','GT','GTE','LT','LTE','IN','BETWEEN','IS_NULL','NOT_NULL']" :key="item" :label="item" :value="item" /></el-select></template></el-table-column>
          <el-table-column label="比较值" min-width="180"><template #default="{ row }"><el-input v-model="row.compareValue" :disabled="['IS_NULL','NOT_NULL'].includes(row.operatorCode)" /></template></el-table-column>
          <el-table-column label="操作" width="64"><template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeCondition($index)" /></template></el-table-column>
        </el-table>
      </div>

      <div v-if="ruleForm.ruleType === 'TIER_RATE'" class="editor-section">
        <div class="editor-section-header"><strong>阶梯费率</strong><el-button :icon="Plus" size="small" @click="addTier">添加阶梯</el-button></div>
        <el-table :data="ruleForm.tiers" size="small" border>
          <el-table-column prop="tierNo" label="#" width="52" />
          <el-table-column label="起始值"><template #default="{ row }"><el-input-number v-model="row.startValue" :controls="false" /></template></el-table-column>
          <el-table-column label="截止值"><template #default="{ row }"><el-input-number v-model="row.endValue" :controls="false" /></template></el-table-column>
          <el-table-column label="费率"><template #default="{ row }"><el-input-number v-model="row.rateValue" :precision="6" :min="0" :controls="false" /></template></el-table-column>
          <el-table-column label="区间" width="190"><template #default="{ row }"><el-select v-model="row.intervalMode"><el-option label="左闭右开 [a,b)" value="LEFT_CLOSED_RIGHT_OPEN" /><el-option label="双闭 [a,b]" value="CLOSED" /></el-select></template></el-table-column>
          <el-table-column label="操作" width="64"><template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeTier($index)" /></template></el-table-column>
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
  min-width: 1040px;
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

.detail-icon {
  width: 18px;
  height: 18px;
  position: absolute;
  top: 12px;
  right: 12px;
  color: #d97706;
}

@media (max-width: 1280px) {
  .cost-lite-workbench {
    min-width: 960px;
  }

  .configuration-grid {
    grid-template-columns: 230px 320px minmax(410px, 1fr);
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
}
</style>
