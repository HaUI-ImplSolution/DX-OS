// SPDX-License-Identifier: Apache-2.0
import { Linking, StyleSheet, Text, View } from "react-native";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { PassageText } from "@/components/PassageText";
import { StatusChip } from "@/components/StatusChip";
import type { KnowledgePassage } from "@/types";
import { colors, fontSize, spacing } from "@/theme";

// A passage quoted from a document with the sources it comes from.
export function KnowledgePassageCard({ passage }: { passage: KnowledgePassage }) {
  return (
    <Card>
      <Text style={styles.title}>{passage.documentTitle}</Text>
      <PassageText content={passage.content} />
      <Text style={styles.caption}>Độ liên quan {Math.round(passage.score * 100)}%</Text>
      {passage.sources.map((source) => (
        <View key={source.sourceId} style={styles.source}>
          <Text style={styles.caption}>
            Nguồn: {source.title}
            {source.organization ? ` — ${source.organization}` : ""}
          </Text>
          {source.reviewStatus === "IN_REVIEW" && <StatusChip label="Chưa chuyên gia duyệt" tone="warning" />}
          {source.url && (
            <Button label="Mở tài liệu" variant="secondary" onPress={() => void Linking.openURL(source.url!)} />
          )}
        </View>
      ))}
    </Card>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  caption: { fontSize: fontSize.caption, color: colors.textMuted },
  source: { gap: spacing.sm },
});
