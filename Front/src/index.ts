import type { App } from "vue";
import CostLiteWorkbench from "./CostLiteWorkbench.vue";
import {
  COST_LITE_API_KEY,
  COST_LITE_PERMISSION_KEY,
  type CostLitePermission,
  type CostLitePermissionResolver,
} from "./costLiteApi";
import type { CostLiteApi } from "./costLiteApi";
import RateMatrixEditor from "./RateMatrixEditor.vue";

export * from "./costLiteApi";
export * from "./operatorPolicy.js";
export * from "./rateMatrix.js";
export { CostLiteWorkbench };
export { RateMatrixEditor };

export interface CostLiteUiOptions {
  api: CostLiteApi;
  permission?: CostLitePermissionResolver;
}

export function installCostLiteUi(app: App, options: CostLiteUiOptions): void {
  app.provide(COST_LITE_API_KEY, options.api);
  app.provide(COST_LITE_PERMISSION_KEY, options.permission || (() => true));
  if (!app.directive("hasPermi")) {
    const permission = options.permission || (() => true);
    app.directive("hasPermi", {
      mounted: (el, binding) => applyPermission(el, binding, permission),
      updated: (el, binding) => applyPermission(el, binding, permission),
    });
  }
  app.component("CostLiteWorkbench", CostLiteWorkbench);
  app.component("RateMatrixEditor", RateMatrixEditor);
}

function applyPermission(
  el: HTMLElement,
  binding: { value?: CostLitePermission },
  permission: CostLitePermissionResolver = () => true,
): void {
  const allowed = permission(binding.value);
  if (allowed) {
    el.removeAttribute("data-cost-lite-permission-hidden");
    el.style.removeProperty("display");
    return;
  }
  el.setAttribute("data-cost-lite-permission-hidden", "true");
  el.style.display = "none";
}

export default CostLiteWorkbench;
