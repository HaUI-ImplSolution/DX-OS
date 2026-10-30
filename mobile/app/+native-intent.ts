// SPDX-License-Identifier: Apache-2.0
// The OAuth redirect (dxfarm://auth?code=...) is consumed by expo-auth-session, not by a route.
export function redirectSystemPath() {
  return "/";
}
