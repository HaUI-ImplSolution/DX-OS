import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { colors, fontSize } from "@/theme";

export default function RootLayout() {
  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.surface },
          headerTintColor: colors.text,
          headerTitleStyle: { fontSize: fontSize.cardTitle, fontWeight: "700" },
          contentStyle: { backgroundColor: colors.bg },
        }}
      />
    </>
  );
}
