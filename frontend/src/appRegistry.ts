// SPDX-License-Identifier: Apache-2.0
import type { AppRegistry } from "./shell";

// Each app is loaded on demand once the manifest names it, so one build serves every app.
const registry: AppRegistry = {
  farm: () => import("./apps/farm"),
  lab: () => import("./apps/lab"),
};

export default registry;
