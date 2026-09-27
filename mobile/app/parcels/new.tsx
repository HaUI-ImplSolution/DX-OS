import { useEffect, useRef, useState } from "react";
import { Alert, ScrollView, StyleSheet, Text, View } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { randomUUID } from "expo-crypto";
import { router, Stack, useLocalSearchParams } from "expo-router";
import { ApiError, isNetworkError } from "@/api/client";
import { createParcel, listParcels, requestParcelEdit, resubmitParcel } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { GPS_MAX_ACCURACY_METERS } from "@/config";
import { readGpsFix, GpsPermissionError, type GpsFix } from "@/gps";
import { useQueue } from "@/offline/QueueProvider";
import { colors, fontSize, spacing } from "@/theme";
import type { ParcelInput } from "@/types";

type Mode = "create" | "resubmit" | "edit";

const TITLE: Record<Mode, string> = {
  create: "Thửa mới",
  resubmit: "Sửa và gửi lại thửa",
  edit: "Đề nghị chỉnh sửa thửa",
};

// Registers a parcel at the field: the position comes from the phone's GPS, never typed in.
export default function ParcelFormScreen() {
  const { mode: modeParam, id } = useLocalSearchParams<{ mode?: Mode; id?: string }>();
  const mode: Mode = modeParam ?? "create";
  const { enqueue } = useQueue();
  const [area, setArea] = useState("");
  const [location, setLocation] = useState("");
  const [reason, setReason] = useState("");
  const [fix, setFix] = useState<GpsFix | null>(null);
  const [locating, setLocating] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // One key per form: sending it again can never create a second parcel.
  const idempotencyKey = useRef(randomUUID());

  useEffect(() => {
    if (mode === "create" || !id) return;
    void listParcels().then((parcels) => {
      const parcel = parcels.find((p) => p.id === Number(id));
      if (!parcel) return;
      setArea(String(parcel.areaHectares));
      setLocation(parcel.location ?? "");
    });
  }, [mode, id]);

  const locate = async () => {
    setLocating(true);
    setError(null);
    try {
      setFix(await readGpsFix());
    } catch (e) {
      setError(
        e instanceof GpsPermissionError
          ? "Chưa có quyền dùng vị trí. Mở Cài đặt và bật quyền Vị trí cho DX-Farm."
          : "Không lấy được vị trí. Ra chỗ thoáng rồi thử lại.",
      );
    } finally {
      setLocating(false);
    }
  };

  const areaValue = Number(area.replace(",", "."));
  const accurate = fix !== null && fix.accuracyMeters <= GPS_MAX_ACCURACY_METERS && !fix.mocked;
  const valid = areaValue > 0 && accurate && (mode !== "edit" || reason.trim() !== "");

  const buildInput = (): ParcelInput => ({
    areaHectares: areaValue,
    location: location.trim() || undefined,
    latitude: fix!.latitude,
    longitude: fix!.longitude,
    gpsAccuracyMeters: fix!.accuracyMeters,
    mockedLocation: fix!.mocked,
    reason: mode === "edit" ? reason.trim() : undefined,
  });

  const saveForLater = async (input: ParcelInput) => {
    await enqueue({
      id: idempotencyKey.current,
      kind: "parcel",
      parcelCode: null,
      payload: JSON.stringify(input),
    });
    Alert.alert("Đã lưu thửa", "Chưa có mạng. Thửa sẽ tự gửi khi có mạng, rồi chờ quản trị viên duyệt.", [
      { text: "Đóng", onPress: () => router.back() },
    ]);
  };

  const submit = async () => {
    if (!valid) return;
    setSending(true);
    setError(null);
    const input = buildInput();
    try {
      if (mode === "create") {
        if ((await NetInfo.fetch()).isConnected === false) {
          await saveForLater(input);
          return;
        }
        await createParcel(input, idempotencyKey.current);
      } else if (mode === "resubmit") {
        await resubmitParcel(Number(id), input);
      } else {
        await requestParcelEdit(Number(id), input);
      }
      Alert.alert("Đã gửi", "Yêu cầu đang chờ quản trị viên duyệt.", [
        { text: "Đóng", onPress: () => router.back() },
      ]);
    } catch (e) {
      if (mode === "create" && isNetworkError(e)) {
        await saveForLater(input);
        return;
      }
      setError(e instanceof ApiError || e instanceof Error ? e.message : "Không gửi được. Thử lại sau.");
    } finally {
      setSending(false);
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Stack.Screen options={{ title: TITLE[mode] }} />
      <Field
        label="Diện tích (ha)"
        keyboardType="decimal-pad"
        value={area}
        onChangeText={setArea}
        placeholder="Ví dụ 1.5"
      />
      <Field
        label="Địa chỉ hoặc tên khu ruộng (không bắt buộc)"
        value={location}
        onChangeText={setLocation}
        placeholder="Ví dụ Ấp 3, xã Tân Thạnh"
      />
      {mode === "edit" && (
        <Field label="Lý do chỉnh sửa" value={reason} onChangeText={setReason} maxLength={255} />
      )}

      <View style={styles.gps}>
        <Text style={styles.gpsTitle}>Vị trí thửa</Text>
        {fix ? (
          <>
            <Text style={styles.body}>
              {fix.latitude}, {fix.longitude}
            </Text>
            <Text style={accurate ? styles.body : styles.warning}>
              Sai số khoảng {Math.round(fix.accuracyMeters)} m
              {fix.mocked
                ? ". Vị trí có dấu hiệu giả lập, hãy tắt ứng dụng giả vị trí."
                : fix.accuracyMeters > GPS_MAX_ACCURACY_METERS
                  ? `. Quá lớn (tối đa ${GPS_MAX_ACCURACY_METERS} m), hãy ra chỗ thoáng rồi lấy lại.`
                  : ". Đạt yêu cầu."}
            </Text>
          </>
        ) : (
          <Text style={styles.body}>Hãy đứng tại ruộng rồi lấy vị trí.</Text>
        )}
        <Button
          label={locating ? "Đang lấy vị trí…" : fix ? "Lấy lại vị trí" : "Tôi đang ở ruộng, lấy vị trí"}
          variant={fix ? "secondary" : "primary"}
          disabled={locating || sending}
          onPress={() => void locate()}
        />
      </View>

      {error && <Text style={styles.error}>{error}</Text>}
      <Button
        label={sending ? "Đang gửi…" : "Gửi yêu cầu"}
        disabled={!valid || sending}
        onPress={() => void submit()}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.lg },
  gps: { gap: spacing.sm },
  gpsTitle: { fontSize: fontSize.body, fontWeight: "700", color: colors.text },
  body: { fontSize: fontSize.body, color: colors.text },
  warning: { fontSize: fontSize.body, color: colors.warning },
  error: { fontSize: fontSize.body, color: colors.danger },
});
