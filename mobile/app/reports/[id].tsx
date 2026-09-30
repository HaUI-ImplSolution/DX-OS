import { router, useLocalSearchParams } from "expo-router";
import { Image, RefreshControl, ScrollView, StyleSheet, Text, View } from "react-native";
import { getDiseaseReport, reportImageUrl } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { ErrorState } from "@/components/ErrorState";
import { Screen } from "@/components/Screen";
import { SkeletonCards } from "@/components/Skeleton";
import { StatusChip } from "@/components/StatusChip";
import { Timeline, type TimelineStep } from "@/components/Timeline";
import { formatDateTime } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { reportStatus } from "@/reportStatus";
import { colors, fontSize, radius, spacing } from "@/theme";
import type { Diagnosis, DiseaseReport } from "@/types";

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
        <Text style={styles.cardTitle}>Kỹ sư yêu cầu chụp lại</Text>
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
      <Text style={styles.caption}>Việc phun thuốc do kỹ sư ghi nhận.</Text>
    </Card>
  );
}

export default function ReportDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { data: report, error, loading, refreshing, reload, refresh } = useAsync(
    () => getDiseaseReport(Number(id)),
    [id],
  );

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

  const status = reportStatus(report.diagnosis);
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
    >
      <Image
        source={{ uri: reportImageUrl(report.imageRef) }}
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
      <DiagnosisSection report={report} />
      {report.diagnosis?.approvalStatus === "REJECTED" && (
        <Button
          label="Chụp lại"
          onPress={() =>
            router.replace({ pathname: "/capture", params: { seasonId: String(report.cropSeasonId) } })
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
});
