import { Link, Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { AuthProvider, useAuth } from "@/auth/AuthProvider";
import { QueueProvider } from "@/offline/QueueProvider";
import { colors, fontSize, sizes } from "@/theme";

function ProfileLink() {
  return (
    <Link href="/profile" asChild>
      <Pressable accessibilityRole="button" style={styles.headerButton}>
        <Text style={styles.headerButtonLabel}>Hồ sơ</Text>
      </Pressable>
    </Link>
  );
}

function RootStack() {
  const { status } = useAuth();
  if (status === "loading") {
    return (
      <View style={styles.loading}>
        <Text style={styles.loadingLabel}>Đang tải…</Text>
      </View>
    );
  }
  const signedIn = status === "signedIn";
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.text,
        headerTitleStyle: { fontSize: fontSize.cardTitle, fontWeight: "700" },
        contentStyle: { backgroundColor: colors.bg },
      }}
    >
      <Stack.Protected guard={signedIn}>
        <Stack.Screen name="index" options={{ title: "Thửa của tôi", headerRight: ProfileLink }} />
        <Stack.Screen name="profile" options={{ title: "Hồ sơ" }} />
        <Stack.Screen name="parcels/[code]" />
        <Stack.Screen name="parcels/new" options={{ title: "Thửa mới" }} />
        <Stack.Screen name="seasons/new" options={{ title: "Vụ mùa mới" }} />
        <Stack.Screen name="seasons/[id]" options={{ title: "Báo cáo của vụ" }} />
        <Stack.Screen name="reports/[id]" options={{ title: "Chi tiết báo cáo" }} />
        <Stack.Screen name="capture" options={{ title: "Chụp ảnh" }} />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="login" options={{ headerShown: false }} />
      </Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  return (
    <AuthProvider>
      <QueueProvider>
        <StatusBar style="dark" />
        <RootStack />
      </QueueProvider>
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  headerButton: { minHeight: sizes.touch, minWidth: sizes.touch, justifyContent: "center" },
  headerButtonLabel: { fontSize: fontSize.body, fontWeight: "700", color: colors.primary },
  loading: { flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: colors.bg },
  loadingLabel: { fontSize: fontSize.body, color: colors.textMuted },
});
