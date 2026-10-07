import { useCallback } from "react";
import { router, useFocusEffect } from "expo-router";
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/api/endpoints";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { Screen } from "@/components/Screen";
import { SkeletonCards } from "@/components/Skeleton";
import { formatDateTime } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { useNotifications } from "@/notifications/NotificationsProvider";
import { colors, fontSize, radius, spacing } from "@/theme";
import type { AppNotification } from "@/types";

function open(notification: AppNotification) {
  const ref = notification.relatedEntityRef;
  if (!ref) return;
  if (notification.relatedEntityType === "PARCEL") {
    router.push({ pathname: "/parcels/[code]", params: { code: ref } });
  } else if (notification.relatedEntityType === "CROP_SEASON") {
    router.push({ pathname: "/seasons/[id]", params: { id: ref } });
  } else if (notification.relatedEntityType === "DISEASE_REPORT") {
    router.push({ pathname: "/reports/[id]", params: { id: ref } });
  }
}

export default function NotificationsScreen() {
  const { data, error, loading, refreshing, reload, refresh } = useAsync(
    () => listNotifications(),
    [],
  );
  const { refreshUnread } = useNotifications();

  // Coming back from an opened item: the read state may have changed.
  useFocusEffect(
    useCallback(() => {
      refreshUnread();
    }, [refreshUnread]),
  );

  const readOne = async (notification: AppNotification) => {
    if (!notification.readAt) {
      await markNotificationRead(notification.id).catch(() => undefined);
      refresh();
      refreshUnread();
    }
    open(notification);
  };

  const readAll = async () => {
    await markAllNotificationsRead().catch(() => undefined);
    refresh();
    refreshUnread();
  };

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

  const items = data?.items ?? [];
  return (
    <View style={styles.container}>
      <FlatList
        data={items}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.list}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        ListHeaderComponent={
          items.some((item) => !item.readAt) ? (
            <View style={styles.header}>
              <Button label="Đánh dấu tất cả đã đọc" variant="secondary" onPress={readAll} />
            </View>
          ) : null
        }
        ListEmptyComponent={<EmptyState message="Chưa có thông báo nào." />}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              refresh();
              refreshUnread();
            }}
          />
        }
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            onPress={() => void readOne(item)}
            style={({ pressed }) => [
              styles.item,
              !item.readAt && styles.unread,
              pressed && styles.pressed,
            ]}
          >
            <Text style={[styles.title, !item.readAt && styles.unreadTitle]}>{item.title}</Text>
            <Text style={styles.body}>{item.body}</Text>
            <Text style={styles.time}>{formatDateTime(item.createdAt)}</Text>
          </Pressable>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  list: { padding: spacing.lg },
  header: { marginBottom: spacing.md },
  separator: { height: spacing.md },
  item: {
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.xs,
  },
  unread: { borderColor: colors.primary, borderLeftWidth: 4 },
  pressed: { opacity: 0.85 },
  title: { fontSize: fontSize.body, color: colors.text },
  unreadTitle: { fontWeight: "700" },
  body: { fontSize: fontSize.body, color: colors.text },
  time: { fontSize: fontSize.caption, color: colors.textMuted },
});
