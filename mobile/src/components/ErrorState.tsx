import { StyleSheet, Text, View } from "react-native";
import { Button } from "@/components/Button";
import { colors, fontSize, spacing } from "@/theme";

interface Props {
  message: string;
  onRetry: () => void;
}

export function ErrorState({ message, onRetry }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.message}>{message}</Text>
      <Button label="Thử lại" variant="secondary" onPress={onRetry} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: spacing.xl, gap: spacing.lg },
  message: { fontSize: fontSize.body, color: colors.danger, textAlign: "center" },
});
