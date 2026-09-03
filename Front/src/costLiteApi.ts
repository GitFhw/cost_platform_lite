import type { InjectionKey } from "vue";

export type CostLiteRecord = Record<string, any>;

export interface CostLiteDictionaryOption {
  label: string;
  value: string;
}

export type CostLiteDictionary = Record<string, CostLiteDictionaryOption[]>;

export interface CostLitePage<T extends CostLiteRecord = CostLiteRecord> {
  rows: T[];
  total: number;
}

export interface CostLiteRequest {
  method: "GET" | "POST" | "PUT" | "DELETE";
  url: string;
  params?: CostLiteRecord;
  data?: unknown;
}

export type CostLiteTransport = (request: CostLiteRequest) => Promise<unknown>;

export type CostLiteApiRouteMode = "proxy" | "runtime";

export interface CostLiteApiOptions {
  basePath?: string;
  routeMode?: CostLiteApiRouteMode;
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
  const rows = [pageSource.rows, pageSource.records, pageSource.list, pageSource.items]
    .find(Array.isArray);
  const bodyRows = isRecord(body) && Array.isArray(body.rows) ? body.rows : undefined;
  const normalizedRows = (rows || bodyRows || []).filter(isRecord);
  const rawTotal = pageSource.total ?? (isRecord(body) ? body.total : undefined) ?? normalizedRows.length;
  const total = Number(rawTotal);
  return {
    rows: normalizedRows,
    total: Number.isFinite(total) ? total : normalizedRows.length,
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
  const routeMode = options.routeMode || "proxy";
  const basePath = options.basePath || (routeMode === "runtime" ? "/cost" : "/cost-lite");

  const route = (proxyPath: string, runtimePath: string): string =>
    routeMode === "runtime" ? runtimePath : proxyPath;

  const request = (method: CostLiteRequest["method"], path: string, params?: CostLiteRecord, data?: unknown) =>
    transport({ method, url: joinPath(basePath, path), params: cleanParams(params), data });

  const getRecord = async (path: string, params?: CostLiteRecord) => recordOf(await request("GET", path, params));
  const getArray = async (path: string, params?: CostLiteRecord) => arrayOf(await request("GET", path, params));
  const getPage = async (path: string, params?: CostLiteRecord) => pageOf(await request("GET", path, params));
  const send = async (method: CostLiteRequest["method"], path: string, data?: unknown) =>
    payloadOf(await request(method, path, undefined, data));

  return {
    health: () => getRecord(route("/health", "/lite/health")),
    bootstrap: () => getRecord(route("/bootstrap", "/lite/bootstrap")),
    listDictionaries: async (dictTypes) => recordOf(await request(
      "GET",
      route("/dictionary/options", "/dictionary/options"),
      { types: dictTypes.join(",") },
    )) as CostLiteDictionary,

    listScenes: (params) => getPage(route("/scenes", "/scene/list"), params),
    getScene: (sceneId) => getRecord(route(`/scenes/${sceneId}`, `/scene/${sceneId}`)),
    getSceneGovernance: (sceneId) => getRecord(route(`/scenes/${sceneId}/governance`, `/scene/governance/${sceneId}`)),
    createScene: (data) => send("POST", route("/scenes", "/scene"), data),
    copyScene: async (data) => recordOf(await request("POST", route("/scenes/copy", "/scene/copy"), undefined, data)),
    updateScene: (data) => send("PUT", route("/scenes", "/scene"), data),
    deleteScenes: (sceneIds) => send("DELETE", route(`/scenes/${sceneIds.join(",")}`, `/scene/${sceneIds.join(",")}`)),

    listFees: (sceneId, params) => getPage(
      route(`/scenes/${sceneId}/fees`, "/fee/list"),
      routeMode === "runtime" ? { ...params, sceneId } : params,
    ),
    getFee: (feeId) => getRecord(route(`/fees/${feeId}`, `/fee/${feeId}`)),
    getFeeGovernance: (feeId) => getRecord(route(`/fees/${feeId}/governance`, `/fee/governance/${feeId}`)),
    createFee: (data) => send("POST", route("/fees", "/fee"), data),
    updateFee: (data) => send("PUT", route("/fees", "/fee"), data),
    disableFees: (feeIds) => send("PUT", route(`/fees/${feeIds.join(",")}/disable`, `/fee/disable/${feeIds.join(",")}`)),
    deleteFees: (feeIds) => send("DELETE", route(`/fees/${feeIds.join(",")}`, `/fee/${feeIds.join(",")}`)),

    listVariables: (sceneId, params) => getPage(
      route(`/scenes/${sceneId}/variables`, "/variable/list"),
      routeMode === "runtime" ? { ...params, sceneId } : params,
    ),
    getVariable: (variableId) => getRecord(route(`/variables/${variableId}`, `/variable/${variableId}`)),
    getVariableGovernance: (variableId) => getRecord(route(`/variables/${variableId}/governance`, `/variable/governance/${variableId}`)),
    createVariable: (data) => send("POST", route("/variables", "/variable"), data),
    copyVariable: async (data) => recordOf(await request("POST", route("/variables/copy", "/variable/copy"), undefined, data)),
    updateVariable: (data) => send("PUT", route("/variables", "/variable"), data),
    deleteVariables: (variableIds) => send("DELETE", route(`/variables/${variableIds.join(",")}`, `/variable/${variableIds.join(",")}`)),

    listVariableGroups: (sceneId) => getArray(
      route("/variable-groups", "/variable/group/list"),
      { sceneId },
    ),
    createVariableGroup: (data) => send("POST", route("/variable-groups", "/variable/group"), data),
    updateVariableGroup: (data) => send("PUT", route("/variable-groups", "/variable/group"), data),
    deleteVariableGroups: (groupIds) => send(
      "DELETE",
      route(`/variable-groups/${groupIds.join(",")}`, `/variable/group/${groupIds.join(",")}`),
    ),

    listRules: (sceneId, feeId, params) => getPage(
      route("/rules", "/rule/list"),
      { ...params, sceneId, feeId },
    ),
    getRule: (ruleId) => getRecord(route(`/rules/${ruleId}`, `/rule/${ruleId}`)),
    getRuleGovernance: (ruleId) => getRecord(route(`/rules/${ruleId}/governance`, `/rule/governance/${ruleId}`)),
    createRule: (data) => send("POST", route("/rules", "/rule"), data),
    copyRule: (data) => send("POST", route("/rules/copy", "/rule/copy"), data),
    updateRule: (data) => send("PUT", route("/rules", "/rule"), data),
    deleteRules: (ruleIds) => send("DELETE", route(`/rules/${ruleIds.join(",")}`, `/rule/${ruleIds.join(",")}`)),
    previewRule: async (data) => recordOf(await request(
      "POST",
      route("/rules/tier-preview", "/rule/tierPreview"),
      undefined,
      data,
    )),
    previewRuleConflict: async (data) => arrayOf(await request(
      "POST",
      route("/rules/conflict-preview", "/rule/conflictPreview"),
      undefined,
      data,
    )),

    listFormulaOptions: (sceneId) => getArray(
      route("/formulas/options", "/formula/optionselect"),
      { sceneId },
    ),
    getFormula: (formulaId) => getRecord(route(`/formulas/${formulaId}`, `/formula/${formulaId}`)),
    getFormulaGovernance: (formulaId) => getRecord(
      route(`/formulas/${formulaId}/governance`, `/formula/governance/${formulaId}`),
    ),
    createFormula: (data) => send("POST", route("/formulas", "/formula"), data),
    updateFormula: (data) => send("PUT", route("/formulas", "/formula"), data),
    deleteFormulas: (formulaIds) => send(
      "DELETE",
      route(`/formulas/${formulaIds.join(",")}`, `/formula/${formulaIds.join(",")}`),
    ),
    listFormulaVersions: (formulaId) => getArray(
      route(`/formulas/${formulaId}/versions`, `/formula/versions/${formulaId}`),
    ),
    getFormulaVersion: (versionId) => getRecord(
      route(`/formula-versions/${versionId}`, `/formula/version/${versionId}`),
    ),
    rollbackFormulaVersion: (versionId) => send(
      "PUT",
      route(`/formula-versions/${versionId}/rollback`, `/formula/version/rollback/${versionId}`),
    ),
    testFormula: async (data) => recordOf(await request(
      "POST",
      route("/formulas/test", "/formula/test"),
      undefined,
      data,
    )),
    listVersions: (sceneId, params) => getPage(
      route("/versions", "/publish/list"),
      { ...params, sceneId },
    ),
    precheckVersion: (sceneId) => getRecord(route(`/versions/precheck/${sceneId}`, `/publish/precheck/${sceneId}`)),
    getVersion: (versionId, params) => getRecord(route(`/versions/${versionId}`, `/publish/${versionId}`), params),
    getPublishDiff: (params) => getRecord(route("/versions/diff", "/publish/diff"), params),
    createVersion: async (data) => recordOf(await request(
      "POST",
      route("/versions", "/publish"),
      undefined,
      data,
    )),
    activateVersion: (versionId) => send("PUT", route(`/versions/${versionId}/activate`, `/publish/activate/${versionId}`)),
    rollbackVersion: (versionId) => send("PUT", route(`/versions/${versionId}/rollback`, `/publish/rollback/${versionId}`)),

    getInputTemplate: (sceneId, feeIds) => getRecord(
      route(
        "/input-template",
        feeIds?.length ? "/run/input-template/fee" : "/run/input-template",
      ),
      { sceneId, feeIds: feeIds?.join(",") },
    ),
    calculate: async (data) => recordOf(await request(
      "POST",
      route("/calculate", "/run/fee/calculate"),
      undefined,
      data,
    )),
    executeSimulation: async (data) => recordOf(await request(
      "POST",
      route("/simulations", "/run/simulation/execute"),
      undefined,
      data,
    )),
    executeSimulationBatch: async (data) => recordOf(await request(
      "POST",
      route("/simulations/batch", "/run/simulation/batch-execute"),
      undefined,
      data,
    )),
    precheckTask: async (data) => recordOf(await request(
      "POST",
      route("/tasks/precheck", "/run/task/precheck"),
      undefined,
      data,
    )),
    submitTask: async (data) => recordOf(await request(
      "POST",
      route("/tasks", "/run/task/submit"),
      undefined,
      data,
    )),

    listBillingLogs: (params) => getPage(
      route("/logs", "/lite/billing-log/list"),
      params,
    ),
    getBillingLog: (simulationId) => getRecord(
      route(`/logs/${simulationId}`, `/lite/billing-log/${simulationId}`),
    ),
    listResults: (params) => getPage(route("/results", "/run/result/list"), params),
    getResult: (resultId) => getRecord(route(`/results/${resultId}`, `/run/result/${resultId}`)),
    getTrace: (traceId) => getRecord(route(`/traces/${traceId}`, `/run/trace/${traceId}`)),
  };
}
