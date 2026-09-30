import { useLocalSearchParams } from "expo-router";
import { FlatList, Image, RefreshControl, StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { getIsolationStatus, listDiseaseReports, reportImageUrl } from "@/api/endpoints";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { IsolationBanner } from "@/components/IsolationBanner";
import { Screen } from "@/components/Screen";
import { SkeletonCards } from "@/components/Skeleton";
import { StatusChip } from "@/components/StatusChip";
import { formatDateTime } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { reportStatus } from "@/reportStatus";
import { colors, fontSize, radius, spacing } from "@/theme";
import type { DiseaseReport, IsolationStatus } from "@/types";

interface ReportsData {
  reports: DiseaseReport[];
  isolation: IsolationStatus;
}

async function loadReports(cropSeasonId: number): Promise<ReportsData> {
  const [reports, isolation] = await Promise.all([
    listDiseaseReports(cropSeasonId),
    getIsolationStatus(cropSeasonId),
  ]);
  return {
    reports: [...reports].sort((a, b) => b.reportedAt.localeCompare(a.reportedAt)),
    isolation,
  };
}

export default function ReportsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const cropSeasonId = Number(id);
  const { width } = useWindowDimensions();
  const cardWidth = (width - spacing.lg * 2 - spacing.md) / 2;
  const { data, error, loading, refreshing, reload, refresh } = useAsync(
    () => loadReports(cropSeasonId),
    [cropSeasonId],
  );

  if (loading && !data) {
    return (
      <Screen>
        <SkeletonCards count={2} height={180} />
      </Screen>
    );
  }
  if (error && !data) {
    return (
      <Screen>
        <ErrorState message={error} onRetry={reload} />
      </Screen>
    );
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={data?.reports ?? []}
        keyExtractor={(report) => String(report.id)}
        numColumns={2}
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.list}
        ListHeaderComponent={<IsolationBanner status={data?.isolation ?? null} />}
        ListEmptyComponent={
          <EmptyState message="Chưa có báo cáo nào. Chụp ảnh lá hoặc thân cây bị bệnh để gửi." />
        }
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
        renderItem={({ item }) => <ReportCard report={item} width={cardWidth} />}
      />
    </View>
  );
}

function ReportCard({ report, width }: { report: DiseaseReport; width: number }) {
  const status = reportStatus(report.diagnosis);
  return (
    <Card style={[styles.card, { width }]}>
      <Image
        source={{ uri: reportImageUrl(report.imageRef) }}
        accessibilityLabel="Ảnh báo cáo sâu bệnh"
        style={styles.image}
      />
      <Text style={styles.date}>{formatDateTime(report.reportedAt)}</Text>
      <StatusChip label={status.label} tone={status.tone} />
    </Card>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  list: { padding: spacing.lg, gap: spacing.md },
  row: { gap: spacing.md },
  card: { padding: spacing.sm, gap: spacing.sm },
  image: {
    width: "100%",
    aspectRatio: 1,
    borderRadius: radius.card - 4,
    backgroundColor: colors.border,
  },
  date: { fontSize: fontSize.caption, color: colors.textMuted },
});
