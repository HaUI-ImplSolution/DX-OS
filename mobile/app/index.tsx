import { useState } from "react";
import { FlatList, RefreshControl, StyleSheet, View } from "react-native";
import { listParcels } from "@/api/endpoints";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { ParcelCard } from "@/components/ParcelCard";
import { Screen } from "@/components/Screen";
import { SkeletonCards } from "@/components/Skeleton";
import { useAsync } from "@/hooks/useAsync";
import { colors, spacing } from "@/theme";

export default function ParcelsScreen() {
  const { data, error, loading, refreshing, reload, refresh } = useAsync(listParcels, []);
  const [version, setVersion] = useState(0);

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
        renderItem={({ item }) => <ParcelCard parcel={item} version={version} />}
        contentContainerStyle={styles.list}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        ListEmptyComponent={
          <EmptyState message="Bạn chưa có thửa ruộng nào. Liên hệ hợp tác xã để được thêm thửa." />
        }
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setVersion((v) => v + 1);
              refresh();
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
});
