import { useState } from "react";
import { router, useLocalSearchParams } from "expo-router";
import { Image, RefreshControl, ScrollView, StyleSheet, Text, View } from "react-native";
import { ApiError } from "@/api/client";
import { getDiseaseReport, getTreatmentApplication, reportSpraying } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { ErrorState } from "@/components/ErrorState";
import { Field } from "@/components/Field";
import { Screen } from "@/components/Screen";
import { SkeletonCards } from "@/components/Skeleton";
import { StatusChip } from "@/components/StatusChip";
import { Timeline, type TimelineStep } from "@/components/Timeline";
import { formatDate, formatDateTime, todayLocal } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { reportStatus } from "@/reportStatus";
import { colors, fontSize, radius, spacing } from "@/theme";
import type { Diagnosis, DiseaseReport, TreatmentApplication } from "@/types";

function timelineSteps(diagnosis: Diagnosis | null): TimelineStep[] {
  const status = diagnosis?.approvalStatus;
  return [
    { label: "Đã gửi", state: "done" },
    { label: "Đã chẩn đoán", state: diagnosis ? "done" : "pending" },
    {
      label: status === "REJECTED" ? "Kỹ sư từ chối" : "Kỹ sư duyệt",
      state: status === "APPROVED" ? "done" : status === "REJECTED" ? "failed" : "pending",
    },
  ];
}

function doseText(rule: NonNullable<Diagnosis["treatmentRule"]>) {
  const amount = rule.doseMin === rule.doseMax ? `${rule.doseMin}` : `${rule.doseMin}–${rule.doseMax}`;
  return `${amount} ${rule.doseUnit}`;
}

function DiagnosisSection({ report }: { report: DiseaseReport }) {
  if (report.status === "NEEDS_RETAKE") {
    return (
      <Card style={styles.rejected}>
        <Text style={styles.cardTitle}>Cần chụp lại ảnh</Text>
        <Text style={styles.body}>{report.retakeReason}</Text>
      </Card>
    );
  }
  const diagnosis = report.diagnosis;
  if (!diagnosis) {
    return <Text style={styles.note}>Đang chờ chẩn đoán.</Text>;
  }
  if (diagnosis.approvalStatus === "PENDING") {
    return <Text style={styles.note}>Kỹ sư đang xem xét.</Text>;
  }
  if (diagnosis.approvalStatus === "REJECTED") {
    return (
      <Card style={styles.rejected}>
        <Text style={styles.cardTitle}>Kỹ sư từ chối khuyến nghị</Text>
        {diagnosis.rejectionReason && <Text style={styles.body}>{diagnosis.rejectionReason}</Text>}
      </Card>
    );
  }
  const rule = diagnosis.treatmentRule;
  return (
    <Card>
      <Text style={styles.cardTitle}>{diagnosis.diseaseName}</Text>
      {diagnosis.recommendationText && <Text style={styles.body}>{diagnosis.recommendationText}</Text>}
      {rule && (
        <View style={styles.facts}>
          <Text style={styles.body}>Thuốc: {rule.pesticideCode}</Text>
          <Text style={styles.body}>Liều: {doseText(rule)}</Text>
          <Text style={styles.body}>Số ngày cách ly: {rule.phiDays} ngày</Text>
        </View>
      )}
    </Card>
  );
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

interface ReportData {
  report: DiseaseReport;
  spraying: TreatmentApplication | null;
}

// The farmer reports the spraying; the engineer confirms it, and until then the season stays
// blocked for harvest.
function SprayingSection({ data, onChanged }: { data: ReportData; onChanged: () => void }) {
  const { report, spraying } = data;
  const rule = report.diagnosis?.treatmentRule;
  const [dose, setDose] = useState(rule ? String((rule.doseMin + rule.doseMax) / 2) : "");
  const [date, setDate] = useState(todayLocal());
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  if (!rule) return null;

  if (spraying && spraying.confirmationStatus !== "REJECTED") {
    const confirmed = spraying.confirmationStatus === "CONFIRMED";
    return (
      <Card>
        <StatusChip
          label={confirmed ? "Kỹ sư đã xác nhận" : "Chờ kỹ sư xác nhận"}
          tone={confirmed ? "success" : "warning"}
        />
        <Text style={styles.body}>
          Đã phun {spraying.doseAmountSnapshot} {spraying.doseUnitSnapshot} ngày{" "}
          {formatDate(spraying.appliedAt)}.
        </Text>
        <Text style={styles.body}>Cách ly đến hết ngày {formatDate(spraying.harvestLockedUntil)}.</Text>
        {!confirmed && (
          <Text style={styles.caption}>Trong lúc chờ, vụ này chưa thu hoạch được.</Text>
        )}
      </Card>
    );
  }

  const doseValue = Number(dose.replace(",", "."));
  const valid = doseValue > 0 && DATE_PATTERN.test(date);
  const submit = async () => {
    setSending(true);
    setProblem(null);
    try {
      await reportSpraying(report.id, { doseAmount: doseValue, appliedAt: date });
      onChanged();
    } catch (e) {
      setProblem(e instanceof ApiError || e instanceof Error ? e.message : "Không gửi được. Thử lại sau.");
    } finally {
      setSending(false);
    }
  };

  return (
    <Card>
      <Text style={styles.cardTitle}>Tôi đã phun thuốc</Text>
      {spraying?.confirmationStatus === "REJECTED" && (
        <Text style={styles.error}>
          Kỹ sư chưa xác nhận lần khai trước. Lý do: {spraying.rejectionReason}
        </Text>
      )}
      <Field
        label={`Liều đã phun (${rule.doseUnit})`}
        value={dose}
        onChangeText={setDose}
        keyboardType="decimal-pad"
        hint={`Trong khoảng ${rule.doseMin}–${rule.doseMax}.`}
      />
      <Field
        label="Ngày phun (năm-tháng-ngày)"
        value={date}
        onChangeText={setDate}
        autoCapitalize="none"
        keyboardType="numbers-and-punctuation"
      />
      {problem && <Text style={styles.error}>{problem}</Text>}
      <Button
        label={sending ? "Đang gửi…" : "Gửi cho kỹ sư xác nhận"}
        disabled={!valid || sending}
        onPress={submit}
      />
    </Card>
  );
}

export default function ReportDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { data, error, loading, refreshing, reload, refresh } = useAsync(async () => {
    const report = await getDiseaseReport(Number(id));
    const approved = report.diagnosis?.approvalStatus === "APPROVED";
    const spraying = approved ? await getTreatmentApplication(report.id) : null;
    return { report, spraying };
  }, [id]);
  const report = data?.report;

  if (loading && !report) {
    return (
      <Screen>
        <SkeletonCards count={2} height={160} />
      </Screen>
    );
  }
  if (!report) {
    return (
      <Screen>
        <ErrorState message={error ?? "Không tải được báo cáo."} onRetry={reload} />
      </Screen>
    );
  }

  const status = reportStatus(report.diagnosis, report.status);
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
    >
      <Image
        source={{ uri: report.imageUrl }}
        accessibilityLabel="Ảnh báo cáo sâu bệnh"
        style={styles.image}
        resizeMode="cover"
      />
      <View style={styles.header}>
        <StatusChip label={status.label} tone={status.tone} />
        <Text style={styles.caption}>
          {report.reportedBy} · {formatDateTime(report.reportedAt)}
        </Text>
      </View>
      <Timeline steps={timelineSteps(report.diagnosis)} />
      {report.retakeOf && (
        <Button
          variant="secondary"
          label="Xem ảnh trước"
          onPress={() => router.push({ pathname: "/reports/[id]", params: { id: String(report.retakeOf) } })}
        />
      )}
      {report.retakeIds.map((child) => (
        <Button
          key={child}
          variant="secondary"
          label={`Xem ảnh chụp lại #${child}`}
          onPress={() => router.push({ pathname: "/reports/[id]", params: { id: String(child) } })}
        />
      ))}
      <DiagnosisSection report={report} />
      {data && report.diagnosis?.approvalStatus === "APPROVED" && (
        <SprayingSection key={data.spraying?.confirmationStatus ?? "none"} data={data} onChanged={refresh} />
      )}
      {report.status === "NEEDS_RETAKE" && report.retakeIds.length === 0 && (
        <Button
          label="Chụp lại"
          onPress={() =>
            router.replace({ pathname: "/capture", params: { seasonId: String(report.cropSeasonId), retakeOf: String(report.id) } })
          }
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.lg },
  image: { width: "100%", aspectRatio: 4 / 3, borderRadius: radius.card, backgroundColor: colors.border },
  header: { gap: spacing.sm },
  cardTitle: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  body: { fontSize: fontSize.body, color: colors.text },
  facts: { marginTop: spacing.sm, gap: spacing.xs },
  caption: { fontSize: fontSize.caption, color: colors.textMuted },
  note: { fontSize: fontSize.body, color: colors.textMuted },
  rejected: { borderColor: colors.danger },
  error: { fontSize: fontSize.body, color: colors.danger },
});
