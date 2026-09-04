# MySQL 计费核心源码

本目录是 MySQL 计费核心普通 Jar 的可维护源码，来自母体计费模块的必要边界，包含：

- 计费 Controller、插件、兼容类和数据库 Mapper 资源；
- 母体计费实体、Mapper、规则执行、公式、发布校验和试算服务；
- 轻量运行所需的通用兼容类和计费字典 Mapper；
- MySQL 运行资源和与母体字段一致的 Mapper XML。

这里没有复制母体后台、用户权限、菜单、账单、定时任务、代码生成等无关模块，也不再包含独立启动类和运行配置。客户业务项目不需要复制本目录；嵌入模式使用 `cost-lite-starter-mysql` 自动传递本模块，独立模式由上级 `server/` 组合成 `cost-lite-server-mysql`。

## 构建

要求 Java 8（构建机可使用更高版本但目标字节码为 Java 8）和 Maven 3.9+，在仓库根目录执行：

```bash
mvn -f Mysql/source/pom.xml clean package -DskipTests
```

产物：

```text
Mysql/source/target/cost-lite-core-mysql-1.0.0.jar
```

嵌入式入口位于 `Mysql/starter/`，独立兼容服务位于 `Mysql/server/`。推荐在仓库根目录执行 `mvn -f Mysql/pom.xml clean install -DskipTests` 一次性构建。`target/`、`.class` 和日志只允许出现在本机构建目录，禁止提交。

数据库账号、密码、JDBC URL 和运行端口始终从外部环境变量或配置文件读取，不写入源码和 Jar。
