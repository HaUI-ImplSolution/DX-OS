import { router } from "expo-router";
import { StyleSheet, Text } from "react-native";
import { Card } from "@/components/Card";
import { StatusChip } from "@/components/StatusChip";
import { useParcelIsolation } from "@/hooks/useParcelIsolation";
import { colors, fontSize } from "@/theme";
import type { Parcel } from "@/types";

interface Props {
  parcel: Parcel;
  // Changes when the list is refreshed so the isolation badge is reloaded too.
  version: number;
}

export function ParcelCard({ parcel, version }: Props) {
  const isolated = useParcelIsolation(parcel.code, version);
  return (
    <Card
      onPress={() =>
        router.push({ pathname: "/parcels/[code]", params: { code: parcel.code } })
      }
    >
      <Text style={styles.code}>{parcel.code}</Text>
      <Text style={styles.meta}>Diện tích {parcel.areaHectares} ha</Text>
      {parcel.location && <Text style={styles.meta}>{parcel.location}</Text>}
      {isolated && <StatusChip label="Đang cách ly" tone="warning" />}
    </Card>
  );
}

const styles = StyleSheet.create({
  code: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.body, color: colors.textMuted },
});
