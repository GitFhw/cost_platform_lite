# Oracle 同进程嵌入式集成

Java 8 + Spring Boot 2.7 Servlet 业务项目只引入 `cost-lite-starter-oracle`，计费 Controller、Service、Mapper 和核心规则执行在业务应用同一个 JVM 内运行，不启动单独的 Lite 进程，也不复制 `Oracle/source/`。

## 1. 制品关系

| 制品 | 职责 | 是否单独启动 |
| --- | --- | --- |
| `cost-lite-core-oracle` | 母体计费实体、Mapper、规则/公式执行、发布和试算核心 | 否，作为依赖加载 |
| `cost-lite-starter-oracle` | Spring Boot 自动配置、Oracle 独立数据源、Mapper 注册和 `/cost/**` 入口 | 否，随业务应用加载 |

业务项目只声明 `cost-lite-starter-oracle`，Maven 会传递引入 Core 和 Oracle JDBC 驱动。Starter 的实现契约与 MySQL Starter 一致，数据库差异由对应 Core、驱动、SQL 和 Oracle 方言适配承担。

## 2. 添加 POM 依赖

```xml
<properties>
    <cost-lite.version>1.0.0</cost-lite.version>
</properties>

<dependency>
    <groupId>com.costplatform.lite</groupId>
    <artifactId>cost-lite-starter-oracle</artifactId>
    <version>${cost-lite.version}</version>
</dependency>
```

本仓库构建并安装本地制品：

```bash
mvn -f Oracle/pom.xml clean install -DskipTests
```

客户项目只引用 Starter，不复制 `Oracle/source/`，不新增计费 Controller、Service、Mapper 或实体。

## 3. 配置计费数据库

先由 DBA 执行 [cost-lite-schema.sql](sql/cost-lite-schema.sql)。计费库可以是独立 Oracle Schema，也可以是业务 Schema；只要 SQL 已初始化且 JDBC 用户可访问 `cost_*`、`sys_dict_type`、`sys_dict_data` 即可。

在业务项目外部配置文件或配置中心加入：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    auth-enabled: false
    operator: ${COST_LITE_OPERATOR:lite-admin}
    datasource:
      driver-class-name: ${COST_LITE_DB_DRIVER:oracle.jdbc.OracleDriver}
      url: ${COST_LITE_DB_URL:jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1}
      username: ${COST_LITE_DB_USERNAME}
      password: ${COST_LITE_DB_PASSWORD}
      hikari:
        pool-name: cost-lite-oracle-pool
        minimum-idle: 1
        maximum-pool-size: 10
        connection-timeout: 5000
        validation-timeout: 3000
```

数据库地址、账号和密码属于宿主外部配置，不写入 Jar、前端或 Git。Starter 使用命名 Bean `costLiteDataSource`、`costLiteSqlSessionFactory` 和 `costLiteTransactionManager`，不会覆盖业务项目自己的 `spring.datasource`。

## 4. 入口和前端

Starter 会在业务项目当前端口注册母体兼容路径：

```text
GET  /cost/lite/health
GET  /cost/lite/bootstrap
GET  /cost/dictionary/options
GET/POST/PUT/DELETE /cost/**
```

工作台使用 `Front/src`，嵌入 Starter 时选择母体兼容路由：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost", routeMode: "runtime" },
);
```

宿主只增加一个菜单和页面包装组件。生产环境继续由宿主登录、权限和网关保护 `/cost/**`；`auth-enabled=false` 仅用于本地验收。

## 5. 验收顺序

```text
1. GET  /cost/lite/health，确认 service=UP、database=UP、databaseProduct=Oracle。
2. GET  /cost/dictionary/options，确认中文字典来自当前计费库。
3. 新增场景、费目、要素、条件组、规则和公式。
4. 通过发布前检查并发布一个版本。
5. 用真实业务 JSON 执行单条和批量试算。
6. 在试算日志查看 input、variables、explain、result.amountTotal 和 result.feeResults。
7. 提交错误 JSON，确认失败日志仍然留存。
```

工作台只开放配置、发布检查、单条试算、批量试算、试算日志和试算结果；同步计费由业务后端调用，不开放正式任务和正式结果页面。正式接口和场景级/指定费目调用示例见 [API.md](API.md)。

## 6. 老项目兼容方式

传统 SSM、Spring Boot 2 但无法引入同进程 Starter，或需要独立进程隔离的项目，使用 `Oracle/backend-integration` 的 HTTP Client/Starter + `Oracle/runtime/cost-lite-server-1.0.0.jar`。这条路径不复制核心源码；前端使用 `routeMode: "proxy"`，由 HTTP Starter 转发到独立 Jar。
