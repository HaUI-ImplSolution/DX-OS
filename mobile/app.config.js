// Cleartext HTTP is allowed outside the production EAS profile so dev/preview builds can reach a LAN backend.
/** @param {{ config: import("expo/config").ExpoConfig }} context */
module.exports = ({ config }) => {
  const allowCleartext = process.env.EAS_BUILD_PROFILE !== "production";
  return {
    ...config,
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
