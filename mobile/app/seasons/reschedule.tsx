// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import { Alert, ScrollView, StyleSheet, Text, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { ApiError } from "@/api/client";
import { getCropSeason, listHarvestReschedules, rescheduleHarvest } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { ErrorState } from "@/components/ErrorState";
import { Field } from "@/components/Field";
import { SkeletonCards } from "@/components/Skeleton";
import { formatDate, formatDateTime } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { colors, fontSize, spacing } from "@/theme";

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

// Moves the expected harvest date; every change stays in the history shown below the form.
export default function RescheduleScreen() {
  const { seasonId } = useLocalSearchParams<{ seasonId: string }>();
  const id = Number(seasonId);
  const { data, error, loading, reload } = useAsync(async () => {
    const [season, history] = await Promise.all([getCropSeason(id), listHarvestReschedules(id)]);
    return { season, history };
  }, [id]);
  const [date, setDate] = useState("");
  const [reason, setReason] = useState("");
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  if (loading && !data) return <SkeletonCards />;
  if (error && !data) return <ErrorState message={error} onRetry={reload} />;
  if (!data) return null;

  const valid = DATE_PATTERN.test(date) && reason.trim() !== "";

  const submit = async () => {
    setSending(true);
    setProblem(null);
    try {
      await rescheduleHarvest(id, { newExpectedHarvestDate: date, reason: reason.trim() });
      Alert.alert("Đã đổi ngày", `Ngày dự kiến thu hoạch mới: ${formatDate(date)}.`, [
        { text: "Đóng", onPress: () => router.back() },
      ]);
    } catch (e) {
      setProblem(e instanceof ApiError || e instanceof Error ? e.message : "Không gửi được. Thử lại sau.");
    } finally {
      setSending(false);
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>{data.season.riceVariety}</Text>
      <Text style={styles.meta}>
        Ngày dự kiến hiện tại: {formatDate(data.season.expectedHarvestDate)}
      </Text>

      <Field
        label="Ngày dự kiến mới (năm-tháng-ngày)"
        value={date}
        onChangeText={setDate}
        placeholder="2026-12-20"
        autoCapitalize="none"
        keyboardType="numbers-and-punctuation"
        hint="Từ hôm nay đến tối đa 90 ngày tới."
      />
      <Field label="Lý do" value={reason} onChangeText={setReason} maxLength={255} />
      {problem && <Text style={styles.error}>{problem}</Text>}
      <Button
        label={sending ? "Đang gửi…" : "Lưu ngày mới"}
        disabled={!valid || sending}
        onPress={submit}
      />

      {data.history.length > 0 && (
        <View style={styles.history}>
          <Text style={styles.subtitle}>Các lần đổi ngày</Text>
          {data.history.map((item, index) => (
            <Card key={item.id}>
              <Text style={styles.entry}>
                Lần {index + 1}: {formatDate(item.previousDate)} → {formatDate(item.newDate)}
              </Text>
              <Text style={styles.meta}>{item.reason}</Text>
              <Text style={styles.caption}>{formatDateTime(item.rescheduledAt)}</Text>
            </Card>
          ))}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.md },
  title: { fontSize: fontSize.title, fontWeight: "700", color: colors.text },
  subtitle: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.body, color: colors.textMuted },
  caption: { fontSize: fontSize.caption, color: colors.textMuted },
  entry: { fontSize: fontSize.body, fontWeight: "700", color: colors.text },
  history: { gap: spacing.sm, marginTop: spacing.lg },
  error: { fontSize: fontSize.body, color: colors.danger },
});
