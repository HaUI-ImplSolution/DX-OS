import { StyleSheet, Text, View } from "react-native";
import { formatDate } from "@/format";
import { chipTones, colors, fontSize, radius, spacing } from "@/theme";
import type { IsolationStatus } from "@/types";

// Renders nothing unless the season is under pesticide isolation.
export function IsolationBanner({ status }: { status: IsolationStatus | null }) {
  if (!status?.isolated) return null;
  return (
    <View style={styles.banner} accessibilityRole="alert">
      <Text style={styles.title}>
        Chưa được thu hoạch, còn {status.daysRemaining} ngày (đến {formatDate(status.harvestLockedUntil)})
      </Text>
      {status.pesticideCode && <Text style={styles.caption}>Thuốc đã phun: {status.pesticideCode}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: chipTones.warning.background,
    borderColor: colors.warning,
    borderWidth: 1,
    borderRadius: radius.card,
    padding: spacing.lg,
    gap: spacing.xs,
  },
  title: { fontSize: fontSize.body, fontWeight: "700", color: colors.warning },
  caption: { fontSize: fontSize.caption, color: colors.warning },
});
