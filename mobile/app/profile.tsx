import { Alert, StyleSheet, Text, View } from "react-native";
import Constants from "expo-constants";
import { useAuth } from "@/auth/AuthProvider";
import { Button } from "@/components/Button";
import { Screen } from "@/components/Screen";
import { colors, fontSize, radius, spacing } from "@/theme";

export default function ProfileScreen() {
  const { user, signOut } = useAuth();

  const confirmSignOut = () =>
    Alert.alert("Đăng xuất", "Bạn có chắc muốn đăng xuất?", [
      { text: "Huỷ", style: "cancel" },
      { text: "Đăng xuất", style: "destructive", onPress: () => void signOut() },
    ]);

  return (
    <Screen>
      <View style={styles.card}>
        <Text style={styles.name}>{user?.fullName}</Text>
        <Text style={styles.meta}>Vai trò: Nông dân</Text>
        <Text style={styles.meta}>Phiên bản {Constants.expoConfig?.version}</Text>
      </View>
      <View style={styles.actions}>
        <Button label="Đăng xuất" variant="danger" onPress={confirmSignOut} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.xs,
  },
  name: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.body, color: colors.textMuted },
  actions: { marginTop: "auto" },
});
