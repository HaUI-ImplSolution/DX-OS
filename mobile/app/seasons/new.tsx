// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState } from "react";
import { Alert, ScrollView, StyleSheet, Text } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { randomUUID } from "expo-crypto";
import { router, Stack, useLocalSearchParams } from "expo-router";
import { ApiError, isNetworkError } from "@/api/client";
import {
  createCropSeason,
  listCropSeasons,
  listRiceVarieties,
  resubmitCropSeason,
  suggestCropSeason,
} from "@/api/endpoints";
import { Button } from "@/components/Button";
import { ErrorState } from "@/components/ErrorState";
import { Field } from "@/components/Field";
import { OptionList } from "@/components/OptionList";
import { SkeletonCards } from "@/components/Skeleton";
import { formatDate } from "@/format";
import { useAsync } from "@/hooks/useAsync";
import { useQueue } from "@/offline/QueueProvider";
import { colors, fontSize, spacing } from "@/theme";
import type { CropSeasonInput, CropSeasonSuggestion, SeasonType } from "@/types";

const SEASON_OPTIONS: { value: SeasonType; label: string }[] = [
  { value: "DONG_XUAN", label: "Đông Xuân" },
  { value: "HE_THU", label: "Hè Thu" },
  { value: "THU_DONG", label: "Thu Đông" },
];

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const today = () => new Date().toISOString().slice(0, 10);

// Starts a crop season on an approved parcel; the harvest date is suggested by the backend.
export default function SeasonFormScreen() {
  const { parcelCode, mode, id } = useLocalSearchParams<{
    parcelCode: string;
    mode?: "create" | "resubmit";
    id?: string;
  }>();
  const resubmit = mode === "resubmit";
  const { enqueue } = useQueue();
  const varieties = useAsync(listRiceVarieties, []);
  const [varietyCode, setVarietyCode] = useState<string | null>(null);
  const [startDate, setStartDate] = useState(today());
  const [seasonType, setSeasonType] = useState<SeasonType | null>(null);
  const [suggestion, setSuggestion] = useState<CropSeasonSuggestion | null>(null);
  const [expected, setExpected] = useState("");
  const [reason, setReason] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // One key per form: sending it again can never create a second season.
  const idempotencyKey = useRef(randomUUID());

  useEffect(() => {
    if (!resubmit || !id) return;
    void listCropSeasons(parcelCode).then((seasons) => {
      const season = seasons.find((s) => s.id === Number(id));
      if (!season) return;
      setVarietyCode(season.riceVarietyCode);
      setStartDate(season.startDate);
      setSeasonType(season.seasonType);
      setExpected(season.expectedHarvestDate ?? "");
      setReason(season.reasonForOverride ?? "");
    });
  }, [resubmit, id, parcelCode]);

  // The backend owns the suggestion, so the phone never computes dates itself.
  useEffect(() => {
    if (!varietyCode || !DATE_PATTERN.test(startDate)) return;
    suggestCropSeason(varietyCode, startDate)
      .then((result) => {
        setSuggestion(result);
        setSeasonType((current) => result.seasonType ?? current);
      })
      .catch(() => setSuggestion(null));
  }, [varietyCode, startDate]);

  const outsideRange =
    suggestion !== null &&
    DATE_PATTERN.test(expected) &&
    (expected < suggestion.earliestHarvestDate || expected > suggestion.latestHarvestDate);
  const valid =
    varietyCode !== null &&
    DATE_PATTERN.test(startDate) &&
    seasonType !== null &&
    (expected === "" || DATE_PATTERN.test(expected)) &&
    (!outsideRange || reason.trim() !== "");

  const saveForLater = async (input: CropSeasonInput) => {
    await enqueue({
      id: idempotencyKey.current,
      kind: "season",
      parcelCode,
      payload: JSON.stringify(input),
    });
    Alert.alert("Đã lưu vụ mùa", "Chưa có mạng. Vụ mùa sẽ tự gửi khi có mạng, rồi chờ quản trị viên duyệt.", [
      { text: "Đóng", onPress: () => router.back() },
    ]);
  };

  const submit = async () => {
    if (!valid || !varietyCode || !seasonType) return;
    setSending(true);
    setError(null);
    const input: CropSeasonInput = {
      riceVarietyCode: varietyCode,
      startDate,
      seasonType,
      expectedHarvestDate: expected || undefined,
      reasonForOverride: outsideRange ? reason.trim() : undefined,
    };
    try {
      if (resubmit) {
        await resubmitCropSeason(Number(id), input);
      } else {
        if ((await NetInfo.fetch()).isConnected === false) {
          await saveForLater(input);
          return;
        }
        await createCropSeason(parcelCode, input, idempotencyKey.current);
      }
      Alert.alert("Đã gửi", "Vụ mùa đang chờ quản trị viên duyệt.", [
        { text: "Đóng", onPress: () => router.back() },
      ]);
    } catch (e) {
      if (!resubmit && isNetworkError(e)) {
        await saveForLater(input);
        return;
      }
      setError(e instanceof ApiError || e instanceof Error ? e.message : "Không gửi được. Thử lại sau.");
    } finally {
      setSending(false);
    }
  };

  if (varieties.loading && !varieties.data) return <SkeletonCards />;
  if (varieties.error && !varieties.data) {
    return <ErrorState message={varieties.error} onRetry={varieties.reload} />;
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Stack.Screen options={{ title: resubmit ? "Sửa và gửi lại vụ" : "Vụ mùa mới" }} />
      <Text style={styles.title}>Giống lúa</Text>
      <OptionList
        value={varietyCode}
        onChange={setVarietyCode}
        options={(varieties.data ?? []).map((variety) => ({
          value: variety.code,
          label: variety.name,
          caption:
            `${variety.growthDaysMin}–${variety.growthDaysMax} ngày` +
            (variety.reviewStatus === "IN_REVIEW" ? " · số liệu chưa được kỹ sư xác nhận" : ""),
        }))}
      />

      <Field
        label="Ngày bắt đầu (năm-tháng-ngày)"
        value={startDate}
        onChangeText={setStartDate}
        placeholder="2026-11-20"
        autoCapitalize="none"
        keyboardType="numbers-and-punctuation"
      />

      <Text style={styles.title}>Loại vụ</Text>
      <OptionList value={seasonType} onChange={setSeasonType} options={SEASON_OPTIONS} />
      {suggestion && suggestion.seasonType === null && (
        <Text style={styles.hint}>Tháng bắt đầu này không thuộc vụ nào, hãy chọn loại vụ.</Text>
      )}

      <Field
        label="Ngày dự kiến thu hoạch"
        value={expected}
        onChangeText={setExpected}
        placeholder={suggestion?.expectedHarvestDate ?? "Để trống để dùng ngày gợi ý"}
        autoCapitalize="none"
        keyboardType="numbers-and-punctuation"
        hint={
          suggestion
            ? `Gợi ý ${formatDate(suggestion.expectedHarvestDate)} (từ ${formatDate(
                suggestion.earliestHarvestDate,
              )} đến ${formatDate(suggestion.latestHarvestDate)}). Để trống để dùng ngày gợi ý.`
            : undefined
        }
      />
      {outsideRange && (
        <Field
          label="Lý do chọn ngày khác gợi ý"
          value={reason}
          onChangeText={setReason}
          maxLength={255}
        />
      )}

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
  title: { fontSize: fontSize.body, fontWeight: "700", color: colors.text },
  hint: { fontSize: fontSize.caption, color: colors.warning },
  error: { fontSize: fontSize.body, color: colors.danger },
});
