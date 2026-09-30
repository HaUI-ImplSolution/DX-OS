import { StyleSheet, Text } from "react-native";
import { Screen } from "@/components/Screen";
import { colors, fontSize } from "@/theme";

export default function HomeScreen() {
  return (
    <Screen>
      <Text style={styles.title}>DX-Farm</Text>
      <Text style={styles.body}>Ứng dụng báo cáo sâu bệnh dành cho nông dân.</Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: fontSize.title, fontWeight: "700", color: colors.text },
  body: { fontSize: fontSize.body, color: colors.textMuted, marginTop: 8 },
});
