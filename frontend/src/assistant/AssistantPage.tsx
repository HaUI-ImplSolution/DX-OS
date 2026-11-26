// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Title } from "react-admin";
import { Alert, Button, Card, Stack, Typography } from "@mui/material";
import PageHeader from "../shell/PageHeader";
import { assistantRequest, type AssistantAccount, type Proposal } from "./api";
import { t } from "../shell/t";

export default function AssistantPage() {
  const [me, setMe] = useState<AssistantAccount | null>(null);
  const [items, setItems] = useState<Proposal[]>([]); const [page, setPage] = useState(0);
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    let active = true; setError("");
    void assistantRequest<AssistantAccount>("/me").then(async result => {
      if (!active) return; setMe(result);
      if (result?.linked) {
        const proposals = await assistantRequest<Proposal[]>(`/me/proposals?page=${page}&size=5`);
        if (active) setItems(proposals);
      }
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : t("Không thể tải trạng thái trợ lý.")); });
    return () => { active = false; };
  }, [page]);
  const states: Record<string, string> = { pending: t("Chờ xác nhận"), executing: t("Đang thực hiện"),
    succeeded: t("Đã lưu"), failed: t("Bị từ chối"), uncertain: t("Chưa rõ kết quả"), cancelled: t("Đã hủy"), expired: t("Hết hạn") };
  return <Stack spacing={2}>
    <Title title={t("Trợ lý nghiệp vụ")} />
    <PageHeader title={t("Trợ lý nghiệp vụ")} subtitle={t("Kết nối và theo dõi các đề xuất xử lý hồ sơ.")} />
    {error && <Alert severity="error">{error}</Alert>}
    {notice && <Alert severity="info">{notice}</Alert>}
    <Card sx={{ p: 2 }}><Stack spacing={2}>
      <Typography>{me?.linked ? t("Đã liên kết tài khoản DX-OS.") : t("Liên kết tài khoản DX-OS để trợ lý truy cập đúng quyền của bạn.")}</Typography>
      {me && <Typography>{me.model.configured ? t("Trợ lý đã sẵn sàng tạo đề xuất.") : t("Trợ lý chưa được cấu hình để tạo đề xuất.")}</Typography>}
      <Button disabled={busy} variant="outlined" onClick={() => {
        setBusy(true); setError("");
        void assistantRequest<{ url: string }>("/me/core/link", "POST", {}).then(result => {
          window.location.assign(result.url);
        }).catch(e => { setError(e instanceof Error ? e.message : t("Không thể liên kết tài khoản.")); setBusy(false); });
      }}>{me?.linked ? t("Liên kết lại DX-OS") : t("Liên kết DX-OS")}</Button>
      {me?.linked && <Button disabled={busy} color="error" variant="outlined" onClick={() => {
        setBusy(true); setError(""); setNotice("");
        void assistantRequest<{ revoked: boolean }>("/me/core/link", "DELETE").then(result => {
          setMe(current => current && { ...current, linked: false }); setItems([]); setPage(0);
          setNotice(result.revoked ? t("Đã hủy liên kết và thu hồi quyền của trợ lý.")
            : t("Đã hủy liên kết ở trợ lý; dịch vụ đăng nhập chưa xác nhận thu hồi nên hãy kiểm tra phiên của bạn ở Keycloak."));
        }).catch(e => setError(e instanceof Error ? e.message : t("Không thể hủy liên kết."))).finally(() => setBusy(false));
      }}>{t("Hủy liên kết")}</Button>}
      <Typography variant="body2" color="text.secondary">{t("Mở hồ sơ có nhiệm vụ AI để tạo, xem căn cứ và xác nhận đề xuất.")}</Typography>
    </Stack></Card>
    <Card sx={{ p: 2 }}><Stack spacing={2}>
      <Typography variant="h6">{t("Lịch sử đề xuất")}</Typography>
      {items.map(item => <Stack key={item.id} spacing={0.5} sx={{ borderBottom: 1, borderColor: "divider", pb: 1 }}>
        <Typography>{item.snapshot.summary}</Typography>
        <Typography variant="body2">{item.state === "succeeded" && item.snapshot.operation === "field" && typeof item.receipt?.version === "number" && item.receipt.version === item.snapshot.version ? t("Không có thay đổi") : item.expires <= item.now && item.state === "pending" ? t("Hết hạn") : states[item.state] ?? item.state}</Typography>
        {item.state === "uncertain" && <Alert severity="warning">{t("Đối chiếu lịch sử hồ sơ trước khi thử một thao tác khác.")}</Alert>}
      </Stack>)}
      {!items.length && <Typography color="text.secondary">{t("Chưa có đề xuất để hiển thị.")}</Typography>}
      <Stack direction="row"><Button disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t("Trang trước")}</Button>
        <Button disabled={items.length < 5} onClick={() => setPage(p => p + 1)}>{t("Trang sau")}</Button></Stack>
    </Stack></Card>
  </Stack>;
}
