import { StyleSheet, Text, View } from "react-native";
import { colors, fontSize, spacing } from "@/theme";

export interface TimelineStep {
  label: string;
  state: "done" | "pending" | "failed";
}

const MARKS = { done: "✓", pending: "…", failed: "✕" } as const;

export function Timeline({ steps }: { steps: TimelineStep[] }) {
  return (
    <View style={styles.container}>
      {steps.map((step) => (
        <View key={step.label} style={styles.step}>
          <View style={[styles.dot, dotStyles[step.state]]}>
            <Text style={[styles.mark, step.state === "pending" && styles.pendingMark]}>
              {MARKS[step.state]}
            </Text>
          </View>
          <Text style={[styles.label, step.state === "pending" && styles.pendingLabel]}>{step.label}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.md },
  step: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  dot: { width: 28, height: 28, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  mark: { fontSize: fontSize.caption, fontWeight: "700", color: "#ffffff" },
  pendingMark: { color: colors.textMuted },
  label: { fontSize: fontSize.body, color: colors.text, flexShrink: 1 },
  pendingLabel: { color: colors.textMuted },
});

const dotStyles = StyleSheet.create({
  done: { backgroundColor: colors.primary },
  pending: { backgroundColor: colors.border },
  failed: { backgroundColor: colors.danger },
});
