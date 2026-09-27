import { router, Stack, useLocalSearchParams } from "expo-router";
import { FlatList, RefreshControl, StyleSheet, Text, View } from "react-native";
import { getIsolationStatus, getParcel, listCropSeasons } from "@/api/endpoints";
import { Button } from "@/components/Button";
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
import type { CropSeason, IsolationStatus, Parcel } from "@/types";

interface SeasonsData {
  parcel: Parcel;
  seasons: CropSeason[];
  isolation: Record<number, IsolationStatus>;
}

async function loadSeasons(parcelCode: string): Promise<SeasonsData> {
  const [parcel, all] = await Promise.all([getParcel(parcelCode), listCropSeasons(parcelCode)]);
  const seasons = [...all].sort(
    (a, b) =>
      Number(b.status === "ACTIVE") - Number(a.status === "ACTIVE") ||
      b.startDate.localeCompare(a.startDate),
  );
  const statuses = await Promise.all(
    seasons.filter((s) => s.status === "ACTIVE").map((s) => getIsolationStatus(s.id)),
  );
  return { parcel, seasons, isolation: Object.fromEntries(statuses.map((s) => [s.cropSeasonId, s])) };
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
            ListHeaderComponent={data ? <ParcelActions parcel={data.parcel} /> : null}
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

function ParcelActions({ parcel }: { parcel: Parcel }) {
  const form = (mode: string) =>
    router.push({ pathname: "/parcels/new", params: { mode, id: String(parcel.id) } });
  return (
    <View style={styles.actions}>
      {parcel.approvalStatus === "PENDING" && (
        <Card>
          <StatusChip label="Chờ duyệt" tone="warning" />
          <Text style={styles.meta}>Quản trị viên chưa duyệt thửa này nên chưa tạo được vụ mùa.</Text>
        </Card>
      )}
      {parcel.approvalStatus === "REJECTED" && (
        <Card>
          <StatusChip label="Bị từ chối" tone="danger" />
          <Text style={styles.meta}>Lý do: {parcel.rejectionReason}</Text>
          <Button label="Sửa và gửi lại" onPress={() => form("resubmit")} />
        </Card>
      )}
      {parcel.approvalStatus === "APPROVED" && (
        <>
          <Button
            label="Bắt đầu vụ mùa mới"
            onPress={() =>
              router.push({ pathname: "/seasons/new", params: { parcelCode: parcel.code } })
            }
          />
          <Button label="Đề nghị chỉnh sửa thửa" variant="secondary" onPress={() => form("edit")} />
        </>
      )}
    </View>
  );
}

function SeasonItem({ season, isolation }: { season: CropSeason; isolation: IsolationStatus | null }) {
  const active = season.status === "ACTIVE";
  const approved = season.approvalStatus === "APPROVED";
  return (
    <View style={styles.item}>
      <IsolationBanner status={isolation} />
      <Card
        style={!active && styles.muted}
        onPress={
          active && approved
            ? () => router.push({ pathname: "/seasons/[id]", params: { id: String(season.id) } })
            : undefined
        }
      >
        <Text style={styles.variety}>{season.riceVariety}</Text>
        {season.code && <Text style={styles.meta}>{season.code}</Text>}
        {season.approvalStatus === "PENDING" && <StatusChip label="Chờ duyệt" tone="warning" />}
        {season.approvalStatus === "REJECTED" && (
          <>
            <StatusChip label="Bị từ chối" tone="danger" />
            <Text style={styles.meta}>Lý do: {season.rejectionReason}</Text>
            <Button
              label="Sửa và gửi lại"
              onPress={() =>
                router.push({
                  pathname: "/seasons/new",
                  params: { parcelCode: season.parcelCode, mode: "resubmit", id: String(season.id) },
                })
              }
            />
          </>
        )}
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
  actions: { gap: spacing.md, marginBottom: spacing.lg },
  muted: { opacity: 0.6 },
  variety: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.body, color: colors.textMuted },
});
