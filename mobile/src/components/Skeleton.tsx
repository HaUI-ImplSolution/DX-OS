import { StyleSheet, View } from "react-native";
import { colors, radius, spacing } from "@/theme";

export function SkeletonCards({ count = 3, height = 96 }: { count?: number; height?: number }) {
  return (
    <View style={styles.container}>
      {Array.from({ length: count }, (_, index) => (
        <View key={index} style={[styles.card, { height }]} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.md },
  card: { backgroundColor: colors.border, borderRadius: radius.card, opacity: 0.6 },
});
