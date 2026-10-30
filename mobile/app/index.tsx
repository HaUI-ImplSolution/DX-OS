// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { router, useFocusEffect } from "expo-router";
import { FlatList, RefreshControl, StyleSheet, View } from "react-native";
import { listParcels } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { ParcelCard } from "@/components/ParcelCard";
import { Screen } from "@/components/Screen";
import { SkeletonCards } from "@/components/Skeleton";
import { useAsync } from "@/hooks/useAsync";
import { useIsolatedParcels } from "@/hooks/useIsolatedParcels";
import { useNotifications } from "@/notifications/NotificationsProvider";
import { useQueue } from "@/offline/QueueProvider";
import { colors, spacing } from "@/theme";

export default function ParcelsScreen() {
  const { data, error, loading, refreshing, reload, refresh } = useAsync(listParcels, []);
  const [version, setVersion] = useState(0);
  const isolated = useIsolatedParcels(version);
  const { sentCount } = useQueue();
  const { refreshUnread } = useNotifications();

  // The unread count follows every return to this screen, not only a pull to refresh.
  useFocusEffect(
    useCallback(() => {
      refreshUnread();
    }, [refreshUnread]),
  );

  // A parcel sent from the offline queue shows up without a manual refresh.
  useEffect(() => {
    if (sentCount > 0) refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sentCount]);

  if (loading && !data) {
    return (
      <Screen>
        <SkeletonCards />
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
        data={data ?? []}
        keyExtractor={(parcel) => parcel.code}
        renderItem={({ item }) => <ParcelCard parcel={item} isolated={isolated.has(item.code)} />}
        contentContainerStyle={styles.list}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        ListEmptyComponent={
          <EmptyState message="Bạn chưa có thửa ruộng nào. Thêm thửa mới khi bạn đang đứng ở ruộng." />
        }
        ListFooterComponent={
          <View style={styles.footer}>
            <Button label="Thêm thửa mới" onPress={() => router.push("/parcels/new")} />
            <Button
              label="Hỏi đáp tài liệu"
              variant="secondary"
              onPress={() => router.push("/knowledge")}
            />
          </View>
        }
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setVersion((v) => v + 1);
              refresh();
              refreshUnread();
            }}
          />
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  list: { padding: spacing.lg },
  separator: { height: spacing.md },
  footer: { marginTop: spacing.lg, gap: spacing.md },
});
