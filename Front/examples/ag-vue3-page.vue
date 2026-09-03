<route lang="yaml">
meta:
  title: 轻量计费
  auth: true
  cache: false
  icon: carbon:calculator
</route>

<script setup lang="ts" name="CostLiteWorkbenchPage">
import api from "@/api";
import {
  CostLiteWorkbench,
  createCostLiteApi,
} from "@/vendor/cost-lite-ui";

/**
 * 业务系统只依赖 Starter 暴露的稳定代理路径，计费服务地址和令牌由后端配置托管。
 */
const costLiteApi = createCostLiteApi(
  (request) => api.request(request),
  { basePath: "/cost" },
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
