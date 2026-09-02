# 轻量计费前端完整集成教程

## 1. 组件定位

`Front/src` 是可嵌入的 Vue 3 工作台，只负责计费维护和联调界面，不依赖母体路由、菜单、权限模型、业务实体或请求封装。

工作台采用单一简化模式，包含：

- 场景新增、编辑、版本和生效状态维护。
- 场景下费目维护。
- 场景下要素和要素分组维护。
- 费目规则、条件、阶梯、固定费率和表达式配置。
- 发布前检查、创建版本和版本生效。
- 输入模板、同步试算、正式任务入口。
- 成功/失败计费日志、结果台账和结果追溯。

当前没有独立公式维护页面。规则可以选择运行端已有公式或填写表达式，公式页面后续可作为独立模块增加。

## 2. 前置条件

- Vue 3.3+。
- Element Plus 2.x。
- 宿主已提供 Axios 或等价 HTTP 请求函数。
- 后端 Starter 已暴露 `/cost-lite/**`，或网关已暴露带服务前缀的路径。

## 3. 复制组件

推荐复制到宿主源码目录：

```text
src/vendor/cost-lite-ui/
├─ CostLiteWorkbench.vue
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

const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost-lite" },
);
</script>

<template>
  <CostLiteWorkbench :api="costLiteApi" />
</template>
```

已有 AG Vue 3 项目的实际包装示例见 `Front/examples/ag-vue3-page.vue`。

## 5. 确定 `basePath`

| 部署方式 | 示例 |
| --- | --- |
| 浏览器直连业务服务 | `/cost-lite` |
| 网关按服务名转发 | `/business/cost-lite` |
| 网关统一 API 前缀 | `/api/business/cost-lite` |

`basePath` 必须是浏览器实际访问的宿主代理路径，不能填写独立计费 Jar 地址。Jar 地址只保存在后端配置中。

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

前端菜单权限只控制入口展示，后端必须继续保护 `/cost-lite/**`。

推荐最小角色：

- 计费实施管理员：场景、费目、要素、规则、发布、试算、日志和结果。
- 计费查看人员：配置只读、日志只读和结果只读。

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
4. 新增一条固定费率规则。
5. 发布并生效版本。
6. 获取输入模板并试算。
7. 查看成功日志。
8. 提交错误 JSON，确认失败日志能打开并显示原始输入。
9. 提交正式任务，确认结果和追溯页有数据。

## 11. 卸载

删除菜单、页面包装组件和 `src/vendor/cost-lite-ui` 即可撤下前端。后端 Starter、计费运行服务和历史计费数据不受影响。
