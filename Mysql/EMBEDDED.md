# MySQL 同进程嵌入式集成

本分支的推荐方案是：业务项目只引入 `cost-lite-starter-mysql`，计费入口和核心逻辑进入业务项目自己的 Spring 容器，同一个 JVM 内运行。计费表既可以放在专用库，也可以放在宿主业务库；不会再启动 `cost-lite-server`，也不需要把核心源码复制到客户项目。

## 1. 两个 Jar 的关系

交付物仍然是两个普通 Jar：

| 制品 | 职责 | 是否需要单独启动 |
| --- | --- | --- |
| `cost-lite-core-mysql` | 计费实体、Mapper、规则/公式引擎、Service、数据库 XML | 否，作为依赖加载 |
| `cost-lite-starter-mysql` | Spring Boot 自动配置、专用数据源或宿主数据源复用、Mapper 注册、`/cost/**` Controller 入口 | 否，随业务应用加载 |

客户项目只声明 `cost-lite-starter-mysql` 一个依赖，Maven 会自动传递下载 Core 和 MySQL 驱动；不需要手工写多条依赖，也不需要执行第二个 `java -jar`。`cost-lite-server` 只是旧项目或独立部署场景的兼容入口。

## 2. 客户项目添加依赖

要求：Java 8、Spring Boot 2.7.x Servlet Web 应用；该入口不依赖若依包名，成熟框架只要兼容 Spring Boot 2.7、Servlet 和 Spring 容器即可接入。建议把两个制品发布到企业 Maven 仓库：

```xml
<properties>
    <cost-lite.version>1.0.0</cost-lite.version>
</properties>

<dependency>
    <groupId>com.costplatform.lite</groupId>
    <artifactId>cost-lite-starter-mysql</artifactId>
    <version>${cost-lite.version}</version>
</dependency>
```

如果只是本机联调，在交付仓库根目录执行：

```powershell
$env:MAVEN_OPTS = "-Dmaven.repo.local=C:\Users\你的用户\.m2\repository"
mvn -f Mysql\pom.xml clean install -DskipTests
```

业务项目仍只引用 `cost-lite-starter-mysql`，不要把 `Mysql/source/` 复制进去。

## 3. 数据库配置方式

先执行 [cost-lite-schema.sql](sql/cost-lite-schema.sql)。这份默认脚本只创建工作台和试算所需表；正式任务、正式结果追溯、重算/告警或 OpenApp 只有在明确启用时，才继续执行 [cost-lite-formal-schema.sql](sql/cost-lite-formal-schema.sql)。数据库落位有两种方式，配置只保留一种：

### 3.1 复用宿主业务数据源

把同一份 SQL 初始化到宿主业务库，省略 `cost.lite.datasource.url`，Starter 会复用名为 `dataSource` 的宿主 `DataSource`：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    datasource:
      host-bean-name: ${COST_LITE_HOST_DATASOURCE_BEAN:dataSource}
```

如果宿主数据源 Bean 名称不是 `dataSource`，只调整 `host-bean-name`，不改计费代码。

### 3.2 使用专用计费库

把 SQL 初始化到专用数据库后，在业务项目外部配置文件或配置中心加入：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    auth-enabled: false
    operator: ${COST_LITE_OPERATOR:lite-admin}
    datasource:
      driver-class-name: ${COST_LITE_DB_DRIVER:com.mysql.cj.jdbc.Driver}
      url: ${COST_LITE_DB_URL:jdbc:mysql://127.0.0.1:13306/cost_platform_lite?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}
      username: ${COST_LITE_DB_USERNAME:root}
      password: ${COST_LITE_DB_PASSWORD:}
      hikari:
        pool-name: cost-lite-pool
        minimum-idle: 1
        maximum-pool-size: 10
        connection-timeout: 5000
```

Lite 使用命名 Bean `costLiteDataSource`、`costLiteSqlSessionFactory` 和 `costLiteTransactionManager`，不会覆盖业务项目原有的 `spring.datasource`。数据库密码只通过环境变量或配置中心提供，不写入 Git。Starter 会传递 MySQL JDBC 驱动；业务项目无需再复制核心源码。专用库配置只在需要与业务库隔离时使用，省略 URL 即切换为宿主库。

## 4. 自动注册后的接口

Starter 自动把 Core 的 Controller 注册到业务项目当前端口，前端和业务后端访问业务项目自己的：

```text
GET  /cost/lite/health
GET  /cost/lite/bootstrap
GET  /cost/dictionary/options
GET/POST/PUT/DELETE /cost/**
```

`/cost/lite/health` 返回 `service=UP` 和 `database=UP` 即表示入口、核心 Bean、Mapper 和独立数据库都已进入同一个应用。

嵌入模式不注册 Lite 的独立 `SecurityFilterChain`，业务项目继续负责登录、权限和 CORS。`auth-enabled=false` 只适合本地免登录验收；生产环境应由宿主保护 `/cost/**`，并按需启用管理令牌。

## 5. 已完成的同进程验证

仓库内的 `Mysql/example/` 只是验证宿主，不是客户必须复制的代码。它只有一个 Spring Boot 启动类，没有计费 Controller 和核心源码，启动后已验证：

```text
GET http://127.0.0.1:18081/cost/lite/health       -> 200
databaseProduct                                  -> MySQL
databaseName                                     -> cost_platform_lite
GET http://127.0.0.1:18081/cost/scene/optionselect -> 200
```

示例默认使用宿主 `spring.datasource` 作为计费数据源；设置 `COST_LITE_DB_URL` 后可切换为专用计费连接池，用于验证两种落位方式。

## 6. 老项目的选择

本 Starter 当前按 Java 8 + Spring Boot 2.7 编译，适用于 Java 8 的通用 Servlet 宿主，不要求宿主使用若依。传统 SSM、非 Spring Boot 宿主使用 `Mysql/backend-integration/cost-lite-client` 直接调用独立 Jar；只有 Spring Boot 2.7 宿主才使用同目录的 HTTP Starter 代理。无法共存 MyBatis 数据源或需要进程隔离的项目仍可选择独立 Jar，所有路径都不复制核心源码。Spring Boot 3 宿主需要另做 `jakarta.*` 适配，当前交付包不把 Boot 2 与 Boot 3 依赖混装。
