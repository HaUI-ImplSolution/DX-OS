import { StyleSheet, Text, View } from "react-native";
import { colors, fontSize, spacing } from "@/theme";

export function EmptyState({ message }: { message: string }) {
  return (
    <View style={styles.container}>
      <Text style={styles.message}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: spacing.xl, alignItems: "center" },
  message: { fontSize: fontSize.body, color: colors.textMuted, textAlign: "center" },
});
