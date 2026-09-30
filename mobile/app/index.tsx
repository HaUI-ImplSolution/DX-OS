import { StyleSheet, Text } from "react-native";
import { useAuth } from "@/auth/AuthProvider";
import { Screen } from "@/components/Screen";
import { colors, fontSize } from "@/theme";

export default function HomeScreen() {
  const { user } = useAuth();
  return (
    <Screen>
      <Text style={styles.title}>Xin chào, {user?.fullName}</Text>
      <Text style={styles.body}>Danh sách thửa ruộng sẽ hiển thị ở đây.</Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: fontSize.title, fontWeight: "700", color: colors.text },
  body: { fontSize: fontSize.body, color: colors.textMuted, marginTop: 8 },
});
