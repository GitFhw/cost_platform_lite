# MySQL 计费核心源码

本目录是 MySQL 运行 Jar 的可维护源码，来自母体计费模块的必要边界，包含：

- 轻量运行入口、Controller、插件和数据库配置；
- 母体计费实体、Mapper、规则执行、公式、发布校验和试算服务；
- 轻量运行所需的通用兼容类和计费字典 Mapper；
- MySQL 运行资源和与母体字段一致的 Mapper XML。

这里没有复制母体后台、用户权限、菜单、账单、定时任务、代码生成等无关模块。客户业务项目不需要复制本目录，客户只使用上级 `runtime/cost-lite-server-1.0.0.jar`；本目录用于我们后续维护核心并重新打包。

## 构建

要求 JDK 17 和 Maven 3.9+，在仓库根目录执行：

```bash
mvn -f Mysql/source/pom.xml clean package -DskipTests
```

产物：

```text
Mysql/source/target/cost-lite-server.jar
```

确认接口、数据库和前端回归通过后，再将产物复制到 `Mysql/runtime/cost-lite-server-1.0.0.jar` 并更新同目录 `SHA256SUMS`。`target/`、`.class` 和日志只允许出现在本机构建目录，禁止提交。

数据库账号、密码、JDBC URL 和运行端口始终从外部环境变量或配置文件读取，不写入源码和 Jar。
