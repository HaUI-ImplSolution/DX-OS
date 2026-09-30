import { StyleSheet, Text, View } from "react-native";
import { chipTones, fontSize, radius, spacing } from "@/theme";

interface Props {
  label: string;
  tone: keyof typeof chipTones;
}

export function StatusChip({ label, tone }: Props) {
  const { background, text } = chipTones[tone];
  return (
    <View style={[styles.chip, { backgroundColor: background }]}>
      <Text style={[styles.label, { color: text }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    alignSelf: "flex-start",
    borderRadius: radius.chip,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
  },
  label: { fontSize: fontSize.caption, fontWeight: "700" },
});
