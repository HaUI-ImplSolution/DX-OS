import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, BackHandler, Image, Linking, StyleSheet, Text, View } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { randomUUID } from "expo-crypto";
import * as ImagePicker from "expo-image-picker";
import { router, Stack, useLocalSearchParams } from "expo-router";
import { ApiError, isNetworkError, type UploadFile } from "@/api/client";
import { createDiseaseReport } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { ProgressBar } from "@/components/ProgressBar";
import { Screen } from "@/components/Screen";
import { prepareForUpload } from "@/image";
import { useQueue } from "@/offline/QueueProvider";
import { colors, fontSize, radius, spacing } from "@/theme";

export default function CaptureScreen() {
  const { seasonId, parcelCode } = useLocalSearchParams<{ seasonId: string; parcelCode?: string }>();
  const { enqueue } = useQueue();
  const [photo, setPhoto] = useState<UploadFile | null>(null);
  const [preparing, setPreparing] = useState(false);
  const [permissionDenied, setPermissionDenied] = useState(false);
  const [sending, setSending] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const hasPhoto = useRef(false);
  // One key per photo: a retry of the same photo can never create a second report.
  const idempotencyKey = useRef(randomUUID());

  const capture = useCallback(async () => {
    setError(null);
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      setPermissionDenied(true);
      return;
    }
    setPermissionDenied(false);
    const result = await ImagePicker.launchCameraAsync({ mediaTypes: ["images"], quality: 1 });
    if (result.canceled) {
      if (!hasPhoto.current) router.back();
      return;
    }
    setPreparing(true);
    try {
      const prepared = await prepareForUpload(result.assets[0]);
      hasPhoto.current = true;
      idempotencyKey.current = randomUUID();
      setPhoto(prepared);
    } catch {
      setError("Không xử lý được ảnh. Hãy chụp lại.");
    } finally {
      setPreparing(false);
    }
  }, []);

  useEffect(() => {
    void capture();
  }, [capture]);

  useEffect(() => {
    if (!sending) return;
    const subscription = BackHandler.addEventListener("hardwareBackPress", () => true);
    return () => subscription.remove();
  }, [sending]);

  const saveForLater = async (file: UploadFile) => {
    try {
      await enqueue({
        id: idempotencyKey.current,
        cropSeasonId: Number(seasonId),
        parcelCode: parcelCode ?? null,
        uri: file.uri,
      });
    } catch {
      setError("Không gửi được và không lưu được ảnh. Kiểm tra dung lượng máy rồi thử lại.");
      setSending(false);
      return;
    }
    setSending(false);
    Alert.alert("Đã lưu ảnh", "Không gửi được. Ảnh đã lưu, sẽ tự gửi khi có mạng.", [
      { text: "Đóng", onPress: () => router.back() },
    ]);
  };

  const send = async () => {
    if (!photo) return;
    setSending(true);
    setProgress(0);
    setError(null);
    if ((await NetInfo.fetch()).isConnected === false) {
      await saveForLater(photo);
      return;
    }
    try {
      await createDiseaseReport(Number(seasonId), photo, setProgress, idempotencyKey.current);
      router.back();
    } catch (e) {
      if (isNetworkError(e)) {
        await saveForLater(photo);
        return;
      }
      setError(e instanceof ApiError ? e.message : "Không gửi được. Thử lại sau.");
      setSending(false);
    }
  };

  return (
    <Screen>
      <Stack.Screen options={{ headerBackVisible: !sending, gestureEnabled: !sending }} />
      {permissionDenied ? (
        <View style={styles.center}>
          <Text style={styles.message}>
            Chưa có quyền dùng camera. Mở Cài đặt và bật quyền Camera cho DX-Farm.
          </Text>
          <Button label="Mở Cài đặt" onPress={() => void Linking.openSettings()} />
          <Button label="Thử lại" variant="secondary" onPress={() => void capture()} />
        </View>
      ) : photo ? (
        <>
          <Image source={{ uri: photo.uri }} style={styles.preview} resizeMode="contain" />
          <Text style={styles.hint}>Ảnh rõ nét, thấy rõ vết bệnh?</Text>
          {sending && <ProgressBar fraction={progress} />}
          {error && <Text style={styles.error}>{error}</Text>}
          <View style={styles.actions}>
            <View style={styles.action}>
              <Button label="Chụp lại" variant="secondary" disabled={sending} onPress={() => void capture()} />
            </View>
            <View style={styles.action}>
              <Button
                label={error ? "Gửi lại" : "Gửi báo cáo"}
                disabled={sending}
                onPress={() => void send()}
              />
            </View>
          </View>
        </>
      ) : (
        <View style={styles.center}>
          <Text style={styles.message}>{preparing ? "Đang xử lý ảnh…" : "Đang mở camera…"}</Text>
          {error && <Text style={styles.error}>{error}</Text>}
          {error && <Button label="Chụp lại" onPress={() => void capture()} />}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: "center", gap: spacing.lg },
  message: { fontSize: fontSize.body, color: colors.textMuted, textAlign: "center" },
  preview: { flex: 1, borderRadius: radius.card, backgroundColor: colors.border },
  hint: { fontSize: fontSize.body, color: colors.text, textAlign: "center", marginVertical: spacing.md },
  error: { fontSize: fontSize.body, color: colors.danger, textAlign: "center", marginBottom: spacing.md },
  actions: { flexDirection: "row", gap: spacing.md, marginTop: spacing.sm },
  action: { flex: 1 },
});
