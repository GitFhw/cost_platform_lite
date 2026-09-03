# MySQL 轻量计费完整集成教程

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
- 日志和结果接口可单独授予只读角色。
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

工作台的场景、费目、要素、规则、版本、试算、日志和结果代码无需修改；适配器会自动把稳定调用转换为 Jar 的 `/cost/scene/**`、`/cost/run/**` 等母体兼容路径。若网关前缀是 `/business/cost`，只改 `basePath`，不逐个修改接口。

## 12. 首个场景联调顺序

工作台保持单一模式，建议按以下顺序配置：

1. 新增场景，填写场景编码、名称、业务域和对象维度。
2. 新增费目，维护费目编码、名称、分类和单位。
3. 新增要素，至少配置一个 `INPUT` 来源的数量要素。
4. 在费目下新增规则，选择固定费率、固定金额、阶梯或公式方式。
5. 执行发布前检查，创建版本并设为生效。
6. 生成输入模板并执行同步计费。
7. 检查调用日志；提交正式任务后检查结果台账和追溯详情。

## 13. 同步计费示例

先通过工作台或接口取得真实 `sceneId`、`versionId` 和 `feeId`：

```bash
curl -X POST http://127.0.0.1:8080/cost-lite/calculate \
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

成功返回中包含费用金额和 `billingLogId`。格式错误等失败请求也会返回 `billingLogId`，用于查询失败日志。

## 14. 公式能力说明

- 运行端保留公式新增、修改、列表、试算、版本和回滚接口。
- 当前精简工作台在规则中支持选择已有公式，或直接填写表达式。
- 当前没有单独的公式维护页面，不影响固定费率、固定金额、阶梯和手写表达式联调。
- 独立公式页后续作为可插拔前端模块交付，删除该模块不会影响核心计费。

## 15. 日志、结果和报表边界

- 同步计费成功或失败均写入 `cost_simulation_record`。
- 正式任务写入 `cost_calc_task`、明细和分片表。
- 正式计费数据写入 `cost_result_ledger`。
- 命中规则、变量、条件和定价过程写入 `cost_result_trace`。
- 第三方自定义报表应读取稳定结果表或调用查询接口，不直接修改计费核心表。

## 16. 验收清单

- [ ] `/cost/lite/health` 中服务和数据库均为 `UP`。
- [ ] 宿主 `/cost-lite/health` 能代理成功。
- [ ] 前端能加载场景列表（Starter 代理或 Jar 直连均可）。
- [ ] 能新增场景、费目、要素和规则。
- [ ] 发布前检查通过，版本可生效。
- [ ] 正确输入能返回预期金额。
- [ ] 错误输入能生成失败日志和 `billingLogId`。
- [ ] 正式任务能生成结果台账和追溯记录。
- [ ] 未把数据库密码、Token 或客户数据提交到 Git。

## 17. 常见问题

### 宿主启动后没有 `/cost-lite/health`

检查 `cost.lite.integration.enabled=true`、Starter 是否进入运行时依赖，以及宿主组件扫描是否包含自动配置。

### 代理返回连接失败

从宿主服务所在机器访问 `COST_LITE_BASE_URL`，检查端口、防火墙、容器网络和上下文路径。

### 页面请求路径多了一层或少了一层

`web-path` 是业务服务内部路径；前端 `basePath` 应填写浏览器实际经过网关后的路径。

### 业务系统字典不一致

直接维护当前轻量库 `sys_dict_type`、`sys_dict_data` 中对应的计费字典名称和值，刷新工作台即可生效；不要修改计费表中的统一编码。只有明确需要复用宿主已有字典、且不把宿主字典表迁入轻量库时，才通过可插拔字典适配器处理映射。
