import { StyleSheet, Text, View } from "react-native";
import { useAuth } from "@/auth/AuthProvider";
import { Button } from "@/components/Button";
import { Screen } from "@/components/Screen";
import { colors, fontSize, spacing } from "@/theme";

export default function LoginScreen() {
  const { signIn, signingIn, notice } = useAuth();
  return (
    <Screen>
      <View style={styles.hero}>
        <Text style={styles.title}>DX-Farm</Text>
        <Text style={styles.subtitle}>Báo cáo sâu bệnh trên ruộng của bạn</Text>
      </View>
      <Button label={signingIn ? "Đang mở…" : "Đăng nhập"} onPress={signIn} disabled={signingIn} />
      {notice && <Text style={styles.notice}>{notice}</Text>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { flex: 1, justifyContent: "center" },
  title: { fontSize: 32, fontWeight: "700", color: colors.primaryDark },
  subtitle: { fontSize: fontSize.body, color: colors.textMuted, marginTop: spacing.sm },
  notice: {
    fontSize: fontSize.body,
    color: colors.danger,
    marginTop: spacing.md,
    marginBottom: spacing.lg,
    textAlign: "center",
  },
});
