# MySQL 轻量计费完整集成教程

接口字段、场景级调用、指定费目调用和 Java/cURL 示例见：[Mysql/API.md](API.md)。

## 1. 适用范围

本目录适用于以下两种部署方式：

1. 独立 MySQL 库：轻量计费使用单独数据库，推荐用于标准交付。
2. 业务 MySQL 库：把 `cost_*` 表初始化到业务库，适用于基础设施受限的项目。

两种方式使用同一套表名和字段。区别只有 JDBC 地址，不需要修改计费实体或 Starter。

### 1.1 配置归属和组件职责

数据库账号密码不打进 Jar，也不写入 Git。MySQL 运行 Jar 在启动时读取环境变量或 Jar 外部的 `application.yml`；Starter 不读取数据库配置，只负责在业务项目中提供 `/cost-lite/**` 代理 Controller，并把请求转给 MySQL Jar。

因此两种部署的配置边界如下：

| 组件 | 配置内容 | 是否连接数据库 |
| --- | --- | --- |
| MySQL 运行 Jar | JDBC URL、数据库账号密码、运行端口、管理 Token | 是，连接专用库或业务库 |
| 业务项目 Starter | MySQL Jar 地址、代理路径、超时、令牌 | 否 |
| `Front/` 工作台 | `/cost-lite` 或 `/cost` 路由模式 | 否 |

当前推荐的正式接入形态是“业务项目前端 + 业务项目 Starter + MySQL 运行 Jar”。运行 Jar 内置轻量 Controller、计费核心和 MySQL 方言适配；Starter 本身不是计费核心，也不把母体 Controller、Service、Mapper 复制进业务项目。

## 2. 环境要求

- 轻量计费运行 Jar：JDK 17。
- 数据库：MySQL 8.0+，字符集 `utf8mb4`。
- 宿主后端：Java 8+；Spring Boot 2.7 或 Spring Boot 3 均可。
- 宿主前端：Vue 3、Element Plus。前端接入见 `../Front/README.md`。

## 3. 初始化数据库

### 3.1 独立库

```sql
create database if not exists cost_platform_lite
  default character set utf8mb4
  collate utf8mb4_general_ci;
```

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p cost_platform_lite < Mysql/sql/cost-lite-schema.sql
```

### 3.2 复用业务库

先备份业务库并确认不存在同名 `cost_*` 表，然后把脚本执行到业务库：

```bash
mysql -h 127.0.0.1 -P 3306 -u app_user -p business_db < Mysql/sql/cost-lite-schema.sql
```

脚本使用母体平台表名，不创建轻量版平行实体。已有同名表时应先比对字段，不要直接覆盖。

脚本只包含当前轻量 Jar 和工作台所需的 26 张 `cost_*` 表：场景、费目、要素、规则、发布、试算、正式任务、结果追溯，以及运行所需的账期控制、审计和异常治理表；另包含母体字典实体对应的 `sys_dict_type`、`sys_dict_data` 两张字典表。`cost_bill_period` 是正式任务的账期运行控制表，不是账单明细表；脚本不包含 RuoYi 的其他 `sys_*` 表、宿主基础设施表、客户业务表或无关账单表，也不依赖宿主字典接口。前端下拉直接读取当前轻量库字典，修改字典表后刷新页面即可。

### 3.3 初始化检查

```sql
select count(*) as cost_table_count
from information_schema.tables
where table_schema = database()
  and table_name like 'cost\_%';
```

至少应包含场景、费目、要素、规则、发布、试算、任务、结果和追溯相关表。

## 4. 启动计费运行 Jar

运行制品位于：

```text
Mysql/runtime/cost-lite-server-1.0.0.jar
```

### 4.1 PowerShell

```powershell
$env:COST_LITE_DB_HOST = "127.0.0.1"
$env:COST_LITE_DB_PORT = "3306"
$env:COST_LITE_DB_NAME = "cost_platform_lite"
$env:COST_LITE_DB_USERNAME = "root"
$env:COST_LITE_DB_PASSWORD = "请替换为真实密码"
$env:COST_LITE_SERVER_PORT = "18080"
$env:COST_LITE_LOG_PATH = "D:\\apps\\cost-lite\\logs"

.\Mysql\bin\start-cost-lite.ps1
```

也可以显式指定 Jar：

```powershell
.\Mysql\bin\start-cost-lite.ps1 -JarPath "D:\\apps\\cost-lite-server-1.0.0.jar"
```

### 4.2 Linux

```bash
export COST_LITE_DB_HOST=127.0.0.1
export COST_LITE_DB_PORT=3306
export COST_LITE_DB_NAME=cost_platform_lite
export COST_LITE_DB_USERNAME=root
export COST_LITE_DB_PASSWORD='请替换为真实密码'
export COST_LITE_SERVER_PORT=18080
export COST_LITE_LOG_PATH=/opt/cost-lite/logs

bash Mysql/bin/start-cost-lite.sh
```

生产环境应由 systemd、容器平台或现有进程管理器托管，不要把密码写入脚本或 Git。

### 4.3 使用 Jar 外部 `application.yml`

数据库配置建议放在运行 Jar 外部，由部署环境注入；不要修改 Jar 内部配置。仓库提供的 `Mysql/config/application-mysql.yml` 可以直接作为模板：

```yaml
spring:
  datasource:
    url: jdbc:mysql://数据库地址:3306/目标数据库?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: 计费数据库账号
    password: ${COST_LITE_DB_PASSWORD}
```

启动时指定 MySQL profile 和外部配置目录：

```powershell
$env:COST_LITE_DB_PASSWORD = "请替换为真实密码"
java -jar .\Mysql\runtime\cost-lite-server-1.0.0.jar `
  --spring.profiles.active=mysql `
  --spring.config.additional-location="optional:file:./Mysql/config/"
```

这里的“外部配置”是运行 Jar 所在部署单元的配置，不是把数据库账号交给前端。若 Jar 独立部署，它读取 Jar 进程的环境变量或配置文件；业务项目 Starter 只读取自己的 `base-url` 和令牌。

## 5. 验证运行服务

```bash
curl http://127.0.0.1:18080/cost/lite/health
```

成功响应应同时满足：

```json
{
  "code": 200,
  "data": {
    "service": "UP",
    "database": "UP",
    "databaseProduct": "MySQL",
    "databaseName": "cost_platform_lite"
  }
}
```

## 6. 安装宿主 Starter

在仓库根目录执行：

```bash
mvn -f Mysql/backend-integration/pom.xml clean install
```

Starter 和 Client 均以 Java 8 编译。Client 不依赖 Spring；Starter 同时兼容 Spring Boot 2.7 的 `spring.factories` 和 Spring Boot 3 的自动配置导入机制。

## 7. 修改宿主 POM

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

正式环境建议把两个制品发布到企业 Maven 仓库，业务项目只保留普通 Maven 依赖。

这里的 Starter 是宿主侧 HTTP 代理适配器，不是数据库运行 Jar。它不承载计费公式、规则匹配和结果落库逻辑；这些逻辑都在 `Mysql/runtime/cost-lite-server-1.0.0.jar` 中执行。

## 8. 配置宿主后端

把 `Mysql/config/host-application.yml` 中的内容合并到宿主配置：

```yaml
cost:
  lite:
    integration:
      enabled: true
      base-url: ${COST_LITE_BASE_URL:http://127.0.0.1:18080}
      admin-token: ${COST_LITE_ADMIN_TOKEN:}
      open-token: ${COST_LITE_OPEN_TOKEN:}
      web-path: /cost-lite
      proxy-enabled: true
      connect-timeout: 5000
      read-timeout: 30000
      max-retries: 0
      response-mode: status
```

启动宿主后验证代理：

```bash
curl http://127.0.0.1:8080/cost-lite/health
```

如果宿主经过网关统一增加服务前缀，例如 `/business`，浏览器访问路径通常是 `/business/cost-lite/health`，但业务服务内部的 `web-path` 仍保持 `/cost-lite`。

## 9. 权限接入

Starter 只注册代理 Controller，不接管宿主鉴权。应由宿主安全框架保护 `/cost-lite/**`：

- 维护类接口只授予计费实施管理员。
- 试算日志接口可单独授予只读角色；正式任务和正式结果仍由后端内部权限控制。
- `/cost-lite/health` 可按项目要求放行或限制在内网。
- 生产环境启用运行 Jar 管理 Token，并通过环境变量传给宿主。

## 10. 字典差异处理

计费数据库始终保存母体统一编码。工作台通过 `/cost/dictionary/options` 读取轻量库的 `sys_dict_data`，中文名称和可选值由数据库维护。目标项目的字典名称或值需要变化时，直接修改对应字典数据，不需要改前端代码，也不需要接入宿主字典表。Jar 默认使用轻量库字典校验：

```yaml
cost:
  lite:
    dictionary:
      provider: SYSTEM
      validation-enabled: true
      allow-unconfigured-types: true
```

其中 `SYSTEM` 指当前 Jar 连接的轻量库字典表，不是读取宿主数据库。只有确实要把宿主已有字典映射到计费统一编码时，才使用可选的 `CONFIG` 或 `CostDictionaryProvider` 扩展。

## 11. 接入前端

按 `../Front/README.md` 把工作台加入宿主前端，并配置一个菜单：

```text
菜单名称：轻量计费
页面路由：/cost/lite
组件路径：项目内的 Cost Lite 页面包装组件
```

### 11.1 使用宿主 Starter 代理

这是推荐的企业项目接入方式。前端只调用稳定的 `/cost-lite/**` 协议：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost-lite", routeMode: "proxy" },
);
```

### 11.2 独立 Jar 直连

如果业务系统不引入 Starter，而是通过同源反向代理或网关把独立 Jar 暴露为 `/cost`，只需切换一处配置：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost", routeMode: "runtime" },
);
```

工作台的场景、费目、要素、规则、版本、试算和试算日志代码无需修改；适配器会自动把稳定调用转换为 Jar 的 `/cost/scene/**`、`/cost/run/**` 等母体兼容路径。若网关前缀是 `/business/cost`，只改 `basePath`，不逐个修改接口。

## 12. 首个场景联调顺序

工作台保持单一模式，建议按以下顺序配置：

1. 新增场景，填写场景编码、名称、业务域和对象维度。
2. 新增费目，维护费目编码、名称、分类和单位。
3. 新增要素，至少配置一个 `INPUT` 来源的数量要素。
4. 在费目下新增规则，选择固定费率、固定金额、阶梯或公式方式。
5. 执行发布前检查，创建版本并设为生效。
6. 生成输入模板，替换一条真实业务数据并执行试算。
7. 检查试算结果预览和试算日志；点击“接口示例”核对场景级、指定费目的后端调用参数。

## 13. 试算与同步计费的区别

工作台的试算用于验证配置，不创建正式任务，也不写入正式结果台账。它使用与业务调用相同的真实业务数据格式，结果和成功/失败状态写入 `cost_simulation_record`，便于配置人员定位要素、规则和公式问题。

同步计费是业务系统后端的生产接口：请求到达后立即执行核心并返回金额，同时写入计费调用日志；它不是异步正式任务，也不应从浏览器直接调用。业务系统需要把返回的 `billingLogId` 与自己的业务单号关联保存。

工作台试算接口：

```bash
curl -X POST http://127.0.0.1:8080/cost-lite/simulations \
  -H "Content-Type: application/json" \
  -d '{
    "sceneId": 4,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": true
  }'
```

不传 `feeId`、`feeIds` 或 `feeCode` 时按场景全部费目试算；传入其中一种时按指定费目试算。存在费目依赖时，结果会同时保留依赖执行范围，响应中的 `targetFeeCodes` 是最终需要关注的费目。

### 13.1 批量试算

批量试算用于一次验证多条真实业务数据，走受保护的管理/集成链路，不需要创建 `openApp`：

| 调用方式 | 路径 |
| --- | --- |
| Starter 代理 | `POST /cost-lite/simulations/batch` |
| 独立 Jar | `POST /cost/run/simulation/batch-execute` |

`inputJson` 传对象数组字符串，每个对象建议带唯一 `bizNo`。服务端逐条执行，成功和失败都写入 `cost_simulation_record`，返回批次统计和每条记录的单条详情节点；金额读取 `records[*].result.amountTotal`，费目明细读取 `records[*].result.feeResults`。不会创建正式任务、正式结果台账或结果追溯。

```json
{
  "sceneId": 4,
  "versionId": 1,
  "feeId": 1,
  "billMonth": "2026-09",
  "inputJson": "[{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8},{\"bizNo\":\"ORDER-002\",\"objectCode\":\"ORDER-002\",\"quantity\":3}]",
  "includeExplain": true
}
```

## 14. 同步计费示例

先通过工作台或接口取得真实 `sceneId`、`versionId` 和 `feeId`：

```bash
curl -X POST http://127.0.0.1:8080/cost-lite/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "sceneId": 4,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": true
}'
```

Starter 通过宿主权限保护 `/cost-lite/calculate`，上游管理令牌由 Starter 的 `admin-token` 配置注入；独立 Jar 对应路径是 `POST /cost/run/fee/calculate`，直接使用 `X-Cost-Lite-Token`。成功返回中包含费用金额和 `billingLogId`。格式错误等失败请求也会返回 `billingLogId`，用于查询失败日志。

## 15. 场景级业务系统调用方案

业务项目内嵌集成时，业务后端引入 Starter 并调用稳定的 `/cost-lite/**` 路径；Starter 连接独立 Jar 时使用配置中的 `base-url` 和 `admin-token`，业务代码不接触数据库账号，也不需要创建 `openApp`。独立 Jar 部署则直接调用 `/cost/**` 管理路径：

| 能力 | Starter 代理 | 独立 Jar |
| --- | --- | --- |
| 查询场景 | `GET /cost-lite/scenes` | `GET /cost/scene/list` |
| 查询场景版本 | `GET /cost-lite/scenes/{sceneId}/versions` | `GET /cost/run/version-options/{sceneId}` |
| 查询场景费目 | `GET /cost-lite/fees?sceneId={sceneId}` | `GET /cost/fee/list?sceneId={sceneId}` |
| 场景级同步计费 | `POST /cost-lite/calculate` | `POST /cost/run/fee/calculate` |
| 指定费目同步计费 | 同上，请求体传 `feeId` | 同上，请求体传 `feeId` |
| 批量试算 | `POST /cost-lite/simulations/batch` | `POST /cost/run/simulation/batch-execute` |

业务系统启动或缓存刷新时解析一次“场景 -> 生效版本 -> 费目”，交易请求只传稳定的 `sceneId`、`versionId`、`feeId` 和输入数据；不要在每笔交易中读取 `cost_*` 配置表。生产同步计费固定使用生效版本，配置变更由发布流程控制。

如确实需要把计费服务作为跨系统开放 API，再启用 `openApp` 和短期令牌模式；它是可选兼容能力，不是本项目内嵌 Starter 集成的前置条件，完整路径见 `API.md` 第 2 至 5 节。

## 16. 指定费目的计算方案

场景级调用建议由业务系统在启动或缓存刷新时完成一次“场景 -> 生效版本 -> 费目”解析，业务交易时只提交明确的 `sceneId`、`versionId`、`feeId` 和输入数据。这样同一场景下多个费目可以分别调用，也可以将 `feeId` 省略后按场景计算全部费目。内嵌 Starter 不需要 `openApp`，下面使用管理/集成路径：

```bash
# 1. 查询场景当前可用版本和费目
curl "http://127.0.0.1:8080/cost-lite/scenes/4/versions"
curl "http://127.0.0.1:8080/cost-lite/fees?sceneId=4&pageNum=1&pageSize=200"

# 2. 按指定费目取得输入模板
curl "http://127.0.0.1:8080/cost-lite/template?sceneId=4&versionId=1&feeId=1"

# 3. 只计算一个费目
curl -X POST http://127.0.0.1:8080/cost-lite/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "sceneId": 4,
    "versionId": 1,
    "feeId": 1,
    "billMonth": "2026-09",
    "inputJson": "{\"bizNo\":\"ORDER-001\",\"objectCode\":\"ORDER-001\",\"quantity\":8}",
    "includeExplain": false
  }'
```

`feeId`、`feeIds`、`feeCode` 三者用于指定计算范围，推荐使用稳定的 `feeId`；版本切换时重新从场景版本接口取得 ID。返回结果会包含运行结果和 `billingLogId`，业务系统应保存自己的业务单号与该日志编号的对应关系。失败请求同样写入调用日志，便于平台侧追查。独立 Jar 调用时将路径换成 `/cost/run/fee/calculate`，并按运行端配置使用 `X-Cost-Lite-Token`。

## 17. 试算日志与结果查询方案

工作台的“试算日志”页查询 `cost_simulation_record`，成功和失败都可打开详情，详情中包含试算输入、要素快照、解释和试算结果。正式结果台账不属于本工作台范围；如果业务系统后端启用受保护的正式任务链，再由后端或报表服务查询 `cost_result_ledger` 和 `cost_result_trace`。本工作台只需要以下试算日志查询路径：

| 查询内容 | Starter 代理 | 独立 Jar |
| --- | --- | --- |
| 试算日志分页 | `GET /cost-lite/logs` | `GET /cost/lite/billing-log/list` |
| 试算日志详情（含结果） | `GET /cost-lite/logs/{simulationId}` | `GET /cost/lite/billing-log/{simulationId}` |

示例：

```bash
curl -H "X-Cost-Lite-Token: $ADMIN_TOKEN" \
  "http://127.0.0.1:8080/cost-lite/logs?sceneId=4&pageNum=1&pageSize=20"
curl -H "X-Cost-Lite-Token: $ADMIN_TOKEN" \
  "http://127.0.0.1:8080/cost-lite/logs/10001"
```

内嵌 Starter 集成时，试算日志和配置管理通过宿主权限保护；独立 Jar 管理接口使用 `X-Cost-Lite-Token`。OpenApp 令牌模式不开放试算日志、正式任务或正式结果管理查询；不要把管理 Token 下发给浏览器。第三方报表如启用正式核算，应由业务系统后端或只读投影服务查询，不修改计费核心表。

## 18. 高基数要素和规则组合

货种、客户、船舶等数据量较大时，不要把全量数据初始化到 `sys_dict_data`，也不要在 `cost_*` 表复制业务主数据。计费输入只传业务编码，例如 `goodsCode`；如果规则需要业务系统补充分类，就由业务系统在调用计费核心前查询并同时传入 `goodsCategory`。当前轻量 Jar 的运行态 `REMOTE` 要素只解析请求中的 `remoteContext`、`remotePayload` 或 `remoteData`，不会在每笔计费时根据 `remoteApi` 自动发起外部 HTTP；`remoteApi` 目前用于连接测试、数据预览和保留母体配置兼容性。后续要做真正的实时自动取数，应由宿主先补齐远程上下文，或单独接入可插拔运行时取数适配器，并明确超时、缓存、失败兜底和批量合并策略。

配置人员需要选择货种时，宿主页面提供受权限保护的分页搜索接口，例如 `GET /business/goods/options?keyword=钢&pageNum=1&pageSize=20&value=G001`，返回 `{ "rows": [{ "label": "钢材", "value": "G001" }], "total": 1 }`。工作台只保存编码到规则比较值，不直接访问客户数据库；当前页面保留文本输入作为零新增开发的默认路径，远程选项控件可以由宿主按此契约接入。

规则不要按所有维度做笛卡尔积。当前核心是同一费目按规则优先级从高到低命中第一条，规则组内条件为 AND，组间由 `conditionLogic` 决定；推荐“特殊组合覆盖 -> 单维度兜底 -> 无条件基础价”。例如只有确实影响价格时才组合“货种分类 + 内外贸 + 进出口”，货种编码优先归类后再参与规则；若必须维护数万条货种与价格映射，应通过可插拔的业务侧分类/价格适配器处理，不把映射硬塞进规则文本。

当前 MySQL 首轮联调建议先使用 `INPUT` 要素传入 `goodsCode`、`goodsCategory`、`tradeMode`、`direction` 和 `quantity`，先验证一条费目能正确命中特殊规则、兜底规则并返回金额。不要把高基数货种实时下拉和自动远程取数放进首轮验收，这样可以先确认数据库、规则、公式、发布和试算日志主链路。

## 19. 公式能力与企业级边界

- 运行端保留公式新增、修改、列表、试算、版本和回滚接口。
- 当前精简工作台在同一页面提供“公式”页，支持中文业务口径、标准表达式、返回类型、状态、在线试算、版本查看和回退。
- 公式是按场景归属的独立可复用资产；规则负责条件命中，规则类型为 `FORMULA` 时必须引用有效的 `amountFormulaCode`。简单业务也可以在规则中直接填写手写表达式。
- 当前不启用“公式费目无规则”模式。母体核心按费目下的规则构造执行链，没有规则的费目会被跳过；发布校验、依赖分析、结果追溯也都以规则为边界。现在强行让公式费目免规则，会造成配置能保存但运行无结果，不符合企业级可追溯要求。
- 后续若要支持免规则公式费目，应作为独立核心升级：新增明确的费目计价来源/公式绑定语义，并同步改造发布快照、依赖校验、执行链、治理删除和结果追溯；不能用 `ruleType=FORMULA` 偷换概念。

## 20. 日志、结果和报表边界

- 试算和同步计费成功或失败均写入 `cost_simulation_record`。
- 试算结果保存在该表的 `result_json`，工作台只查询和展示这类结果。
- 正式任务写入 `cost_calc_task`、明细和分片表；这是运行端保留的后端扩展能力，不在本工作台范围内。
- 正式计费数据写入 `cost_result_ledger`。
- 命中规则、变量、条件和定价过程写入 `cost_result_trace`。
- 第三方自定义报表应读取稳定结果表或调用查询接口，不直接修改计费核心表。

## 21. 验收清单

- [ ] `/cost/lite/health` 中服务和数据库均为 `UP`。
- [ ] 宿主 `/cost-lite/health` 能代理成功。
- [ ] 前端能加载场景列表（Starter 代理或 Jar 直连均可）。
- [ ] 能新增场景、费目、要素、条件组、规则和公式。
- [ ] 公式试算返回预期结果，公式版本可以查看和回退。
- [ ] 发布前检查通过，版本可生效。
- [ ] 正确输入能返回预期金额。
- [ ] 错误输入能生成失败日志和 `billingLogId`。
- [ ] 真实业务 JSON 能按场景或指定费目试算，结果和失败原因可在试算日志详情查看。
- [ ] 业务后端通过 Starter/Jar 管理链路调用同步计费，能取得金额和 `billingLogId`。
- [ ] 未把数据库密码、Token 或客户数据提交到 Git。

## 22. 常见问题

### 宿主启动后没有 `/cost-lite/health`

检查 `cost.lite.integration.enabled=true`、Starter 是否进入运行时依赖，以及宿主组件扫描是否包含自动配置。

### 代理返回连接失败

从宿主服务所在机器访问 `COST_LITE_BASE_URL`，检查端口、防火墙、容器网络和上下文路径。

### 页面请求路径多了一层或少了一层

`web-path` 是业务服务内部路径；前端 `basePath` 应填写浏览器实际经过网关后的路径。

### 业务系统字典不一致

直接维护当前轻量库 `sys_dict_type`、`sys_dict_data` 中对应的计费字典名称和值，刷新工作台即可生效；不要修改计费表中的统一编码。只有明确需要复用宿主已有字典、且不把宿主字典表迁入轻量库时，才通过可插拔字典适配器处理映射。
