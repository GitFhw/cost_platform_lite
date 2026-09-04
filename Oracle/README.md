# Oracle 轻量计费集成教程

接口字段、场景级调用、指定费目调用和 Java/cURL 示例见：[Oracle/API.md](API.md)。

本目录是可直接交付给第三方业务系统的 Oracle 集成包。Java 8 + Spring Boot 2.7 Servlet 项目推荐引入 `cost-lite-starter-oracle` 同进程接入；传统 SSM 或需要进程隔离的项目可使用独立 Jar + HTTP 代理。`source/` 保存本仓库维护用的 Oracle 计费核心源码；客户迁移只使用 Starter/Jar、前端、配置和 SQL，不需要复制源码。同进程说明见 [EMBEDDED.md](EMBEDDED.md)。

## 1. 交付内容

```text
Oracle/
├─ source/                              Oracle 计费核心源码和独立构建入口
│  ├─ src/
│  ├─ pom.xml
│  └─ README.md
├─ backend-integration/
│  ├─ cost-lite-client/                 Java 8 兼容 HTTP Client
│  └─ cost-lite-spring-boot-starter/    可选 Spring Boot 代理 Starter
├─ starter/                              Java 8 + Spring Boot 2.7 同进程 Starter
├─ example/                              同进程集成验证宿主
├─ server/                               独立 Oracle 服务入口
├─ bin/
│  ├─ start-cost-lite.ps1              Windows 启动脚本
│  └─ start-cost-lite.sh                Linux 启动脚本
├─ config/
│  ├─ application-oracle.yml            Jar 运行配置样例
│  ├─ embedded-application.yml          同进程 Starter 宿主配置样例
│  ├─ host-application.yml              宿主系统配置样例
│  └─ runtime.env.example                环境变量样例
├─ runtime/
│  ├─ cost-lite-server-1.0.0.jar        Oracle 运行 Jar
│  └─ SHA256SUMS                         Jar 校验值
├─ sql/
│  └─ cost-lite-schema.sql               母体 cost_* 表 Oracle 建表脚本
└─ README.md
```

Jar 加密暂未纳入本版本。后续如需授权控制，可在不改变宿主接口的前提下增加许可证校验或授权服务。

## 2. 环境要求

- 运行 Jar：Java 8 及以上。
- Oracle：12c 或更高版本，推荐 19c、21c 或 23ai；数据库用户需要 `CREATE SESSION`、建表权限和对应表空间配额。
- 同进程宿主：Java 8 + Spring Boot 2.7 Servlet 应用；传统 SSM 或其他版本使用 HTTP 代理方式。
- 宿主前端：Vue 3、Element Plus。前端工作台接入方式见 `../Front/README.md`。

Oracle 使用 Service Name 连接时，URL 采用：

```text
jdbc:oracle:thin:@//主机:端口/服务名
```

如果现场使用 SID、SCAN、TNS Alias 或钱包连接，不要改表结构，只需通过 `COST_LITE_DB_URL` 覆盖完整 JDBC URL。

## 3. 初始化数据库

### 3.1 独立计费库

建议创建独立 Schema，避免与业务表权限互相影响。以 Oracle 23ai Free 为例，先由 DBA 创建用户和表空间配额，再执行脚本：

```sql
CREATE USER cost_lite IDENTIFIED BY "请替换为真实密码";
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER TO cost_lite;
ALTER USER cost_lite QUOTA UNLIMITED ON USERS;
```

使用 SQL*Plus 或 SQLcl 执行：

```bash
sqlplus cost_lite@//127.0.0.1:1521/FREEPDB1
```

登录后执行：

```sql
@Oracle/sql/cost-lite-schema.sql
```

也可以使用 SQLcl：

```bash
sql Oracle/sql/cost-lite-schema.sql
```

### 3.2 初始化到业务 Schema

轻量包复用母体平台的 `cost_*` 表、字段和业务编码，不创建 `lite_*` 或 `billing_*` 平行实体。把脚本执行到业务用户前，先完成以下检查：

1. 备份业务库并确认变更窗口。
2. 检查目标 Schema 是否已经存在同名 `cost_*` 表。
3. 如果已存在表，先比对字段、类型、约束和索引，禁止直接覆盖生产表。
4. 确认业务系统不会同时加载另一套同名实体映射。

脚本不包含 `DROP TABLE`，建表和索引使用幂等 PL/SQL 块，适合重复执行；它不是用于升级任意历史版本表结构的通用迁移工具。

### 3.3 初始化检查

```sql
SELECT COUNT(*) AS cost_table_count
FROM user_tables
WHERE table_name LIKE 'COST\_%' ESCAPE '\\';
```

本交付脚本包含母体当前冻结模型的 26 张 `cost_*` 表、相关二级索引，以及工作台实际需要的 `sys_dict_type`、`sys_dict_data` 两张字典表。字典只初始化计费页面需要的类型和值，不迁移其他 RuoYi `sys_*` 表、账单表或客户业务表；若业务库已有同名母体字典表，脚本会复用现有表并只补充缺失的计费字典值。

## 4. 启动 Oracle 运行 Jar

### 4.1 Windows PowerShell

先设置环境变量。密码只放在本机环境变量、密钥管理系统或进程管理器中，不写入脚本和 Git：

```powershell
$env:COST_LITE_DB_HOST = "127.0.0.1"
$env:COST_LITE_DB_PORT = "1521"
$env:COST_LITE_DB_SERVICE = "FREEPDB1"
$env:COST_LITE_DB_USERNAME = "cost_lite"
$env:COST_LITE_DB_PASSWORD = "请替换为真实密码"
$env:COST_LITE_SERVER_PORT = "18082"
$env:COST_LITE_LOG_PATH = "D:\\apps\\cost-lite\\logs"

.\Oracle\bin\start-cost-lite.ps1
```

现场如果需要自定义 URL，优先设置它，脚本不会重新拼接：

```powershell
$env:COST_LITE_DB_URL = "jdbc:oracle:thin:@//db.example.com:1521/APPPRD"
.\Oracle\bin\start-cost-lite.ps1 -JarPath "D:\apps\cost-lite-server-1.0.0.jar"
```

### 4.2 Linux

```bash
export COST_LITE_DB_HOST=127.0.0.1
export COST_LITE_DB_PORT=1521
export COST_LITE_DB_SERVICE=FREEPDB1
export COST_LITE_DB_USERNAME=cost_lite
export COST_LITE_DB_PASSWORD='请替换为真实密码'
export COST_LITE_SERVER_PORT=18082
export COST_LITE_LOG_PATH=/opt/cost-lite/logs

bash Oracle/bin/start-cost-lite.sh
```

脚本也兼容 `COST_LITE_DB_NAME` 作为服务名别名。生产环境建议交给 systemd、容器平台或现有进程管理器托管。

### 4.3 使用环境样例

复制 `Oracle/config/runtime.env.example` 到部署目录后逐项替换占位值，再由部署平台加载。不要把真实密码、Token、钱包文件路径或客户数据提交到 Git。

### 4.4 在本仓库重新构建 Oracle Jar

只有修改计费核心、公式引擎或 Oracle 方言兼容逻辑时才执行本节。客户业务项目不要复制 `Oracle/source/`，同进程项目引用 `cost-lite-starter-oracle`，独立部署项目使用 `Oracle/runtime/` 中的已验证 Jar。

```bash
mvn -f Oracle/pom.xml clean install -DskipTests
```

完整回归通过后，把 `Oracle/server/target/cost-lite-server.jar` 发布为 `Oracle/runtime/cost-lite-server-1.0.0.jar`，再重新计算 `Oracle/runtime/SHA256SUMS`。`target/` 和其中的 `.class` 只属于本机构建目录，不提交 Git。

## 5. 验证运行服务

```bash
curl http://127.0.0.1:18082/cost/lite/health
```

正常响应至少应包含：

```json
{
  "code": 200,
  "data": {
    "service": "UP",
    "database": "UP",
    "databaseProduct": "Oracle"
  }
}
```

进一步查看运行能力：

```bash
curl http://127.0.0.1:18082/cost/lite/bootstrap
```

## 6. 宿主后端接入方式

### 6.1 同进程 Starter

Java 8 + Spring Boot 2.7 Servlet 项目推荐依赖 `com.costplatform.lite:cost-lite-starter-oracle`。它会自动注册计费入口并使用 `cost.lite.datasource.*` 建立命名的 Oracle 计费数据源，不启动独立 Jar。完整配置见 [EMBEDDED.md](EMBEDDED.md)。

```xml
<dependency>
    <groupId>com.costplatform.lite</groupId>
    <artifactId>cost-lite-starter-oracle</artifactId>
    <version>${cost-lite.version}</version>
</dependency>
```

嵌入模式前端调用母体兼容路径，使用 `routeMode: "runtime"`；宿主继续负责登录、菜单、权限和 CORS。

### 6.2 独立 Jar + HTTP

业务系统不需要复制母体 Controller、Service、Mapper 或实体，只需通过网关或同源反向代理访问 Jar 的 `/cost/**` 路由。前端适配器使用 `routeMode: "runtime"`。

### 6.3 Spring Boot Starter 代理

在 `Oracle/backend-integration` 目录构建：

```bash
mvn -f Oracle/backend-integration/pom.xml clean install
```

宿主 `pom.xml` 增加：

```xml
<properties>
    <cost-lite.version>1.0.0</cost-lite.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.costplatform</groupId>
        <artifactId>cost-lite-spring-boot-starter</artifactId>
        <version>${cost-lite.version}</version>
    </dependency>
</dependencies>
```

将 `Oracle/config/host-application.yml` 合并到宿主配置：

```yaml
cost:
  lite:
    integration:
      enabled: true
      base-url: ${COST_LITE_BASE_URL:http://127.0.0.1:18082}
      admin-token: ${COST_LITE_ADMIN_TOKEN:}
      open-token: ${COST_LITE_OPEN_TOKEN:}
      web-path: /cost
      proxy-enabled: true
      connect-timeout: 5000
      read-timeout: 30000
      max-retries: 0
```

Starter 只负责稳定代理和响应适配，不接管宿主登录、角色和菜单权限。宿主按自己的安全框架保护 `/cost/**`，因此 RuoYi、Spring Cloud 或其他成熟框架都可以接入。

宿主启动后检查：

```bash
curl http://127.0.0.1:8080/cost/health
```

## 7. 前端接入

将 `../Front/src/CostLiteWorkbench.vue` 复制或作为组件依赖引入，在宿主路由增加一个菜单即可。HTTP 代理模式：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost", routeMode: "proxy" },
);
```

独立 Jar 直连模式：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost", routeMode: "runtime" },
);
```

如果业务网关前缀为 `/business/cost`，只调整 `basePath`，不需要逐个改动场景、费目、要素、规则、版本和试算日志接口。菜单清单和完整页面说明见 `../Front/menu-manifest.yml` 与 `../Front/README.md`。

## 8. 最快一日接通顺序

工作台只有一个维护模式，面向计费实施人员或技术维护人员，不要求业务人员承担计费配置职责。建议按以下顺序操作：

1. 执行 Oracle DDL，启动 Jar，确认健康检查为 `UP`。
2. 新增场景，维护场景编码、名称、业务域和对象维度。
3. 在场景下新增费目。
4. 在场景下新增要素，至少配置一个 `INPUT` 来源的数量要素。
5. 在“公式”页按场景维护可复用公式，先执行在线试算；在费目下新增规则，先使用固定费率或固定金额跑通链路，再按需要使用阶梯或公式。
6. 执行发布前检查，创建版本并立即生效。
7. 用一条真实业务数据执行试算，检查试算结果预览和试算日志。
8. 点击工作台“接口示例”，核对场景级、指定费目的请求体及 Starter、独立 Jar 调用写法。

## 9. 接口验收示例

以下 ID 使用实际创建接口返回值替换。

### 9.1 创建固定费率规则

```bash
curl -X POST http://127.0.0.1:18082/cost/rule \
  -H "Content-Type: application/json" \
  -d '{
    "sceneId": 1,
    "feeId": 1,
    "ruleCode": "DEFAULT_RATE",
    "ruleName": "默认费率",
    "ruleType": "FIXED_RATE",
    "conditionLogic": "AND",
    "priority": 100,
    "quantityVariableCode": "quantity",
    "pricingMode": "TYPED",
    "pricingConfig": {"rateValue": 2.50},
    "status": "0",
    "sortNo": 0,
    "conditions": [],
    "tiers": []
  }'
```

### 9.2 发布并生效

```bash
curl http://127.0.0.1:18082/cost/publish/precheck/1

curl -X POST http://127.0.0.1:18082/cost/publish \
  -H "Content-Type: application/json" \
  -d '{
    "sceneId": 1,
    "publishDesc": "首次发布",
    "activateNow": true
  }'
```

### 9.3 试算与同步计费

工作台试算用于验证配置，不创建正式任务，也不写入正式结果台账；它会把真实输入、要素快照、解释、结果和失败原因保存到 `cost_simulation_record`。同步计费是业务系统后端的生产接口，立即返回金额并保存调用日志，不应从浏览器直接调用。

工作台试算接口如下，`feeId`、`feeIds`、`feeCode` 都可选；不传时按场景全部费目试算：

```bash
curl -X POST http://127.0.0.1:18082/cost/run/simulation/execute \
  -H "Content-Type: application/json" \
  -d '{
    "sceneId": 1,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": true
  }'
```

同步计费供业务后端调用：

```bash
curl -X POST http://127.0.0.1:18082/cost/run/fee/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "sceneId": 1,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": true
  }'
```

成功响应会返回金额和 `billingLogId`；输入格式、版本或变量不正确时会返回错误，同时尽量保留失败日志编号，便于查询 `/cost/run/simulation/**`。

批量试算走受保护的管理/集成链路，不需要创建 `openApp`：

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

`inputJson` 必须是非空对象数组，`bizNo` 在同一批次内必须唯一。每条输入都会保存一条 `cost_simulation_record`，成功和失败都保留；响应中的金额读取每条记录的 `result.amountTotal`，费目明细读取 `result.feeResults`；批量试算不会创建正式任务、正式结果台账或结果追溯。

### 9.4 后端正式任务与结果

正式任务不属于开放接口和工作台操作范围，只给业务系统后端或受保护的内部运维接口调用。正式任务使用 `/cost/run/task/submit`，任务详情使用 `/cost/run/task/{taskId}`，结果台账使用：

```text
GET /cost/run/result/list
GET /cost/run/result/{resultId}
GET /cost/run/trace/{traceId}
```

正式任务会保存任务、明细、结果台账和追溯数据；本工作台不提供这些操作和页面，第三方报表如确有需要再由业务后端或只读服务查询。

### 9.5 场景级业务系统调用

业务项目内嵌集成时，业务后端引入 Starter 并调用稳定的 `/cost/**` 路径；Starter 连接独立 Jar 时使用配置中的 `base-url` 和 `admin-token`，业务代码不接触数据库账号，也不需要创建 `openApp`。独立 Jar 部署则直接调用 `/cost/**` 管理路径。建议缓存“场景 -> 生效版本 -> 费目”的解析结果，交易时只传稳定的 `sceneId`、`versionId`、`feeId` 和输入数据。

| 能力 | Starter 代理 | 独立 Jar |
| --- | --- | --- |
| 查询场景 | `GET /cost/scenes` | `GET /cost/scene/list` |
| 查询场景版本 | `GET /cost/scenes/{sceneId}/versions` | `GET /cost/run/version-options/{sceneId}` |
| 查询场景费目 | `GET /cost/fees?sceneId={sceneId}` | `GET /cost/fee/list?sceneId={sceneId}` |
| 场景级同步计费 | `POST /cost/calculate` | `POST /cost/run/fee/calculate` |
| 批量试算 | `POST /cost/simulations/batch` | `POST /cost/run/simulation/batch-execute` |

如确实需要把计费服务作为跨系统开放 API，再启用下面的开放应用令牌模式；它是可选兼容能力，不是内嵌 Starter 集成的前置条件：

开放接口只提供场景、版本、费目、模板和同步计费，不提供试算日志、正式任务或正式结果管理查询，也没有正式任务提交的开放接口。

```json
{
  "appCode": "ERP_ORDER_COST",
  "appName": "ERP 订单计费",
  "sceneScopeType": "LIST",
  "sceneIds": [1],
  "allowDraftSnapshot": false,
  "tokenTtlSeconds": 7200,
  "status": "0"
}
```

独立 Jar 的开放调用路径：

```bash
# 换取短期 accessToken
curl -X POST http://127.0.0.1:18082/cost/open/auth/token \
  -H "Content-Type: application/json" \
  -d '{"appCode":"ERP_ORDER_COST","appSecret":"创建开放应用时保存的密钥"}'

# 查询授权场景、版本和费目
curl -H "X-Cost-Open-Token: $TOKEN" http://127.0.0.1:18082/cost/open/scenes
curl -H "X-Cost-Open-Token: $TOKEN" http://127.0.0.1:18082/cost/open/scenes/1/versions
curl -H "X-Cost-Open-Token: $TOKEN" \
  "http://127.0.0.1:18082/cost/open/scenes/1/fees?versionId=1&snapshotMode=ACTIVE"
```

Starter 代理下将上面的路径分别替换为 `/cost/open/token`、`/cost/open/scenes`、`/cost/open/scenes/{sceneId}/versions` 和 `/cost/open/scenes/{sceneId}/fees`。令牌也支持 `Authorization: Bearer $TOKEN`；生产业务调用固定使用 `ACTIVE` 正式版本。

### 9.6 指定费目的计算

推荐业务系统缓存“场景 -> 生效版本 -> 费目”的解析结果，交易时传入明确的 `feeId`。需要计算一个场景的全部费目时可以省略 `feeId`，也可以传 `feeIds` 或 `feeCode`。

```bash
# 先获取指定费目的输入模板（内嵌集成）
curl -H "X-Cost-Lite-Token: $ADMIN_TOKEN" \
  "http://127.0.0.1:18082/cost/run/input-template/fee?sceneId=1&versionId=1&feeId=1"

# 再计算指定费目
curl -X POST http://127.0.0.1:18082/cost/run/fee/calculate \
  -H "Content-Type: application/json" \
  -H "X-Cost-Lite-Token: $ADMIN_TOKEN" \
  -d '{
    "sceneId": 1,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": false
  }'
```

上面的路径是内嵌集成管理路径；如启用上一节的 OpenApp 模式，再改用对应的 `/cost/open/**` 路径。返回值包含计费结果和 `billingLogId`；业务系统应保存自己的业务号与该日志编号。错误输入、版本失效或变量缺失也会保存失败日志，平台侧可以据此定位。

### 9.7 试算日志查询

工作台的“试算日志”页查询 `cost_simulation_record`，日志详情同时返回试算输入、要素、解释和试算结果。管理查询使用管理 Token：

```text
GET /cost/lite/billing-log/list?sceneId=1&pageNum=1&pageSize=20
GET /cost/lite/billing-log/{simulationId}
```

Starter 代理对应为 `/cost/logs` 和 `/cost/logs/{simulationId}`。开放令牌不开放管理查询，避免把管理能力下发到浏览器；正式结果查询只属于后端扩展，不属于本工作台。

## 10. 公式与可插拔扩展

- 母体公式实体和公式接口保留。当前工作台在同一页面提供公式维护、中文业务口径、标准表达式、在线试算、版本查看和回退。
- 公式是场景下独立、可复用、可治理的资产；规则负责适用条件和命中，`ruleType=FORMULA` 时必须引用有效的 `amountFormulaCode`。简单场景也可以在规则中直接填写手写表达式。
- 当前不启用“公式费目无规则”。母体执行链按费目下的规则构造，没有规则的费目会被跳过；发布校验、依赖分析、治理和结果追溯也都是规则边界。让公式费目绕过规则会造成可保存但不可执行，不符合企业级设计。
- 如果后续确认需要免规则公式费目，应另做母体核心升级，增加明确的费目计价来源/公式绑定语义，并同步改造快照、依赖、执行、治理和追溯，不用 `ruleType=FORMULA` 偷换概念。
- 定制化新增能力放在插件目录，通过 `COST_LITE_PLUGIN_ENABLED` 和 `COST_LITE_PLUGIN_DIR` 控制；不用的插件可以不部署或关闭。
- 字典差异优先直接维护当前轻量库的 `sys_dict_type`、`sys_dict_data`；数据库仍保存母体统一编码。只有明确复用宿主字典且不迁移字典表时，才使用宿主 YAML 映射或 `CostDictionaryProvider` 扩展。

## 11. 日志、结果与数据边界

- 试算和同步计费成功、失败都写入母体 `cost_simulation_record`；试算结果保存在其中的 `result_json`，供工作台日志详情查看。
- 正式任务写入 `cost_calc_task`、任务明细、分片和输入批次相关表。
- 正式计费数据写入 `cost_result_ledger`。
- 规则命中、变量、条件和定价过程写入 `cost_result_trace`。
- 正式任务和正式结果表是运行端保留的后端扩展能力，不在开放令牌和本工作台范围内。
- 计费日志和结果数据不会因为前端页面关闭而丢失；报表由第三方按需自定义，不修改核心计费表结构。

## 12. 常见问题

### 连接报 ORA-12514 或 ORA-12505

确认使用的是正确的 Service Name 或 SID。优先用 `COST_LITE_DB_URL` 写入现场完整 JDBC URL，不要修改 Jar。

### 报 ORA-00942 或无权限

确认运行用户就是执行 DDL 的 Schema，且拥有表空间配额和建表权限。业务库部署时检查当前连接用户，不要只检查 DBA 用户。

### 查询分页失败

Oracle Jar 内置 MyBatis 方言适配，会将母体 Mapper 使用的 `LIMIT`、函数和批量插入转换为 Oracle 语义。若仍失败，先检查 Jar SHA-256 是否与 `runtime/SHA256SUMS` 一致，再保留脱敏 SQL 日志反馈。

### 宿主没有 `/cost/**`

确认 Starter 进入运行时依赖、`cost.lite.integration.enabled=true`，并由宿主安全框架放行或授权对应路径。

### 业务字典编码不同

只在宿主配置中增加 `type-mappings` 和 `value-mappings`，不要改 `cost_*` 表字段、实体或接口编码。

## 13. 安全与提交边界

- 本目录不包含真实数据库密码、Token、钱包文件、客户数据或运行日志。
- Jar 加密暂不启用；请通过网络隔离、鉴权 Token、文件权限和部署平台控制运行端访问。
- 不要提交 `target/`、`node_modules/`、`dist/`、`*.log` 或业务项目源码。
