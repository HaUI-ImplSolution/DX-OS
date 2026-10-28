// SPDX-License-Identifier: Apache-2.0
import { StyleSheet, Text, View } from "react-native";
import { Card } from "@/components/Card";
import { colors, fontSize, spacing } from "@/theme";
import type { GeneratedExplanation } from "@/types";

export function GeneratedExplanationCard({ answer }: { answer: GeneratedExplanation }) {
  if (answer.mode !== "generated" || answer.reason || !answer.summary.trim()) return null;
  return (
    <Card>
      <Text style={styles.title}>Giải thích kết quả nhận diện</Text>
      <Text style={styles.caption}>AI tổng hợp từ tài liệu</Text>
      <Text style={styles.body}>{answer.summary}</Text>
      {answer.steps.length > 0 && (
        <View style={styles.points}>
          <Text style={styles.title}>Việc cần theo dõi</Text>
          {answer.steps.map((step, index) => <Text key={index} style={styles.body}>• {step}</Text>)}
        </View>
      )}
      {answer.note && <Text style={styles.caption}>Giới hạn cần lưu ý: {answer.note}</Text>}
    </Card>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: fontSize.body, fontWeight: "600", color: colors.text },
  body: { fontSize: fontSize.body, lineHeight: 24, color: colors.text },
  caption: { fontSize: fontSize.caption, lineHeight: 20, color: colors.textMuted },
  points: { gap: spacing.sm, marginTop: spacing.sm },
});
