import type { ReactNode } from "react";
import { StyleSheet, View } from "react-native";
import { colors, spacing } from "@/theme";

export function Screen({ children }: { children: ReactNode }) {
  return <View style={styles.container}>{children}</View>;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, padding: spacing.lg },
});
