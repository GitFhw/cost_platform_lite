# Oracle 轻量计费接口文档

本文档对应 `Oracle/runtime/cost-lite-server-1.0.0.jar`。Starter 代理和独立 Jar 使用同一套计费接口语义，宿主代理默认也使用 `/cost/**`，Starter 会把宿主请求转发到 Jar 的同名上游路径。宿主已有路径冲突时，只需通过 `web-path` 和前端 `basePath` 各调整一次。

| 用途 | Starter 宿主入口 | 独立 Jar 入口 |
| --- | --- | --- |
| 管理/工作台 | `/cost/**` | `/cost/**` |
| 开放业务调用 | `/cost/open/**` | `/cost/open/**` |

嵌入业务项目时，优先使用管理/集成链路：业务项目引入 Starter 后由宿主权限保护 `/cost/**`，Starter 使用配置中的 `admin-token` 调用 Jar；不需要创建 `openApp`。`/cost/open/**` 只是可选的外部应用令牌模式，本教程的集成验证可以完全跳过。

## 1. 调用边界

- 工作台只做配置维护、版本发布、真实业务数据试算和试算日志查看。
- 试算不创建正式任务、不写正式结果台账；结果保存在 `cost_simulation_record.result_json`。
- 业务后端同步计费使用受保护的管理/集成路径，立即返回金额并保存 `billingLogId`；OpenApp 令牌模式仅作为可选兼容能力保留。
- 受保护的批量试算只用于配置联调，每条输入写入一条 `cost_simulation_record`，不创建正式任务和正式结果台账。
- 正式任务和正式结果查询属于受保护的业务后端内部接口，不属于工作台，也不应把管理令牌下发给浏览器。
- 数据库账号、开放应用密钥和访问令牌不能放进浏览器。

## 2. 换取开放令牌

开放应用由平台管理员在管理侧创建并限定场景。创建应用时保存返回的 `appSecret`，密钥只在创建或重置时明文返回一次。

### 2.1 创建开放应用

Starter：`POST /cost/open-apps`；独立 Jar：`POST /cost/openApp`。

```json
{
  "appCode": "ERP_ORDER_COST",
  "appName": "ERP 订单计费",
  "sceneScopeType": "LIST",
  "sceneIds": [1],
  "allowDraftSnapshot": false,
  "tokenTtlSeconds": 7200,
  "status": "0",
  "remark": "订单服务端调用"
}
```

### 2.2 申请短期令牌

Starter：`POST /cost/open/token`；独立 Jar：`POST /cost/open/auth/token`。

```bash
curl -X POST http://127.0.0.1:18082/cost/open/auth/token \
  -H "Content-Type: application/json" \
  -d '{"appCode":"ERP_ORDER_COST","appSecret":"部署时注入的应用密钥"}'
```

后续请求使用以下任一请求头：

```text
X-Cost-Open-Token: accessToken
Authorization: Bearer accessToken
```

令牌只在业务后端内存或安全缓存中使用，不要下发给浏览器。

## 3. 场景级发现

业务系统启动或配置缓存刷新时调用一次，不要在每笔交易中读取配置表。

```bash
# 查询授权场景
curl -H "X-Cost-Open-Token: $TOKEN" \
  http://127.0.0.1:18082/cost/open/scenes

# 查询场景版本
curl -H "X-Cost-Open-Token: $TOKEN" \
  http://127.0.0.1:18082/cost/open/scenes/1/versions

# 查询生效版本下的费目
curl -H "X-Cost-Open-Token: $TOKEN" \
  "http://127.0.0.1:18082/cost/open/scenes/1/fees?versionId=1&snapshotMode=ACTIVE"
```

生产调用固定使用 `snapshotMode=ACTIVE` 和当前生效的 `versionId`。业务系统只保存场景、版本和费目 ID，不读取或拼接 `cost_*` 表 SQL。

## 4. 获取输入模板

场景全费目模板：

```bash
curl -H "X-Cost-Open-Token: $TOKEN" \
  "http://127.0.0.1:18082/cost/open/fee-template?sceneId=1&versionId=1&taskType=FORMAL_SINGLE&snapshotMode=ACTIVE"
```

指定费目模板：

```bash
curl -H "X-Cost-Open-Token: $TOKEN" \
  "http://127.0.0.1:18082/cost/open/fee-template?sceneId=1&versionId=1&feeId=1&taskType=FORMAL_SINGLE&snapshotMode=ACTIVE"
```

模板响应中的 `inputJson` 是示例请求数据。将其中示例值替换为业务真实值，保留字段路径和业务编码。

## 5. 同步计费

### 5.1 场景全费目

Starter：`POST /cost/open/calculate`；独立 Jar：`POST /cost/open/fee/calculate`。

不传 `feeId`、`feeIds`、`feeCode` 时，按场景生效版本计算全部费目：

```bash
curl -X POST http://127.0.0.1:18082/cost/open/fee/calculate \
  -H "Content-Type: application/json" \
  -H "X-Cost-Open-Token: $TOKEN" \
  -d '{
    "sceneId": 1,
    "versionId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": false
  }'
```

### 5.2 指定单个费目

传入 `feeId` 后只关注该费目；如果该费目依赖其他费目，核心会先执行依赖链，但返回记录会标出 `targetFeeCodes` 和 `dependentFeeCodes`：

```bash
curl -X POST http://127.0.0.1:18082/cost/open/fee/calculate \
  -H "Content-Type: application/json" \
  -H "X-Cost-Open-Token: $TOKEN" \
  -d '{
    "sceneId": 1,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": true
  }'
```

`feeIds` 是数字数组，`feeCode` 是费目编码。三者可单独使用，服务端会合并去重；推荐使用稳定的 `feeId`。

### 5.3 请求参数

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `sceneId` | 是 | 场景主键。 |
| `versionId` | 生产必填 | 生效版本主键；不建议业务系统省略。 |
| `feeId` | 否 | 指定一个费目。省略表示场景全费目。 |
| `feeIds` | 否 | 指定多个费目。 |
| `feeCode` | 否 | 按费目编码指定范围。 |
| `billMonth` | 否 | 账期，格式 `yyyy-MM`。 |
| `inputJson` | 是 | JSON 字符串，不是嵌套 JSON 对象。字段以费目输入模板为准。 |
| `includeExplain` | 否 | 是否返回规则、要素和定价解释；联调建议 `true`。 |

### 5.4 成功响应示例

```json
{
  "code": 200,
  "data": {
    "sceneId": 1,
    "versionId": 1,
    "targetFeeCodes": ["ORACLE_E2E_FEE"],
    "dependentFeeCodes": [],
    "records": [
      {
        "feeId": 1,
        "feeCode": "ORACLE_E2E_FEE",
        "feeName": "订单费用",
        "amountValue": 20.00,
        "ruleCode": "ORACLE_E2E_RATE"
      }
    ],
    "successCount": 1,
    "noMatchCount": 0,
    "failedCount": 0,
    "billingLogId": 10001,
    "billingLogNo": "CALC-202609030001",
    "billingLogStatus": "SUCCESS"
  }
}
```

格式错误、变量缺失、规则执行异常等失败请求也会尽量写入试算/调用日志，并在错误响应中返回 `billingLogId`。业务系统应保存自己的 `bizNo` 与该编号的关联关系。

## 6. 工作台试算接口

这组接口供工作台或受保护的管理端使用，不使用开放令牌。Starter：`POST /cost/simulations`；独立 Jar：`POST /cost/run/simulation/execute`。

请求体与同步计费相同，额外支持 `feeId`、`feeIds`、`feeCode` 指定试算范围，并支持 `includeExplain`：

```json
{
  "sceneId": 1,
  "versionId": 1,
  "feeId": 1,
  "billMonth": "2026-09",
  "inputJson": "{\"bizNo\":\"REAL-ORDER-001\",\"objectCode\":\"REAL-ORDER-001\",\"quantity\":8}",
  "includeExplain": true
}
```

不传费目范围时按场景全部费目试算。响应包含 `record`、`input`、`variables`、`explain` 和 `result`；其中 `result` 是本次试算结果，日志详情可通过以下接口再次读取：

```bash
curl -H "X-Cost-Lite-Token: $ADMIN_TOKEN" \
  "http://127.0.0.1:18082/cost/lite/billing-log/list?sceneId=1&pageNum=1&pageSize=20"
curl -H "X-Cost-Lite-Token: $ADMIN_TOKEN" \
  "http://127.0.0.1:18082/cost/lite/billing-log/10001"
```

Starter 代理对应路径为 `/cost/logs` 和 `/cost/logs/{simulationId}`。

### 6.1 批量试算

批量试算仍然走管理/集成链路，不依赖 `openApp`。Starter：`POST /cost/simulations/batch`；独立 Jar：`POST /cost/run/simulation/batch-execute`。Starter 的上游管理令牌由 Starter 配置注入，独立 Jar 直接使用 `X-Cost-Lite-Token` 或 `Authorization: Bearer`：

```bash
curl -X POST http://127.0.0.1:18082/cost/run/simulation/batch-execute \
  -H "Content-Type: application/json" \
  -H "X-Cost-Lite-Token: $ADMIN_TOKEN" \
  -d '{
    "sceneId": 1,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "[{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8},{\"bizNo\":\"ORDER-002\",\"objectCode\":\"ORDER-002\",\"quantity\":3}]",
    "includeExplain": true
  }'
```

`inputJson` 必须是非空对象数组，`bizNo` 在同一批次内必须唯一。服务端逐条执行并逐条落 `cost_simulation_record`，成功和失败都会保留；响应返回 `totalCount`、`successCount`、`failedCount`，以及每条记录的摘要字段和单条详情同名的 `input`、`variables`、`explain`、`result` 节点。金额从既有 `result.amountTotal` 读取，费目明细从既有 `result.feeResults` 读取。批量试算不会写入 `cost_calc_task`、`cost_result_ledger` 或 `cost_result_trace`。

## 7. Java 后端调用示例

以下示例使用 Spring `RestTemplate`，应放在业务后端服务中，不要放在浏览器：

```java
String adminToken = System.getenv("COST_LITE_ADMIN_TOKEN");
Map<String, Object> body = new LinkedHashMap<>();
body.put("sceneId", 1L);
body.put("versionId", 1L);
body.put("feeId", 1L);
body.put("billMonth", "2026-09");
body.put("inputJson", objectMapper.writeValueAsString(orderInput));
body.put("includeExplain", false);

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("X-Cost-Lite-Token", adminToken);
HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

ResponseEntity<Map> response = restTemplate.postForEntity(
    costLiteBaseUrl + "/cost/run/fee/calculate", request, Map.class);
Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
Object billingLogId = data.get("billingLogId");
```

Starter 代理模式使用 `costLiteBaseUrl + "/cost/calculate"`，并由 Starter 注入上游管理令牌；独立 Jar 模式使用 `costLiteBaseUrl + "/cost/run/fee/calculate"` 和 `X-Cost-Lite-Token`。批量试算把路径替换为 `/cost/simulations/batch` 或 `/cost/run/simulation/batch-execute`，并把 `inputJson` 改成对象数组字符串。建议在业务服务中设置连接超时、读取超时、幂等业务号和失败重试策略；不要把失败重试做成无限重试。

## 8. 业务后端正式核算路径

正式核算可以保留给业务系统后端或受保护的内部运维服务使用，但不属于工作台“接口示例”，也不开放给浏览器、批量试算或 OpenApp 令牌。Starter 代理和独立 Jar 的具体路径如下：

| 能力 | Starter 代理 | 独立 Jar |
| --- | --- | --- |
| 正式任务预检查 | `POST /cost/tasks/precheck` | `POST /cost/run/task/precheck` |
| 提交正式任务 | `POST /cost/tasks` | `POST /cost/run/task/submit` |
| 查询任务 | `GET /cost/tasks/{taskId}` | `GET /cost/run/task/{taskId}` |
| 结果分页 | `GET /cost/results` | `GET /cost/run/result/list` |
| 结果详情 | `GET /cost/results/{resultId}` | `GET /cost/run/result/{resultId}` |
| 规则/变量追溯 | `GET /cost/traces/{traceId}` | `GET /cost/run/trace/{traceId}` |

正式单笔请求示例（字段以 `CostCalcTaskSubmitBo` 为准）：

```json
{
  "sceneId": 1,
  "versionId": 1,
  "taskType": "FORMAL_SINGLE",
  "billMonth": "2026-09",
  "requestNo": "ERP-ORDER-20260903-0001",
  "inputSourceType": "INLINE_JSON",
  "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
  "remark": "业务后端正式核算"
}
```

调用前应先执行预检查并使用业务侧幂等号；正式任务会写入 `cost_calc_task`、任务明细/分片、`cost_result_ledger` 和 `cost_result_trace`，与工作台试算的 `cost_simulation_record` 完全区分。是否开放该链路由宿主权限和业务流程决定。

## 9. 明确不提供的接口

OpenApp 路由没有正式任务提交、试算日志查询或正式结果查询；但集成项目可以直接使用上节列出的受保护管理路径。工作台仍不展示正式任务和正式结果页面，Starter/独立 Jar 的管理令牌只能由服务端保存。

## 10. 高基数要素和规则组合

货种、客户、船舶等数据量较大时，不要把全量数据初始化到 `sys_dict_data`，也不要在 `cost_*` 表复制业务主数据。计费输入只传业务编码，例如 `goodsCode`；如果规则需要业务系统补充分类，就由业务系统在调用计费核心前查询并同时传入 `goodsCategory`。当前轻量 Jar 的运行态 `REMOTE` 要素只解析请求中的 `remoteContext`、`remotePayload` 或 `remoteData`，不会在每笔计费时根据 `remoteApi` 自动发起外部 HTTP；`remoteApi` 目前用于连接测试、数据预览和保留母体配置兼容性。后续要做真正的实时自动取数，应由宿主先补齐远程上下文，或单独接入可插拔运行时取数适配器，并明确超时、缓存、失败兜底和批量合并策略。

配置人员需要选择货种时，宿主页面提供一个受权限保护的分页搜索接口，例如 `GET /business/goods/options?keyword=钢&pageNum=1&pageSize=20&value=G001`，返回 `{ "rows": [{ "label": "钢材", "value": "G001" }], "total": 1 }`。工作台只保存编码到规则比较值，不直接访问客户数据库；当前轻量工作台保留文本输入作为无额外开发的默认路径，宿主可以在页面包装层把这个接口接成远程搜索下拉。运行时仍只提交编码和必要的低基数分类，不提交下拉全量数据。

规则不要按所有维度做笛卡尔积。当前核心是同一费目按规则优先级从高到低命中第一条，规则组内条件为 AND，组间由 `conditionLogic` 决定；推荐先做“特殊组合覆盖 -> 单维度兜底 -> 无条件基础价”，例如货种分类 + 内外贸 + 进出口只在确实影响价格时组合，货种编码本身优先归类后再参与规则。若必须维护数万条货种与价格映射，应新增可插拔的业务侧价格/分类查询适配器，不把映射硬塞进规则文本。

首轮 Oracle 集成测试建议使用 `INPUT` 要素传入 `goodsCode`、`goodsCategory`、`tradeMode`、`direction` 和 `quantity`，先验证单费目规则命中、兜底和金额结果；高基数远程下拉及按 `remoteApi` 自动实时取数不作为首轮验收项。
