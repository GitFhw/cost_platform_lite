import type { InjectionKey } from "vue";

export type CostLiteRecord = Record<string, any>;

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

export interface CostLiteApi {
  health(): Promise<CostLiteRecord>;
  bootstrap(): Promise<CostLiteRecord>;

  listScenes(params?: CostLiteRecord): Promise<CostLitePage>;
  getScene(sceneId: number | string): Promise<CostLiteRecord>;
  createScene(data: CostLiteRecord): Promise<unknown>;
  updateScene(data: CostLiteRecord): Promise<unknown>;
  deleteScenes(sceneIds: Array<number | string>): Promise<unknown>;

  listFees(sceneId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  getFee(feeId: number | string): Promise<CostLiteRecord>;
  getFeeGovernance(feeId: number | string): Promise<CostLiteRecord>;
  createFee(data: CostLiteRecord): Promise<unknown>;
  updateFee(data: CostLiteRecord): Promise<unknown>;
  deleteFees(feeIds: Array<number | string>): Promise<unknown>;

  listVariables(sceneId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  getVariable(variableId: number | string): Promise<CostLiteRecord>;
  createVariable(data: CostLiteRecord): Promise<unknown>;
  updateVariable(data: CostLiteRecord): Promise<unknown>;
  deleteVariables(variableIds: Array<number | string>): Promise<unknown>;

  listVariableGroups(sceneId: number | string): Promise<CostLiteRecord[]>;
  createVariableGroup(data: CostLiteRecord): Promise<unknown>;
  updateVariableGroup(data: CostLiteRecord): Promise<unknown>;
  deleteVariableGroups(groupIds: Array<number | string>): Promise<unknown>;

  listRules(sceneId: number | string, feeId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  getRule(ruleId: number | string): Promise<CostLiteRecord>;
  createRule(data: CostLiteRecord): Promise<unknown>;
  updateRule(data: CostLiteRecord): Promise<unknown>;
  deleteRules(ruleIds: Array<number | string>): Promise<unknown>;
  previewRule(data: CostLiteRecord): Promise<CostLiteRecord>;
  previewRuleConflict(data: CostLiteRecord): Promise<CostLiteRecord[]>;

  listFormulaOptions(sceneId: number | string): Promise<CostLiteRecord[]>;
  listVersions(sceneId: number | string, params?: CostLiteRecord): Promise<CostLitePage>;
  precheckVersion(sceneId: number | string): Promise<CostLiteRecord>;
  createVersion(data: CostLiteRecord): Promise<CostLiteRecord>;
  activateVersion(versionId: number | string): Promise<unknown>;

  getInputTemplate(sceneId: number | string, feeIds?: Array<number | string>): Promise<CostLiteRecord>;
  calculate(data: CostLiteRecord): Promise<CostLiteRecord>;
  executeSimulation(data: CostLiteRecord): Promise<CostLiteRecord>;
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
  options: { basePath?: string } = {},
): CostLiteApi {
  const basePath = options.basePath || "/cost-lite";

  const request = (method: CostLiteRequest["method"], path: string, params?: CostLiteRecord, data?: unknown) =>
    transport({ method, url: joinPath(basePath, path), params: cleanParams(params), data });

  const getRecord = async (path: string, params?: CostLiteRecord) => recordOf(await request("GET", path, params));
  const getArray = async (path: string, params?: CostLiteRecord) => arrayOf(await request("GET", path, params));
  const getPage = async (path: string, params?: CostLiteRecord) => pageOf(await request("GET", path, params));
  const send = async (method: CostLiteRequest["method"], path: string, data?: unknown) =>
    payloadOf(await request(method, path, undefined, data));

  return {
    health: () => getRecord("/health"),
    bootstrap: () => getRecord("/bootstrap"),

    listScenes: (params) => getPage("/scenes", params),
    getScene: (sceneId) => getRecord(`/scenes/${sceneId}`),
    createScene: (data) => send("POST", "/scenes", data),
    updateScene: (data) => send("PUT", "/scenes", data),
    deleteScenes: (sceneIds) => send("DELETE", `/scenes/${sceneIds.join(",")}`),

    listFees: (sceneId, params) => getPage(`/scenes/${sceneId}/fees`, params),
    getFee: (feeId) => getRecord(`/fees/${feeId}`),
    getFeeGovernance: (feeId) => getRecord(`/fees/${feeId}/governance`),
    createFee: (data) => send("POST", "/fees", data),
    updateFee: (data) => send("PUT", "/fees", data),
    deleteFees: (feeIds) => send("DELETE", `/fees/${feeIds.join(",")}`),

    listVariables: (sceneId, params) => getPage(`/scenes/${sceneId}/variables`, params),
    getVariable: (variableId) => getRecord(`/variables/${variableId}`),
    createVariable: (data) => send("POST", "/variables", data),
    updateVariable: (data) => send("PUT", "/variables", data),
    deleteVariables: (variableIds) => send("DELETE", `/variables/${variableIds.join(",")}`),

    listVariableGroups: (sceneId) => getArray("/variable-groups", { sceneId }),
    createVariableGroup: (data) => send("POST", "/variable-groups", data),
    updateVariableGroup: (data) => send("PUT", "/variable-groups", data),
    deleteVariableGroups: (groupIds) => send("DELETE", `/variable-groups/${groupIds.join(",")}`),

    listRules: (sceneId, feeId, params) => getPage("/rules", { ...params, sceneId, feeId }),
    getRule: (ruleId) => getRecord(`/rules/${ruleId}`),
    createRule: (data) => send("POST", "/rules", data),
    updateRule: (data) => send("PUT", "/rules", data),
    deleteRules: (ruleIds) => send("DELETE", `/rules/${ruleIds.join(",")}`),
    previewRule: async (data) => recordOf(await request("POST", "/rules/tier-preview", undefined, data)),
    previewRuleConflict: async (data) => arrayOf(await request("POST", "/rules/conflict-preview", undefined, data)),

    listFormulaOptions: (sceneId) => getArray("/formulas/options", { sceneId }),
    listVersions: (sceneId, params) => getPage("/versions", { ...params, sceneId }),
    precheckVersion: (sceneId) => getRecord(`/versions/precheck/${sceneId}`),
    createVersion: async (data) => recordOf(await request("POST", "/versions", undefined, data)),
    activateVersion: (versionId) => send("PUT", `/versions/${versionId}/activate`),

    getInputTemplate: (sceneId, feeIds) => getRecord("/input-template", {
      sceneId,
      feeIds: feeIds?.join(","),
    }),
    calculate: async (data) => recordOf(await request("POST", "/calculate", undefined, data)),
    executeSimulation: async (data) => recordOf(await request("POST", "/simulations", undefined, data)),
    submitTask: async (data) => recordOf(await request("POST", "/tasks", undefined, data)),

    listBillingLogs: (params) => getPage("/logs", params),
    getBillingLog: (simulationId) => getRecord(`/logs/${simulationId}`),
    listResults: (params) => getPage("/results", params),
    getResult: (resultId) => getRecord(`/results/${resultId}`),
    getTrace: (traceId) => getRecord(`/traces/${traceId}`),
  };
}
