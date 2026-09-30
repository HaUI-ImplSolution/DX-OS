import { useCallback, useEffect, useRef } from "react";
import { router, useFocusEffect, useLocalSearchParams } from "expo-router";
import {
  FlatList,
  Image,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
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
import { useQueue } from "@/offline/QueueProvider";
import { reportStatus } from "@/reportStatus";
import { chipTones, colors, fontSize, radius, sizes, spacing } from "@/theme";
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
  const wasFocused = useRef(false);
  const { items, sentCount } = useQueue();
  const queued = items.filter((item) => item.cropSeasonId === cropSeasonId);
  const waiting = queued.filter((item) => item.status !== "failed").length;
  const failed = queued.length - waiting;
  const sentBefore = useRef(sentCount);
  const cardWidth = (width - spacing.lg * 2 - spacing.md) / 2;
  const { data, error, loading, refreshing, reload, refresh } = useAsync(
    () => loadReports(cropSeasonId),
    [cropSeasonId],
  );

  // Reload when returning from the capture screen; the first focus is covered by the initial load.
  useFocusEffect(
    useCallback(() => {
      if (wasFocused.current) refresh();
      wasFocused.current = true;
    }, [refresh]),
  );

  // A queued report was uploaded in the background: reload the list.
  useEffect(() => {
    if (sentCount !== sentBefore.current) {
      sentBefore.current = sentCount;
      refresh();
    }
  }, [sentCount, refresh]);

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
        ListHeaderComponent={
          <View style={styles.header}>
            <IsolationBanner status={data?.isolation ?? null} />
            {queued.length > 0 && (
              <Pressable
                accessibilityRole="button"
                onPress={() => router.push("/profile")}
                style={styles.queueStrip}
              >
                <Text style={styles.queueLabel}>
                  {waiting > 0 ? `Chờ gửi (${waiting})` : ""}
                  {waiting > 0 && failed > 0 ? " · " : ""}
                  {failed > 0 ? `Gửi lỗi (${failed})` : ""}
                </Text>
                <Text style={styles.queueHint}>Chạm để xem</Text>
              </Pressable>
            )}
          </View>
        }
        ListEmptyComponent={
          <EmptyState message="Chưa có báo cáo nào. Chụp ảnh lá hoặc thân cây bị bệnh để gửi." />
        }
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
        renderItem={({ item }) => <ReportCard report={item} width={cardWidth} />}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Chụp ảnh"
        onPress={() => router.push({ pathname: "/capture", params: {
              seasonId: String(cropSeasonId),
              ...(data?.isolation.parcelCode && { parcelCode: data.isolation.parcelCode }),
            },
          })}
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}
      >
        <Text style={styles.fabIcon}>📷</Text>
        <Text style={styles.fabLabel}>Chụp</Text>
      </Pressable>
    </View>
  );
}

function ReportCard({ report, width }: { report: DiseaseReport; width: number }) {
  const status = reportStatus(report.diagnosis);
  return (
    <Card
      style={[styles.card, { width }]}
      onPress={() => router.push({ pathname: "/reports/[id]", params: { id: String(report.id) } })}
    >
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
  list: { padding: spacing.lg, paddingBottom: sizes.fab + spacing.lg * 2, gap: spacing.md },
  row: { gap: spacing.md },
  card: { padding: spacing.sm, gap: spacing.sm },
  image: {
    width: "100%",
    aspectRatio: 1,
    borderRadius: radius.card - 4,
    backgroundColor: colors.border,
  },
  header: { gap: spacing.md, marginBottom: spacing.md },
  queueStrip: {
    backgroundColor: chipTones.warning.background,
    borderRadius: radius.card,
    padding: spacing.md,
    minHeight: sizes.touch,
    justifyContent: "center",
  },
  queueLabel: { fontSize: fontSize.body, fontWeight: "700", color: colors.warning },
  queueHint: { fontSize: fontSize.caption, color: colors.warning },
  fab: {
    position: "absolute",
    right: spacing.lg,
    bottom: spacing.lg,
    width: sizes.fab,
    height: sizes.fab,
    borderRadius: sizes.fab / 2,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    elevation: 4,
  },
  fabPressed: { opacity: 0.85 },
  fabIcon: { fontSize: 24 },
  fabLabel: { fontSize: fontSize.caption, fontWeight: "700", color: "#ffffff" },
  date: { fontSize: fontSize.caption, color: colors.textMuted },
});
