import type { App } from "vue";
import CostLiteWorkbench from "./CostLiteWorkbench.vue";
import { COST_LITE_API_KEY } from "./costLiteApi";
import type { CostLiteApi } from "./costLiteApi";

export * from "./costLiteApi";
export { CostLiteWorkbench };

export interface CostLiteUiOptions {
  api: CostLiteApi;
}

export function installCostLiteUi(app: App, options: CostLiteUiOptions): void {
  app.provide(COST_LITE_API_KEY, options.api);
  app.component("CostLiteWorkbench", CostLiteWorkbench);
}

export default CostLiteWorkbench;
