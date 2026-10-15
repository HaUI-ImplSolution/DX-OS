import { StyleSheet, Text, View } from "react-native";
import { getParcelWeather } from "@/api/endpoints";
import { Card } from "@/components/Card";
import { formatDate } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { colors, fontSize, spacing } from "@/theme";

const FORECAST_DAYS = 3;

// WMO weather interpretation codes grouped by how a farmer reads them.
function describe(code: number): string {
  if (code === 0) return "Trời quang";
  if (code <= 3) return "Có mây";
  if (code === 45 || code === 48) return "Sương mù";
  if (code >= 51 && code <= 57) return "Mưa phùn";
  if (code >= 61 && code <= 67) return "Mưa";
  if (code >= 80 && code <= 82) return "Mưa rào";
  if (code >= 95) return "Dông";
  return "Nhiều mây";
}

// Weather at the parcel: now and the next days. A missing connection only hides the card.
export function WeatherCard({ parcelCode }: { parcelCode: string }) {
  const { data, error } = useAsync(() => getParcelWeather(parcelCode), [parcelCode]);
  if (!data) {
    return error ? (
      <Card>
        <Text style={styles.title}>Thời tiết</Text>
        <Text style={styles.meta}>{error}</Text>
      </Card>
    ) : null;
  }
  const { current, daily } = data;
  return (
    <Card>
      <Text style={styles.title}>Thời tiết tại thửa</Text>
      {data.isSnapshot && (
        <Text style={styles.warning}>{data.note ?? "Dữ liệu tham khảo, không phải thời gian thực."}</Text>
      )}
      <Text style={styles.now}>
        {describe(current.weatherCode)}, {current.temperatureC.toFixed(0)} °C
      </Text>
      <Text style={styles.meta}>
        Độ ẩm {Math.round(current.humidityPercent)}% · Gió {Math.round(current.windSpeedKmh)} km/h · Mưa{" "}
        {current.precipitationMm.toFixed(1)} mm
      </Text>
      <View style={styles.days}>
        {daily.slice(0, FORECAST_DAYS).map((day) => (
          <View key={day.date} style={styles.day}>
            <Text style={styles.dayLabel}>{formatDate(day.date)}</Text>
            <Text style={styles.meta}>
              {Math.round(day.tempMinC)}–{Math.round(day.tempMaxC)} °C
            </Text>
            <Text style={styles.meta}>Mưa {day.precipitationProbabilityPercent}%</Text>
          </View>
        ))}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: fontSize.cardTitle, fontWeight: "700", color: colors.text },
  now: { fontSize: fontSize.body, fontWeight: "700", color: colors.text },
  meta: { fontSize: fontSize.caption, color: colors.textMuted },
  warning: { fontSize: fontSize.caption, color: colors.warning },
  days: { flexDirection: "row", gap: spacing.md },
  day: { flex: 1, gap: spacing.xs },
  dayLabel: { fontSize: fontSize.caption, fontWeight: "700", color: colors.text },
});
