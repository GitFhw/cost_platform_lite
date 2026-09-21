import type { InjectionKey } from "vue";

export type CostLiteRecord = Record<string, any>;

export interface CostLiteDictionaryOption {
  label: string;
  value: string;
  disabled?: boolean;
}

export type CostLiteDictionary = Record<string, CostLiteDictionaryOption[]>;

export interface CostLitePage<T extends CostLiteRecord = CostLiteRecord> {
  rows: T[];
  total: number;
  hasMore?: boolean;
  hasPageMetadata?: boolean;
}

export interface CostLiteOptionPage extends CostLitePage<CostLiteDictionaryOption> {
  hasMore?: boolean;
  hasOptionSnapshot?: boolean;
}

export interface CostLiteRequest {
  method: "GET" | "POST" | "PUT" | "DELETE";
  url: string;
  params?: CostLiteRecord;
  data?: unknown;
}

export type CostLiteTransport = (request: CostLiteRequest) => Promise<unknown>;

export interface CostLiteApiOptions {
  basePath?: string;
}

export interface CostLiteApi {
  health(): Promise<CostLiteRecord>;
  bootstrap(): Promise<CostLiteRecord>;
  listDictionaries(dictTypes: string[]): Promise<CostLiteDictionary>;

  listScenes(params?: CostLiteRecord): Promise<CostLitePage>;
  getScene(sceneId: number | string): Promise<CostLiteRecord>;
  getSceneGovernance?(sceneId: number | string): Promise<CostLiteRecord>;
  createScene(data: CostLiteRecord): Promise<unknown>;
  copyScene?(data: CostLiteRecord): Promise<CostLiteRecord>;
  updateScene(data: CostLiteRecord): Promise<unknown>;
  deleteScenes(sceneIds: Array<number | string>): Promise<unknown>;

  listFees(sceneId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  getFee(feeId: number | string): Promise<CostLiteRecord>;
  getFeeGovernance(feeId: number | string): Promise<CostLiteRecord>;
  listFeeVariables?(feeId: number | string): Promise<CostLiteRecord[]>;
  replaceFeeVariables?(feeId: number | string, data: CostLiteRecord[]): Promise<unknown>;
  createFee(data: CostLiteRecord): Promise<unknown>;
  updateFee(data: CostLiteRecord): Promise<unknown>;
  disableFees?(feeIds: Array<number | string>): Promise<unknown>;
  deleteFees(feeIds: Array<number | string>): Promise<unknown>;

  listVariables(sceneId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  getVariable(variableId: number | string): Promise<CostLiteRecord>;
  getVariableGovernance?(variableId: number | string): Promise<CostLiteRecord>;
  createVariable(data: CostLiteRecord): Promise<unknown>;
  copyVariable?(data: CostLiteRecord): Promise<CostLiteRecord>;
  updateVariable(data: CostLiteRecord): Promise<unknown>;
  deleteVariables(variableIds: Array<number | string>): Promise<unknown>;
  listVariableOptions?(variableId: number | string, params?: CostLiteRecord): Promise<CostLiteOptionPage>;
  testRemoteVariable?(data: CostLiteRecord): Promise<CostLiteRecord>;
  previewRemoteVariable?(data: CostLiteRecord): Promise<CostLiteRecord>;
  refreshRemoteVariables?(sceneId?: number | string): Promise<CostLiteRecord>;

  listVariableGroups(sceneId: number | string): Promise<CostLiteRecord[]>;
  createVariableGroup(data: CostLiteRecord): Promise<unknown>;
  updateVariableGroup(data: CostLiteRecord): Promise<unknown>;
  deleteVariableGroups(groupIds: Array<number | string>): Promise<unknown>;

  listRules(sceneId: number | string, feeId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  getRule(ruleId: number | string): Promise<CostLiteRecord>;
  getRuleGovernance?(ruleId: number | string): Promise<CostLiteRecord>;
  createRule(data: CostLiteRecord): Promise<unknown>;
  copyRule?(data: CostLiteRecord): Promise<unknown>;
  updateRule(data: CostLiteRecord): Promise<unknown>;
  deleteRules(ruleIds: Array<number | string>): Promise<unknown>;
  previewRule(data: CostLiteRecord): Promise<CostLiteRecord>;
  previewRuleConflict(data: CostLiteRecord): Promise<CostLiteRecord[]>;

  listFormulaOptions(sceneId: number | string): Promise<CostLiteRecord[]>;
  getFormula?(formulaId: number | string): Promise<CostLiteRecord>;
  getFormulaGovernance?(formulaId: number | string): Promise<CostLiteRecord>;
  createFormula?(data: CostLiteRecord): Promise<unknown>;
  updateFormula?(data: CostLiteRecord): Promise<unknown>;
  deleteFormulas?(formulaIds: Array<number | string>): Promise<unknown>;
  listFormulaVersions?(formulaId: number | string): Promise<CostLiteRecord[]>;
  getFormulaVersion?(versionId: number | string): Promise<CostLiteRecord>;
  rollbackFormulaVersion?(versionId: number | string): Promise<unknown>;
  testFormula?(data: CostLiteRecord): Promise<CostLiteRecord>;
  listVersions(sceneId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  precheckVersion(sceneId: number | string): Promise<CostLiteRecord>;
  getVersion?(versionId: number | string, params?: CostLiteRecord): Promise<CostLiteRecord>;
  getPublishDiff?(params: CostLiteRecord): Promise<CostLiteRecord>;
  createVersion(data: CostLiteRecord): Promise<CostLiteRecord>;
  activateVersion(versionId: number | string): Promise<unknown>;
  rollbackVersion?(versionId: number | string): Promise<unknown>;

  getInputTemplate(sceneId: number | string, feeIds?: Array<number | string>): Promise<CostLiteRecord>;
  calculate(data: CostLiteRecord): Promise<CostLiteRecord>;
  executeSimulation(data: CostLiteRecord): Promise<CostLiteRecord>;
  executeSimulationBatch(data: CostLiteRecord): Promise<CostLiteRecord>;
  precheckTask?(data: CostLiteRecord): Promise<CostLiteRecord>;
  submitTask(data: CostLiteRecord): Promise<CostLiteRecord>;

  listBillingLogs(params?: CostLiteRecord): Promise<CostLitePage>;
  getBillingLog(simulationId: number | string): Promise<CostLiteRecord>;
  listResults(params?: CostLiteRecord): Promise<CostLitePage>;
  getResult(resultId: number | string): Promise<CostLiteRecord>;
  getTrace(traceId: number | string): Promise<CostLiteRecord>;
}

export const COST_LITE_API_KEY: InjectionKey<CostLiteApi> = Symbol("cost-lite-api");

export type CostLitePermission = string | string[] | undefined;
export type CostLitePermissionResolver = (permission: CostLitePermission) => boolean;
export const COST_LITE_PERMISSION_KEY: InjectionKey<CostLitePermissionResolver> = Symbol("cost-lite-permission");

function isRecord(value: unknown): value is CostLiteRecord {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function responseBody(response: unknown): unknown {
  if (
    isRecord(response) &&
    "data" in response &&
    "config" in response &&
    "headers" in response
  ) {
    return response.data;
  }
  return response;
}

function assertSuccess(body: unknown): void {
  if (!isRecord(body)) {
    return;
  }
  if (typeof body.status === "number" && body.status !== 1) {
    throw new Error(body.error || body.statusText || "轻量计费接口调用失败");
  }
  if (typeof body.code === "number" && ![0, 200].includes(body.code)) {
    throw new Error(body.msg || body.message || "轻量计费接口调用失败");
  }
}

function payloadOf(response: unknown): unknown {
  const body = responseBody(response);
  assertSuccess(body);
  if (!isRecord(body)) {
    return body;
  }
  if (typeof body.status === "number") {
    return body.data;
  }
  if (typeof body.code === "number" && "data" in body) {
    return body.data;
  }
  return body;
}

function recordOf(response: unknown): CostLiteRecord {
  const payload = payloadOf(response);
  return isRecord(payload) ? payload : {};
}

function arrayOf(response: unknown): CostLiteRecord[] {
  const payload = payloadOf(response);
  if (Array.isArray(payload)) {
    return payload.filter(isRecord);
  }
  if (isRecord(payload)) {
    const candidates = [payload.rows, payload.records, payload.list, payload.items];
    const rows = candidates.find(Array.isArray);
    return Array.isArray(rows) ? rows.filter(isRecord) : [];
  }
  return [];
}

function pageOf(response: unknown): CostLitePage {
  const body = responseBody(response);
  assertSuccess(body);
  const payload = payloadOf(body);
  const pageSource = isRecord(payload) ? payload : isRecord(body) ? body : {};
  const directRows = Array.isArray(payload) ? payload : undefined;
  const rows = directRows ?? [pageSource.rows, pageSource.records, pageSource.list, pageSource.items]
    .find(Array.isArray);
  const bodyRows = isRecord(body) && Array.isArray(body.rows) ? body.rows : undefined;
  const normalizedRows = (rows || bodyRows || []).filter(isRecord);
  const explicitTotal = pageSource.total ?? (isRecord(body) ? body.total : undefined);
  const rawTotal = explicitTotal ?? normalizedRows.length;
  const total = Number(rawTotal);
  const explicitHasMore = pageSource.hasMore
    ?? pageSource.hasNext
    ?? (isRecord(body) ? body.hasMore ?? body.hasNext : undefined);
  const hasPageMetadata = explicitTotal !== undefined || typeof explicitHasMore === "boolean";
  const hasMore = typeof explicitHasMore === "boolean"
    ? explicitHasMore
    : Number.isFinite(total) && total > normalizedRows.length;
  return {
    rows: normalizedRows,
    total: Number.isFinite(total) ? total : normalizedRows.length,
    hasMore,
    hasPageMetadata,
  };
}

function cleanParams(params?: CostLiteRecord): CostLiteRecord | undefined {
  if (!params) {
    return undefined;
  }
  const cleaned = Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ""),
  );
  return Object.keys(cleaned).length ? cleaned : undefined;
}

function joinPath(basePath: string, path: string): string {
  const base = `/${basePath}`.replace(/\/{2,}/g, "/").replace(/\/$/, "");
  const suffix = `/${path}`.replace(/\/{2,}/g, "/");
  return `${base}${suffix}`.replace(/\/{2,}/g, "/");
}

export function createCostLiteApi(
  transport: CostLiteTransport,
  options: CostLiteApiOptions = {},
): CostLiteApi {
  const basePath = options.basePath || "/cost";

  const request = (method: CostLiteRequest["method"], path: string, params?: CostLiteRecord, data?: unknown) =>
    transport({ method, url: joinPath(basePath, path), params: cleanParams(params), data });

  const getRecord = async (path: string, params?: CostLiteRecord) => recordOf(await request("GET", path, params));
  const getArray = async (path: string, params?: CostLiteRecord) => arrayOf(await request("GET", path, params));
  const getPage = async (path: string, params?: CostLiteRecord) => pageOf(await request("GET", path, params));
  const send = async (method: CostLiteRequest["method"], path: string, data?: unknown) =>
    payloadOf(await request(method, path, undefined, data));

  return {
    health: () => getRecord("/lite/health"),
    bootstrap: () => getRecord("/lite/bootstrap"),
    listDictionaries: async (dictTypes) => recordOf(await request(
      "GET",
      "/dictionary/options",
      { types: dictTypes.join(",") },
    )) as CostLiteDictionary,

    listScenes: (params) => getPage("/scene/list", params),
    getScene: (sceneId) => getRecord(`/scene/${sceneId}`),
    getSceneGovernance: (sceneId) => getRecord(`/scene/governance/${sceneId}`),
    createScene: (data) => send("POST", "/scene", data),
    copyScene: async (data) => recordOf(await request("POST", "/scene/copy", undefined, data)),
    updateScene: (data) => send("PUT", "/scene", data),
    deleteScenes: (sceneIds) => send("DELETE", `/scene/${sceneIds.join(",")}`),

    listFees: (sceneId, params) => getPage(
      "/fee/list",
      { ...params, sceneId },
    ),
    getFee: (feeId) => getRecord(`/fee/${feeId}`),
    getFeeGovernance: (feeId) => getRecord(`/fee/governance/${feeId}`),
    listFeeVariables: (feeId) => getArray(`/fee/${feeId}/variables`),
    replaceFeeVariables: (feeId, data) => send("PUT", `/fee/${feeId}/variables`, data),
    createFee: (data) => send("POST", "/fee", data),
    updateFee: (data) => send("PUT", "/fee", data),
    disableFees: (feeIds) => send("PUT", `/fee/disable/${feeIds.join(",")}`),
    deleteFees: (feeIds) => send("DELETE", `/fee/${feeIds.join(",")}`),

    listVariables: (sceneId, params) => getPage(
      "/variable/list",
      { ...params, sceneId },
    ),
    getVariable: (variableId) => getRecord(`/variable/${variableId}`),
    getVariableGovernance: (variableId) => getRecord(`/variable/governance/${variableId}`),
    createVariable: (data) => send("POST", "/variable", data),
    copyVariable: async (data) => recordOf(await request("POST", "/variable/copy", undefined, data)),
    updateVariable: (data) => send("PUT", "/variable", data),
    deleteVariables: (variableIds) => send("DELETE", `/variable/${variableIds.join(",")}`),
    listVariableOptions: (variableId, params) => getPage(
      "/variable/options",
      { ...params, variableId },
    ),
    testRemoteVariable: async (data) => recordOf(await request(
      "POST",
      "/variable/remote/test",
      undefined,
      data,
    )),
    previewRemoteVariable: async (data) => recordOf(await request(
      "POST",
      "/variable/remote/preview",
      undefined,
      data,
    )),
    refreshRemoteVariables: async (sceneId) => recordOf(await request(
      "POST",
      "/variable/remote/refresh",
      undefined,
      sceneId === undefined ? {} : { sceneId },
    )),

    listVariableGroups: (sceneId) => getArray(
      "/variable/group/list",
      { sceneId },
    ),
    createVariableGroup: (data) => send("POST", "/variable/group", data),
    updateVariableGroup: (data) => send("PUT", "/variable/group", data),
    deleteVariableGroups: (groupIds) => send(
      "DELETE",
      `/variable/group/${groupIds.join(",")}`,
    ),

    listRules: (sceneId, feeId, params) => getPage(
      "/rule/list",
      { ...params, sceneId, feeId },
    ),
    getRule: (ruleId) => getRecord(`/rule/${ruleId}`),
    getRuleGovernance: (ruleId) => getRecord(`/rule/governance/${ruleId}`),
    createRule: (data) => send("POST", "/rule", data),
    copyRule: (data) => send("POST", "/rule/copy", data),
    updateRule: (data) => send("PUT", "/rule", data),
    deleteRules: (ruleIds) => send("DELETE", `/rule/${ruleIds.join(",")}`),
    previewRule: async (data) => recordOf(await request(
      "POST",
      "/rule/tierPreview",
      undefined,
      data,
    )),
    previewRuleConflict: async (data) => arrayOf(await request(
      "POST",
      "/rule/conflictPreview",
      undefined,
      data,
    )),

    listFormulaOptions: (sceneId) => getArray(
      "/formula/optionselect",
      { sceneId },
    ),
    getFormula: (formulaId) => getRecord(`/formula/${formulaId}`),
    getFormulaGovernance: (formulaId) => getRecord(
      `/formula/governance/${formulaId}`,
    ),
    createFormula: (data) => send("POST", "/formula", data),
    updateFormula: (data) => send("PUT", "/formula", data),
    deleteFormulas: (formulaIds) => send(
      "DELETE",
      `/formula/${formulaIds.join(",")}`,
    ),
    listFormulaVersions: (formulaId) => getArray(
      `/formula/versions/${formulaId}`,
    ),
    getFormulaVersion: (versionId) => getRecord(
      `/formula/version/${versionId}`,
    ),
    rollbackFormulaVersion: (versionId) => send(
      "PUT",
      `/formula/version/rollback/${versionId}`,
    ),
    testFormula: async (data) => recordOf(await request(
      "POST",
      "/formula/test",
      undefined,
      data,
    )),
    listVersions: (sceneId, params) => getPage(
      "/publish/list",
      { ...params, sceneId },
    ),
    precheckVersion: (sceneId) => getRecord(`/publish/precheck/${sceneId}`),
    getVersion: (versionId, params) => getRecord(`/publish/${versionId}`, params),
    getPublishDiff: (params) => getRecord("/publish/diff", params),
    createVersion: async (data) => recordOf(await request(
      "POST",
      "/publish",
      undefined,
      data,
    )),
    activateVersion: (versionId) => send("PUT", `/publish/activate/${versionId}`),
    rollbackVersion: (versionId) => send("PUT", `/publish/rollback/${versionId}`),

    getInputTemplate: (sceneId, feeIds) => getRecord(
      feeIds?.length ? "/run/input-template/fee" : "/run/input-template",
      { sceneId, feeIds: feeIds?.join(",") },
    ),
    calculate: async (data) => recordOf(await request(
      "POST",
      "/run/fee/calculate",
      undefined,
      data,
    )),
    executeSimulation: async (data) => recordOf(await request(
      "POST",
      "/run/simulation/execute",
      undefined,
      data,
    )),
    executeSimulationBatch: async (data) => recordOf(await request(
      "POST",
      "/run/simulation/batch-execute",
      undefined,
      data,
    )),
    precheckTask: async (data) => recordOf(await request(
      "POST",
      "/run/task/precheck",
      undefined,
      data,
    )),
    submitTask: async (data) => recordOf(await request(
      "POST",
      "/run/task/submit",
      undefined,
      data,
    )),

    listBillingLogs: (params) => getPage(
      "/lite/billing-log/list",
      params,
    ),
    getBillingLog: (simulationId) => getRecord(
      `/lite/billing-log/${simulationId}`,
    ),
    listResults: (params) => getPage("/run/result/list", params),
    getResult: (resultId) => getRecord(`/run/result/${resultId}`),
    getTrace: (traceId) => getRecord(`/run/trace/${traceId}`),
  };
}
