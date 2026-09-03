# MySQL 轻量计费接口文档

本文档对应 `Mysql/runtime/cost-lite-server-1.0.0.jar`。Starter 代理和独立 Jar 的业务调用语义一致，区别只有 URL 前缀：

| 用途 | Starter 代理 | 独立 Jar |
| --- | --- | --- |
| 管理/工作台 | `/cost-lite/**` | `/cost/**` |
| 开放业务调用 | `/cost-lite/open/**` | `/cost/open/**` |

## 1. 调用边界

- 工作台只做配置维护、版本发布、真实业务数据试算和试算日志查看。
- 试算不创建正式任务、不写正式结果台账；结果保存在 `cost_simulation_record.result_json`。
- 开放接口只给业务系统后端做同步计费，立即返回金额并保存 `billingLogId`。
- 开放接口不提供正式任务提交、试算日志查询或正式结果管理查询。
- 数据库账号、开放应用密钥和访问令牌不能放进浏览器。

## 2. 换取开放令牌

开放应用由平台管理员在管理侧创建并限定场景。创建应用时保存返回的 `appSecret`，密钥只在创建或重置时明文返回一次。

### 2.1 创建开放应用

Starter：`POST /cost-lite/open-apps`；独立 Jar：`POST /cost/openApp`。

```json
{
  "appCode": "ERP_ORDER_COST",
  "appName": "ERP 订单计费",
  "sceneScopeType": "LIST",
  "sceneIds": [4],
  "allowDraftSnapshot": false,
  "tokenTtlSeconds": 7200,
  "status": "0",
  "remark": "订单服务端调用"
}
```

### 2.2 申请短期令牌

Starter：`POST /cost-lite/open/token`；独立 Jar：`POST /cost/open/auth/token`。

```bash
curl -X POST http://127.0.0.1:8080/cost-lite/open/token \
  -H "Content-Type: application/json" \
  -d '{"appCode":"ERP_ORDER_COST","appSecret":"部署时注入的应用密钥"}'
```

响应中的 `accessToken` 只在业务后端内存或安全缓存中使用。后续请求使用以下任一请求头：

```text
X-Cost-Open-Token: accessToken
Authorization: Bearer accessToken
```

## 3. 场景级发现

业务系统启动或配置缓存刷新时调用一次，不要在每笔交易中读取配置表。

```bash
# 查询授权场景
curl -H "X-Cost-Open-Token: $TOKEN" \
  http://127.0.0.1:8080/cost-lite/open/scenes

# 查询场景版本
curl -H "X-Cost-Open-Token: $TOKEN" \
  http://127.0.0.1:8080/cost-lite/open/scenes/4/versions

# 查询生效版本下的费目
curl -H "X-Cost-Open-Token: $TOKEN" \
  "http://127.0.0.1:8080/cost-lite/open/scenes/4/fees?versionId=1&snapshotMode=ACTIVE"
```

生产调用固定使用 `snapshotMode=ACTIVE` 和当前生效的 `versionId`。业务系统只保存场景、版本和费目 ID，不读取或拼接 `cost_*` 表 SQL。

## 4. 获取输入模板

场景全费目模板：

```bash
curl -H "X-Cost-Open-Token: $TOKEN" \
  "http://127.0.0.1:8080/cost-lite/open/template?sceneId=4&versionId=1&taskType=FORMAL_SINGLE&snapshotMode=ACTIVE"
```

指定费目模板：

```bash
curl -H "X-Cost-Open-Token: $TOKEN" \
  "http://127.0.0.1:8080/cost-lite/open/template?sceneId=4&versionId=1&feeId=1&taskType=FORMAL_SINGLE&snapshotMode=ACTIVE"
```

模板响应中的 `inputJson` 是示例请求数据。将其中示例值替换为业务真实值，保留字段路径和业务编码。

## 5. 同步计费

### 5.1 场景全费目

Starter：`POST /cost-lite/open/calculate`；独立 Jar：`POST /cost/open/fee/calculate`。

不传 `feeId`、`feeIds`、`feeCode` 时，按场景生效版本计算全部费目：

```bash
curl -X POST http://127.0.0.1:8080/cost-lite/open/calculate \
  -H "Content-Type: application/json" \
  -H "X-Cost-Open-Token: $TOKEN" \
  -d '{
    "sceneId": 4,
    "versionId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": false
  }'
```

### 5.2 指定单个费目

传入 `feeId` 后只关注该费目；如果该费目依赖其他费目，核心会先执行依赖链，但返回记录会标出 `targetFeeCodes` 和 `dependentFeeCodes`：

```bash
curl -X POST http://127.0.0.1:8080/cost-lite/open/calculate \
  -H "Content-Type: application/json" \
  -H "X-Cost-Open-Token: $TOKEN" \
  -d '{
    "sceneId": 4,
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
| `includeExplain` | 否 | 是否返回规则、要素和定价解释；联调建议 `true`，生产可按响应大小设为 `false`。 |

### 5.4 成功响应示例

```json
{
  "code": 200,
  "data": {
    "sceneId": 4,
    "versionId": 1,
    "targetFeeCodes": ["MYSQL_E2E_FEE"],
    "dependentFeeCodes": [],
    "records": [
      {
        "feeId": 1,
        "feeCode": "MYSQL_E2E_FEE",
        "feeName": "订单费用",
        "amountValue": 20.00,
        "ruleCode": "MYSQL_E2E_RATE"
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

这组接口供工作台或受保护的管理端使用，不使用开放令牌。Starter：`POST /cost-lite/simulations`；独立 Jar：`POST /cost/run/simulation/execute`。

请求体与同步计费相同，额外支持 `feeId`、`feeIds`、`feeCode` 指定试算范围，并支持 `includeExplain`：

```json
{
  "sceneId": 4,
  "versionId": 1,
  "feeId": 1,
  "billMonth": "2026-09",
  "inputJson": "{\"bizNo\":\"REAL-ORDER-001\",\"objectCode\":\"REAL-ORDER-001\",\"quantity\":8}",
  "includeExplain": true
}
```

不传费目范围时按场景全部费目试算。响应包含 `record`、`input`、`variables`、`explain` 和 `result`；其中 `result` 是本次试算结果，日志详情可通过以下接口再次读取：

```bash
curl -H "X-Cost-Lite-Admin-Token: $ADMIN_TOKEN" \
  "http://127.0.0.1:8080/cost-lite/logs?sceneId=4&pageNum=1&pageSize=20"
curl -H "X-Cost-Lite-Admin-Token: $ADMIN_TOKEN" \
  "http://127.0.0.1:8080/cost-lite/logs/10001"
```

独立 Jar 对应路径为 `/cost/lite/billing-log/list` 和 `/cost/lite/billing-log/{simulationId}`。

## 7. Java 后端调用示例

以下示例使用 Spring `RestTemplate`，应放在业务后端服务中，不要放在浏览器：

```java
Map<String, Object> body = new LinkedHashMap<>();
body.put("sceneId", 4L);
body.put("versionId", 1L);
body.put("feeId", 1L);
body.put("billMonth", "2026-09");
body.put("inputJson", objectMapper.writeValueAsString(orderInput));
body.put("includeExplain", false);

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("X-Cost-Open-Token", openToken);
HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

ResponseEntity<Map> response = restTemplate.postForEntity(
    costLiteBaseUrl + "/cost-lite/open/calculate", request, Map.class);
Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
Object billingLogId = data.get("billingLogId");
```

独立 Jar 模式只替换为 `costLiteBaseUrl + "/cost/open/fee/calculate"`。建议在业务服务中设置连接超时、读取超时、幂等业务号和失败重试策略；不要把失败重试做成无限重试。

## 8. 明确不提供的接口

开放路由没有 `/open/task`、开放试算日志查询或开放正式结果查询。`/cost-lite/tasks`、`/cost/run/task/submit` 等正式任务能力属于受保护的内部后端扩展，不是本工作台功能，也不能把管理 Token 下发给浏览器。
