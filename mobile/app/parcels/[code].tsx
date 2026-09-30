import { router, Stack, useLocalSearchParams } from "expo-router";
import { FlatList, RefreshControl, StyleSheet, Text, View } from "react-native";
import { getIsolationStatus, listCropSeasons } from "@/api/endpoints";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { IsolationBanner } from "@/components/IsolationBanner";
import { Screen } from "@/components/Screen";
import { SkeletonCards } from "@/components/Skeleton";
import { StatusChip } from "@/components/StatusChip";
import { formatDate } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { colors, fontSize, spacing } from "@/theme";
import type { CropSeason, IsolationStatus } from "@/types";

interface SeasonsData {
  seasons: CropSeason[];
  isolation: Record<number, IsolationStatus>;
}

async function loadSeasons(parcelCode: string): Promise<SeasonsData> {
  const all = await listCropSeasons(parcelCode);
  const seasons = [...all].sort(
    (a, b) =>
      Number(b.status === "ACTIVE") - Number(a.status === "ACTIVE") ||
      b.startDate.localeCompare(a.startDate),
  );
  const statuses = await Promise.all(
    seasons.filter((s) => s.status === "ACTIVE").map((s) => getIsolationStatus(s.id)),
  );
  return { seasons, isolation: Object.fromEntries(statuses.map((s) => [s.cropSeasonId, s])) };
}

export default function SeasonsScreen() {
  const { code } = useLocalSearchParams<{ code: string }>();
  const { data, error, loading, refreshing, reload, refresh } = useAsync(
    () => loadSeasons(code),
    [code],
  );

  return (
    <>
      <Stack.Screen options={{ title: `Thửa ${code}` }} />
      {loading && !data ? (
        <Screen>
          <SkeletonCards />
        </Screen>
      ) : error && !data ? (
        <Screen>
          <ErrorState message={error} onRetry={reload} />
        </Screen>
      ) : (
        <View style={styles.container}>
          <FlatList
            data={data?.seasons ?? []}
            keyExtractor={(season) => String(season.id)}
            contentContainerStyle={styles.list}
            ItemSeparatorComponent={() => <View style={styles.separator} />}
            ListEmptyComponent={<EmptyState message="Thửa này chưa có vụ mùa." />}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
            renderItem={({ item }) => (
              <SeasonItem season={item} isolation={data?.isolation[item.id] ?? null} />
            )}
          />
        </View>
      )}
    </>
  );
}

function SeasonItem({ season, isolation }: { season: CropSeason; isolation: IsolationStatus | null }) {
  const active = season.status === "ACTIVE";
  return (
    <View style={styles.item}>
      <IsolationBanner status={isolation} />
      <Card
        style={!active && styles.muted}
        onPress={
          active
            ? () => router.push({ pathname: "/seasons/[id]", params: { id: String(season.id) } })
            : undefined
        }
      >
        <Text style={styles.variety}>{season.riceVariety}</Text>
        <Text style={styles.meta}>Bắt đầu {formatDate(season.startDate)}</Text>
        {season.expectedHarvestDate && (
          <Text style={styles.meta}>Dự kiến thu hoạch {formatDate(season.expectedHarvestDate)}</Text>
        )}
        {!active && <StatusChip label="Đã thu hoạch" tone="neutral" />}
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  list: { padding: spacing.lg },
  separator: { height: spacing.md },
  item: { gap: spacing.sm },
  muted: { opacity: 0.6 },
  variety: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.body, color: colors.textMuted },
});
