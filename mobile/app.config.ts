import type { ConfigContext, ExpoConfig } from "expo/config";

// Cleartext HTTP is allowed outside the production EAS profile so dev/preview builds can reach a LAN backend.
export default ({ config }: ConfigContext): ExpoConfig => {
  const allowCleartext = process.env.EAS_BUILD_PROFILE !== "production";
  return {
    ...(config as ExpoConfig),
    ios: {
      ...config.ios,
      infoPlist: {
        ...config.ios?.infoPlist,
        ...(allowCleartext && { NSAppTransportSecurity: { NSAllowsArbitraryLoads: true } }),
      },
    },
    plugins: [
      ...(config.plugins ?? []),
      ["expo-build-properties", { android: { usesCleartextTraffic: allowCleartext } }],
    ],
  };
};
