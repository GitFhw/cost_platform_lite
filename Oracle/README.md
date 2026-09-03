# Oracle 轻量计费集成教程

本目录是可直接交付给第三方业务系统的 Oracle 集成包。运行端以独立 Jar 交付，宿主系统可选择通过 HTTP 直连 Jar，或引入 `cost-lite-spring-boot-starter` 暴露同源代理。仓库只保存集成所需的 SQL、配置、脚本、Client、Starter 和运行制品，不包含母体平台源码或第三方项目源码。

## 1. 交付内容

```text
Oracle/
├─ backend-integration/
│  ├─ cost-lite-client/                 Java 8 兼容 HTTP Client
│  └─ cost-lite-spring-boot-starter/    可选 Spring Boot 代理 Starter
├─ bin/
│  ├─ start-cost-lite.ps1              Windows 启动脚本
│  └─ start-cost-lite.sh                Linux 启动脚本
├─ config/
│  ├─ application-oracle.yml            Jar 运行配置样例
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

- 运行 Jar：JDK 17。
- Oracle：12c 或更高版本，推荐 19c、21c 或 23ai；数据库用户需要 `CREATE SESSION`、建表权限和对应表空间配额。
- 宿主后端：Java 8 及以上；Starter 可用于 Spring Boot 2.7 或 Spring Boot 3。
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

本交付脚本包含母体当前冻结模型的 26 张 `cost_*` 表和相关二级索引。若业务库已有母体表，最终以业务库实际模型和母体版本基线为准。

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

### 6.1 独立 Jar + HTTP

业务系统不需要复制母体 Controller、Service、Mapper 或实体，只需通过网关或同源反向代理访问 Jar 的 `/cost/**` 路由。前端适配器使用 `routeMode: runtime`。

### 6.2 Spring Boot Starter 代理

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
      web-path: /cost-lite
      proxy-enabled: true
      connect-timeout: 5000
      read-timeout: 30000
      max-retries: 0
```

Starter 只负责稳定代理和响应适配，不接管宿主登录、角色和菜单权限。宿主按自己的安全框架保护 `/cost-lite/**`，因此 RuoYi、Spring Cloud 或其他成熟框架都可以接入。

宿主启动后检查：

```bash
curl http://127.0.0.1:8080/cost-lite/health
```

## 7. 前端接入

将 `../Front/src/CostLiteWorkbench.vue` 复制或作为组件依赖引入，在宿主路由增加一个菜单即可。推荐的代理模式：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost-lite", routeMode: "proxy" },
);
```

独立 Jar 直连模式：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost", routeMode: "runtime" },
);
```

如果业务网关前缀为 `/business/cost`，只调整 `basePath`，不需要逐个改动场景、费目、要素、规则、版本、日志和结果接口。菜单清单和完整页面说明见 `../Front/menu-manifest.yml` 与 `../Front/README.md`。

## 8. 最快一日接通顺序

工作台只有一个维护模式，面向计费实施人员或技术维护人员，不要求业务人员承担计费配置职责。建议按以下顺序操作：

1. 执行 Oracle DDL，启动 Jar，确认健康检查为 `UP`。
2. 新增场景，维护场景编码、名称、业务域和对象维度。
3. 在场景下新增费目。
4. 在场景下新增要素，至少配置一个 `INPUT` 来源的数量要素。
5. 在费目下新增规则，先使用固定费率或固定金额跑通链路，再按需要使用阶梯或公式。
6. 执行发布前检查，创建版本并立即生效。
7. 通过工作台或接口执行同步计费。
8. 检查成功或失败日志；正式任务完成后检查结果台账和追溯详情。

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

### 9.3 同步计费

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

### 9.4 正式任务与结果

正式任务使用 `/cost/run/task/submit`，任务详情使用 `/cost/run/task/{taskId}`，结果台账使用：

```text
GET /cost/run/result/list
GET /cost/run/result/{resultId}
GET /cost/run/trace/{traceId}
```

正式任务会保存任务、明细、结果台账和追溯数据；第三方报表可基于结果查询接口或 `cost_result_ledger` 只读开发。

## 10. 公式与可插拔扩展

- 母体公式实体和公式接口保留，规则可选择已有公式或填写 `amountFormula`。
- 当前精简工作台优先支持手写表达式和固定/阶梯费率，独立公式维护页可以作为前端可插拔模块后续加入。
- 定制化新增能力放在插件目录，通过 `COST_LITE_PLUGIN_ENABLED` 和 `COST_LITE_PLUGIN_DIR` 控制；不用的插件可以不部署或关闭。
- 字典差异通过宿主 YAML 映射或 `CostDictionaryProvider` 扩展适配，数据库仍保存母体统一编码。

## 11. 日志、结果与数据边界

- 同步计费成功和失败都写入母体 `cost_simulation_record`。
- 正式任务写入 `cost_calc_task`、任务明细、分片和输入批次相关表。
- 正式计费数据写入 `cost_result_ledger`。
- 规则命中、变量、条件和定价过程写入 `cost_result_trace`。
- 计费日志和结果数据不会因为前端页面关闭而丢失；报表由第三方按需自定义，不修改核心计费表结构。

## 12. 常见问题

### 连接报 ORA-12514 或 ORA-12505

确认使用的是正确的 Service Name 或 SID。优先用 `COST_LITE_DB_URL` 写入现场完整 JDBC URL，不要修改 Jar。

### 报 ORA-00942 或无权限

确认运行用户就是执行 DDL 的 Schema，且拥有表空间配额和建表权限。业务库部署时检查当前连接用户，不要只检查 DBA 用户。

### 查询分页失败

Oracle Jar 内置 MyBatis 方言适配，会将母体 Mapper 使用的 `LIMIT`、函数和批量插入转换为 Oracle 语义。若仍失败，先检查 Jar SHA-256 是否与 `runtime/SHA256SUMS` 一致，再保留脱敏 SQL 日志反馈。

### 宿主没有 `/cost-lite/**`

确认 Starter 进入运行时依赖、`cost.lite.integration.enabled=true`，并由宿主安全框架放行或授权对应路径。

### 业务字典编码不同

只在宿主配置中增加 `type-mappings` 和 `value-mappings`，不要改 `cost_*` 表字段、实体或接口编码。

## 13. 安全与提交边界

- 本目录不包含真实数据库密码、Token、钱包文件、客户数据或运行日志。
- Jar 加密暂不启用；请通过网络隔离、鉴权 Token、文件权限和部署平台控制运行端访问。
- 不要提交 `target/`、`node_modules/`、`dist/`、`*.log` 或业务项目源码。
