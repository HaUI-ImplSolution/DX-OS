import { StyleSheet, Text, View } from "react-native";
import { passageFields, passageLines } from "@/passageText";
import { colors, fontSize, spacing } from "@/theme";

// A passage written as "Label: value" lines reads as a list of fields; any other passage keeps its
// line breaks.
export function PassageText({ content }: { content: string }) {
  const lines = passageLines(content);
  const fields = passageFields(lines);
  if (!fields) return <Text style={styles.value}>{lines.join("\n")}</Text>;
  return (
    <View style={styles.list}>
      {fields.map((field, index) => (
        <View key={`${index}-${field.label}`} style={styles.field}>
          <Text style={styles.label}>{field.label}</Text>
          <Text style={styles.value}>{field.value}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.sm },
  field: { gap: 2 },
  label: { fontSize: fontSize.caption, fontWeight: "700", color: colors.textMuted },
  value: { fontSize: fontSize.body, color: colors.text },
});
