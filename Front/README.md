# 轻量计费前端完整集成教程

## 1. 组件定位

`Front/src` 是 Oracle 和 MySQL 共用的一套 Vue 3 工作台，只负责计费维护和联调界面，不依赖母体路由、菜单、权限模型、业务实体、宿主字典接口或请求封装。工作台启动时通过宿主暴露的 `/cost/dictionary/options` 读取当前计费数据源中的计费字典，因此宿主项目不需要复制字典 JS。

Jar 连接目标库中按 `Mysql/sql/cost-lite-schema.sql` 或 `Oracle/sql/cost-lite-schema.sql` 初始化的同名 `cost_*` 表，字段与母体实体保持一致，并只读取轻量 SQL 同步初始化的 `sys_dict_type`、`sys_dict_data` 两张字典表；不要求目标系统迁移母体源码、其他 `sys_*` 表或无关业务表。前端只通过宿主接口读取字典，不直接连接数据库。

工作台采用单一简化模式，包含：

- 场景新增、编辑、版本和生效状态维护。
- 场景下费目维护。
- 场景下要素和要素分组维护。
- 费目规则、条件、阶梯、固定费率和表达式配置。
- 基于费目要素类型生成动态费率矩阵：字典/布尔使用受控下拉，数值使用严格数字输入并支持比较符，文本使用文本输入；操作符按单元格保存，不再由客户手工拼接规则字符串。
- 场景级公式资产维护、在线试算、版本查看和回退。
- 发布前检查、创建版本和版本生效。
- 输入模板、真实业务数据试算和试算结果预览。
- 成功/失败试算日志，日志详情中保留输入、要素、解释和试算结果。

动态矩阵只接纳低风险、可审计的要素类型。平台字典或业务字典必须先返回可用选项快照，停用选项保留用于历史显示但不可新选；字典列支持 `EQ/IN`（属于任一，多选），布尔列支持 `EQ`，数值列支持 `EQ/GT/GE/LT/LE`，文本列收敛为 `EQ`。日期、日期时间、每日时段、公式和高基数主数据继续走高级条件编辑器或宿主适配器；轻量执行链已对日期/时间使用类型化比较，矩阵不把时间条件伪装成普通文本。

公式维护放在同一个工作台的“公式”页中，不新增第二套菜单。公式是按场景归属的独立资产，可维护中文业务口径、标准执行表达式、返回类型、状态、测试输入、版本和回退；规则仍负责适用条件和费率命中。公式页依赖运行端已有的 `cost_formula`、`cost_formula_version` 接口，删除该页只会撤掉维护入口，不会影响运行核心。

后端支持同进程 Starter、独立 Jar 和 HTTP 代理；前端统一通过 transport 访问稳定的 `/cost/**` 接口，不绑定某个宿主路由或权限实现。

Oracle 和 MySQL 的差异只在后端运行 Jar 与初始化 SQL；两套 Jar 都提供相同的工作台接口语义，业务项目不需要为数据库类型复制一套页面。

## 2. 前置条件

- Vue 3.3+。
- Element Plus 2.x。
- 宿主已提供 Axios 或等价 HTTP 请求函数。
- 后端 Starter 已暴露 `/cost/**`，或网关已暴露带服务前缀的路径。

## 3. 复制组件

推荐复制到宿主源码目录：

```text
src/vendor/cost-lite-ui/
├─ CostLiteWorkbench.vue
├─ RateMatrixEditor.vue
├─ rateMatrix.js
├─ operatorPolicy.js
├─ ConditionValueEditor.vue
├─ costLiteApi.ts
└─ index.ts
```

也可以把 `Front/` 发布到企业 npm 仓库，再按包依赖接入。

## 4. 创建宿主页

通用示例见 `Front/examples/generic-page.vue`。核心代码如下：

```vue
<script setup lang="ts">
import request from "@/utils/request";
import {
  CostLiteWorkbench,
  createCostLiteApi,
} from "@/vendor/cost-lite-ui";

const costLiteApi = createCostLiteApi((config) => request(config), { basePath: "/cost" });
</script>

<template>
  <CostLiteWorkbench :api="costLiteApi" />
</template>
```

`basePath` 只填写宿主浏览器实际访问的计费根路径；同进程 Starter、独立 Jar 和 HTTP 代理都通过宿主的 transport 统一适配。

如果目标项目需要调整中文名称或可选值，直接修改目标轻量库中的对应字典数据即可，工作台刷新后自动生效：

```http
GET /cost/dictionary/options?types=cost_business_domain,cost_scene_status,cost_rule_operator
```

字典接口只接受 `cost_` 开头的类型，返回值来自当前 Jar 连接的轻量库；第三方系统无需接入宿主字典表。

已有 AG Vue 3 项目的实际包装示例见 `Front/examples/ag-vue3-page.vue`。

如果业务服务不引入 Starter，而是由独立 Jar 直接提供计费接口，仍然使用同一个工作台，只需让 transport 指向该 Jar 的稳定路径：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost" },
);
```

## 5. 确定 `basePath`

| 部署方式 | `basePath` 示例 |
| --- | --- |
| 同进程 Starter 或独立 Jar | `/cost` |
| HTTP Starter 宿主服务 | `/cost` |
| 网关按服务名转发 | `/business/cost` |
| 网关统一 API 前缀 | `/api/business/cost` |

`basePath` 始终填写浏览器实际访问的路径，不填写 `http://host:port` 形式的 Jar 地址；调用方不用手工维护场景、费目、要素、规则等接口地址。

如果独立 Jar 或同进程 Starter 被网关挂在 `/business/cost`，仍然只改 `basePath`：

```ts
{
  basePath: "/business/cost",
}
```

## 6. 请求适配要求

`createCostLiteApi` 接收的 transport 函数输入结构：

```ts
interface CostLiteRequest {
  method: "GET" | "POST" | "PUT" | "DELETE";
  url: string;
  params?: Record<string, unknown>;
  data?: unknown;
}
```

返回值可以是 AxiosResponse，也可以是项目已经解包后的业务响应。适配器会识别常见的 `data`、`code`、`rows` 和 `total` 结构。

## 7. 菜单与路由

通用菜单字段见 `Front/menu-manifest.yml`。宿主只需要一个菜单：

```text
名称：轻量计费
路由：/cost/lite
组件：宿主创建的页面包装组件
图标：calculator
```

不同业务系统菜单表差异较大，因此本目录不硬编码宿主菜单 SQL。实施时把清单字段翻译到宿主菜单模型即可。

## 8. 权限

前端菜单权限只控制入口展示，后端必须继续保护同进程 Starter 或独立 Jar 的 `/cost/**` 管理路径；HTTP 代理还要保护代理入口。

推荐最小角色：

- 计费实施管理员：场景、费目、要素、规则、公式、发布、试算和试算日志。
- 计费查看人员：配置只读和试算日志只读。

工作台不提供同步计费、正式任务提交或正式结果台账页面。同步计费是业务系统后端的生产调用接口，工作台只用试算验证配置是否能处理真实业务数据。

对于货种、客户、船舶等高基数要素，宿主应在变量上配置 `BUSINESS_DICT` 或 `BUSINESS_MASTER` 选项来源，并实现后端 `CostLiteOptionProvider`，返回编码、名称、禁用状态和分页信息；工作台只保存稳定编码，不把业务表复制进轻量库。平台字典使用 `PLATFORM_DICT`，从当前计费库的 `cost_*` 字典读取。

接入业务选项时，变量的运行时 `sourceType` 与规则编辑器的 `optionSourceType` 必须分开：业务字典通常是 `sourceType=INPUT`、`optionSourceType=BUSINESS_DICT`，不能把宿主业务字典误标成轻量平台 `DICT`。后端会复核目录编码、停用选项和配置 JSON。

## 9. 构建验证

在宿主项目执行已有命令，例如：

```bash
pnpm exec vue-tsc --noEmit
pnpm build
```

如果项目本身存在历史类型错误，应至少保证生产构建通过，并确认新增的 `Front/src` 和页面包装组件没有出现在错误列表中。

## 10. 页面联调

1. 打开轻量计费菜单，确认健康状态正常。
2. 新增一个场景。
3. 在场景下新增费目和要素。
4. 在“公式”页新增公式，先使用“试算当前内容”验证表达式和输入样例。
5. 在费目下新增一条固定费率或公式金额规则；规则负责条件，公式负责金额表达式。
6. 发布并生效版本。
7. 获取输入模板，替换为一条真实业务数据并执行试算。
8. 查看试算结果预览和成功日志。
9. 提交错误 JSON，确认失败日志能打开并显示原始输入。
10. 点击“接口示例”，确认场景级和指定费目的请求体、Starter 和独立 Jar 调用写法正确。

## 11. 卸载

删除菜单、页面包装组件和 `src/vendor/cost-lite-ui` 即可撤下前端。后端 Starter、计费运行服务和历史计费数据不受影响。
