<script setup lang="ts" name="CostLiteStandaloneJarPage">
import request from "@/utils/request";
import {
  CostLiteWorkbench,
  createCostLiteApi,
} from "@/vendor/cost-lite-ui";

/**
 * 独立 Jar 直连示例。Jar 地址由宿主网关或同源反向代理提供，
 * 页面只声明浏览器可访问的前缀和运行时路由模式。
 */
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  {
    basePath: "/cost",
    routeMode: "runtime",
  },
);
</script>

<template>
  <div class="cost-lite-page">
    <CostLiteWorkbench :api="costLiteApi" />
  </div>
</template>

<style scoped>
.cost-lite-page {
  min-height: 100%;
  overflow: auto;
  background: #edf1f5;
}
</style>
