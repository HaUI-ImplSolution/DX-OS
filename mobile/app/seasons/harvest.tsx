// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import { Alert, ScrollView, StyleSheet, Switch, Text, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { ApiError } from "@/api/client";
import { getCropSeason, getHarvestReadiness, harvestCropSeason } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { ErrorState } from "@/components/ErrorState";
import { Field } from "@/components/Field";
import { IsolationBanner } from "@/components/IsolationBanner";
import { SkeletonCards } from "@/components/Skeleton";
import { formatDate, todayLocal } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { chipTones, colors, fontSize, radius, spacing } from "@/theme";
import type { HarvestInput } from "@/types";

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

const toNumber = (value: string) => Number(value.replace(",", "."));

// Declares the harvest: the backend refuses it while the season is isolated, and asks for a
// confirmation and a reason when it is before the expected date.
export default function HarvestScreen() {
  const { seasonId } = useLocalSearchParams<{ seasonId: string }>();
  const id = Number(seasonId);
  const { data, error, loading, reload } = useAsync(
    async () => {
      const [season, readiness] = await Promise.all([getCropSeason(id), getHarvestReadiness(id)]);
      return { season, readiness };
    },
    [id],
  );
  const [date, setDate] = useState(todayLocal());
  const [yieldKg, setYieldKg] = useState("");
  const [area, setArea] = useState("");
  const [confirmEarly, setConfirmEarly] = useState(false);
  const [earlyReason, setEarlyReason] = useState("");
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  if (loading && !data) return <SkeletonCards />;
  if (error && !data) return <ErrorState message={error} onRetry={reload} />;
  if (!data) return null;

  const { season, readiness } = data;
  const expected = readiness.expectedHarvestDate;
  const early = expected !== null && DATE_PATTERN.test(date) && date < expected;
  const blocked = readiness.isolation.isolated;
  const yieldValue = toNumber(yieldKg);
  const areaValue = toNumber(area);
  const valid =
    DATE_PATTERN.test(date) &&
    yieldKg.trim() !== "" &&
    yieldValue > 0 &&
    (area.trim() === "" || areaValue > 0) &&
    (!early || (confirmEarly && earlyReason.trim() !== ""));

  const submit = async () => {
    setSending(true);
    setProblem(null);
    const input: HarvestInput = {
      actualHarvestDate: date,
      yieldKg: yieldValue,
      harvestedAreaHectares: area.trim() === "" ? undefined : areaValue,
      confirmEarly: early ? true : undefined,
      earlyReason: early ? earlyReason.trim() : undefined,
    };
    try {
      await harvestCropSeason(id, input);
      Alert.alert("Đã ghi nhận thu hoạch", `Vụ ${season.code ?? season.riceVariety} đã thu hoạch.`, [
        { text: "Đóng", onPress: () => router.dismissAll() },
      ]);
    } catch (e) {
      setProblem(e instanceof ApiError || e instanceof Error ? e.message : "Không gửi được. Thử lại sau.");
    } finally {
      setSending(false);
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>{season.riceVariety}</Text>
      {season.code && <Text style={styles.meta}>{season.code}</Text>}
      {expected && <Text style={styles.meta}>Dự kiến thu hoạch {formatDate(expected)}</Text>}

      <IsolationBanner status={readiness.isolation} />
      {blocked && readiness.isolation.message !== "" && (
        <Text style={styles.meta}>{readiness.isolation.message}</Text>
      )}

      {readiness.warnings.length > 0 && (
        <Card style={styles.warning}>
          <Text style={styles.warningTitle}>Còn việc chưa xử lý</Text>
          {readiness.warnings.map((warning) => (
            <Text key={warning.code} style={styles.warningText}>
              • {warning.message}
            </Text>
          ))}
          <Text style={styles.meta}>Bạn vẫn có thể thu hoạch.</Text>
        </Card>
      )}

      <Field
        label="Ngày thu hoạch (năm-tháng-ngày)"
        value={date}
        onChangeText={setDate}
        autoCapitalize="none"
        keyboardType="numbers-and-punctuation"
      />
      <Field
        label="Sản lượng (kg)"
        value={yieldKg}
        onChangeText={setYieldKg}
        keyboardType="decimal-pad"
      />
      <Field
        label="Diện tích thu hoạch (ha)"
        value={area}
        onChangeText={setArea}
        keyboardType="decimal-pad"
        hint="Để trống nếu thu hoạch cả thửa."
      />

      {early && (
        <Card style={styles.warning}>
          <Text style={styles.warningTitle}>
            Chưa đến ngày dự kiến ({formatDate(expected)})
          </Text>
          <View style={styles.switchRow}>
            <Switch value={confirmEarly} onValueChange={setConfirmEarly} />
            <Text style={styles.switchLabel}>Tôi xác nhận thu hoạch sớm</Text>
          </View>
          <Field
            label="Lý do thu hoạch sớm"
            value={earlyReason}
            onChangeText={setEarlyReason}
            maxLength={255}
          />
        </Card>
      )}

      {problem && <Text style={styles.error}>{problem}</Text>}
      <Button
        label={sending ? "Đang gửi…" : "Ghi nhận thu hoạch"}
        disabled={!valid || sending || blocked}
        onPress={submit}
      />
      <Button
        label="Chưa thu hoạch, đổi ngày dự kiến"
        variant="secondary"
        onPress={() =>
          router.replace({ pathname: "/seasons/reschedule", params: { seasonId: String(id) } })
        }
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.md },
  title: { fontSize: fontSize.title, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.body, color: colors.textMuted },
  warning: {
    backgroundColor: chipTones.warning.background,
    borderColor: colors.warning,
    borderRadius: radius.card,
    gap: spacing.sm,
  },
  warningTitle: { fontSize: fontSize.body, fontWeight: "700", color: colors.warning },
  warningText: { fontSize: fontSize.body, color: colors.text },
  switchRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  switchLabel: { fontSize: fontSize.body, color: colors.text, flexShrink: 1 },
  error: { fontSize: fontSize.body, color: colors.danger },
});
