import { Alert, Image, ScrollView, StyleSheet, Text, View } from "react-native";
import Constants from "expo-constants";
import { useAuth } from "@/auth/AuthProvider";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { StatusChip } from "@/components/StatusChip";
import { formatDateTime } from "@/format";
import type { QueueItem } from "@/offline/queue";
import { useQueue } from "@/offline/QueueProvider";
import { colors, fontSize, radius, spacing } from "@/theme";

const STATUS = {
  pending: { label: "Đang chờ", tone: "neutral" },
  sending: { label: "Đang gửi", tone: "warning" },
  failed: { label: "Lỗi", tone: "danger" },
} as const;

function QueueRow({ item, onRemove }: { item: QueueItem; onRemove: () => void }) {
  const status = STATUS[item.status];
  const confirmRemove = () =>
    Alert.alert("Xoá ảnh chờ gửi", "Ảnh này sẽ bị xoá và không được gửi. Bạn có chắc không?", [
      { text: "Huỷ", style: "cancel" },
      { text: "Xoá", style: "destructive", onPress: onRemove },
    ]);

  return (
    <Card>
      <View style={styles.row}>
        <Image source={{ uri: item.uri }} style={styles.thumbnail} accessibilityLabel="Ảnh chờ gửi" />
        <View style={styles.rowText}>
          <Text style={styles.rowTitle}>
            {item.parcelCode ? `Thửa ${item.parcelCode}` : `Vụ mùa #${item.cropSeasonId}`}
          </Text>
          <Text style={styles.caption}>{formatDateTime(item.createdAt)}</Text>
          <StatusChip label={status.label} tone={status.tone} />
        </View>
      </View>
      {item.status === "failed" && item.lastError && <Text style={styles.error}>{item.lastError}</Text>}
      {item.status !== "sending" && <Button label="Xoá" variant="secondary" onPress={confirmRemove} />}
    </Card>
  );
}

export default function ProfileScreen() {
  const { user, signOut } = useAuth();
  const { items, remove } = useQueue();

  const confirmSignOut = () =>
    Alert.alert("Đăng xuất", "Bạn có chắc muốn đăng xuất?", [
      { text: "Huỷ", style: "cancel" },
      { text: "Đăng xuất", style: "destructive", onPress: () => void signOut() },
    ]);

  const waiting = items.filter((item) => item.status !== "failed").length;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Card>
        <Text style={styles.name}>{user?.fullName}</Text>
        <Text style={styles.meta}>Vai trò: Nông dân</Text>
        <Text style={styles.meta}>Phiên bản {Constants.expoConfig?.version}</Text>
      </Card>

      {items.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Chờ gửi ({waiting})</Text>
          {items.map((item) => (
            <QueueRow key={item.id} item={item} onRemove={() => remove(item.id)} />
          ))}
        </View>
      )}

      <Button label="Đăng xuất" variant="danger" onPress={confirmSignOut} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.lg },
  name: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.body, color: colors.textMuted },
  section: { gap: spacing.md },
  sectionTitle: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  row: { flexDirection: "row", gap: spacing.md },
  rowText: { flex: 1, gap: spacing.xs },
  rowTitle: { fontSize: fontSize.body, fontWeight: "700", color: colors.text },
  caption: { fontSize: fontSize.caption, color: colors.textMuted },
  thumbnail: { width: 72, height: 72, borderRadius: radius.card - 4, backgroundColor: colors.border },
  error: { fontSize: fontSize.caption, color: colors.danger },
});
