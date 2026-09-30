import { StyleSheet, Text, View } from "react-native";
import { colors, fontSize, spacing } from "@/theme";

export function ProgressBar({ fraction }: { fraction: number }) {
  const percent = Math.round(Math.min(Math.max(fraction, 0), 1) * 100);
  return (
    <View accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 100, now: percent }}>
      <View style={styles.track}>
        <View style={[styles.fill, { width: `${percent}%` }]} />
      </View>
      <Text style={styles.label}>Đang gửi… {percent}%</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 8, borderRadius: 4, backgroundColor: colors.border, overflow: "hidden" },
  fill: { height: 8, backgroundColor: colors.primary },
  label: { fontSize: fontSize.caption, color: colors.textMuted, marginTop: spacing.xs, textAlign: "center" },
});
