<script setup lang="ts" name="CostLiteWorkbenchPage">
import request from "@/utils/request";
import {
  CostLiteWorkbench,
  createCostLiteApi,
} from "@/vendor/cost-lite-ui";

const costLiteApi = createCostLiteApi(
  (config) => request(config),
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
