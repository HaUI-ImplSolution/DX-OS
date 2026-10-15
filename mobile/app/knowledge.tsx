import { useState } from "react";
import { Linking, ScrollView, StyleSheet, Text, View } from "react-native";
import { askKnowledge } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/EmptyState";
import { Field } from "@/components/Field";
import { StatusChip } from "@/components/StatusChip";
import type { KnowledgeAnswer } from "@/types";
import { colors, fontSize, spacing } from "@/theme";

// Questions answered from the disease documents: passages are quoted with the sources they come from.
export default function KnowledgeScreen() {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<KnowledgeAnswer | null>(null);
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ask = async () => {
    const text = question.trim();
    if (!text) return;
    setAsking(true);
    setError(null);
    try {
      setAnswer(await askKnowledge(text));
    } catch (e) {
      setAnswer(null);
      setError(e instanceof Error ? e.message : "Không hỏi được, hãy thử lại sau.");
    } finally {
      setAsking(false);
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Field
        label="Câu hỏi"
        placeholder="Ví dụ: Lúa bị đạo ôn thì xử lý thế nào?"
        value={question}
        onChangeText={setQuestion}
        maxLength={1000}
        multiline
        returnKeyType="search"
      />
      <Button label={asking ? "Đang tìm…" : "Hỏi"} onPress={() => void ask()} disabled={asking || !question.trim()} />

      {error && <Text style={styles.error}>{error}</Text>}

      {answer && answer.passages.length === 0 && (
        <EmptyState message="Chưa tìm thấy đoạn tài liệu phù hợp. Hãy hỏi cụ thể hơn hoặc nêu tên bệnh." />
      )}

      {answer?.passages.map((passage, index) => (
        <Card key={index}>
          <Text style={styles.title}>{passage.documentTitle}</Text>
          <Text style={styles.body}>{passage.content}</Text>
          <Text style={styles.caption}>Độ liên quan {Math.round(passage.score * 100)}%</Text>
          {passage.sources.map((source) => (
            <View key={source.sourceId} style={styles.source}>
              <Text style={styles.caption}>
                Nguồn: {source.title}
                {source.organization ? ` — ${source.organization}` : ""}
              </Text>
              {source.reviewStatus === "IN_REVIEW" && (
                <StatusChip label="Chưa chuyên gia duyệt" tone="warning" />
              )}
              {source.url && (
                <Button label="Mở tài liệu" variant="secondary" onPress={() => void Linking.openURL(source.url!)} />
              )}
            </View>
          ))}
        </Card>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.lg },
  title: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  body: { fontSize: fontSize.body, color: colors.text },
  caption: { fontSize: fontSize.caption, color: colors.textMuted },
  source: { gap: spacing.sm },
  error: { fontSize: fontSize.body, color: colors.danger },
});
