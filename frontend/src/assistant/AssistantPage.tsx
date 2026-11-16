// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { Title } from "react-admin";
import { Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions,
  DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, Switch, Tab, Tabs, TextField, Typography } from "@mui/material";
import PageHeader from "../shell/PageHeader";
import { getLocale } from "../shell/locale";
import { t } from "../shell/t";
import { assistantRequest, type Profiles, type Notifications, type Proposal } from "./api";

type Runtime = { active: boolean; provisioned: boolean };
type Telegram = { configured: boolean; paired: boolean; username: string | null; url: string | null };
const stateLabels: Record<string, string> = { pending: t("Chờ xác nhận"), executing: t("Đang thực hiện"), succeeded: t("Đã thực hiện"),
  failed: t("Bị từ chối"), uncertain: t("Chưa rõ kết quả"), cancelled: t("Đã hủy"), expired: t("Hết hạn") };

const supportedTimeZones = (Intl as typeof Intl & { supportedValuesOf?: (key: "timeZone") => string[] }).supportedValuesOf;
const timeZones = Array.from(new Set(["Asia/Ho_Chi_Minh", "UTC",
  ...(supportedTimeZones ? supportedTimeZones("timeZone") :
    ["Asia/Bangkok", "Asia/Singapore", "Asia/Tokyo", "Europe/London", "America/New_York"])]));
const zoneLabel = (zone: string) => zone === "Asia/Ho_Chi_Minh" ? t("Việt Nam · Asia/Ho_Chi_Minh") : zone.replace(/_/g, " ");

export default function AssistantPage() {
  const [tab, setTab] = useState(0);
  const [changeBot, setChangeBot] = useState(false);
  const [addProfile, setAddProfile] = useState(false);
  const [profiles, setProfiles] = useState<Profiles>({ profiles: [], selected: null });
  const [runtime, setRuntime] = useState<Runtime | null>(null);
  const [telegram, setTelegram] = useState<Telegram | null>(null);
  const [notifications, setNotifications] = useState<Notifications | null>(null);
  const [linked, setLinked] = useState(false);
  const [capacity, setCapacity] = useState<{ capacity: number; provisioned: number; assigned: number } | null>(null);
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [provider, setProvider] = useState("gemini"), [model, setModel] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [token, setToken] = useState(""), [code, setCode] = useState("");
  const [confirm, setConfirm] = useState<Proposal | null>(null);
  const [page, setPage] = useState(0);

  const load = useCallback(async () => {
    const initial = await assistantRequest<{ linked: boolean }>("/me");
    setLinked(initial.linked);
    const identity = initial as { linked: boolean; admin?: boolean };
    if (identity.admin) setCapacity(await assistantRequest("/admin/capacity"));
    const results = await Promise.allSettled([
      assistantRequest<Profiles>("/me/providers"), assistantRequest<Runtime>("/me/runtime"),
      assistantRequest<Telegram>("/me/telegram"), assistantRequest<Notifications>("/me/notifications"),
      ...(initial.linked ? [assistantRequest<Proposal[]>(`/me/proposals?page=${page}&size=5`)] : []),
    ]);
    const [p, r, bot, n, history] = results;
    if (p?.status === "fulfilled") setProfiles(p.value as Profiles);
    if (r?.status === "fulfilled") setRuntime(r.value as Runtime);
    if (bot?.status === "fulfilled") setTelegram(bot.value as Telegram);
    if (n?.status === "fulfilled") setNotifications(n.value as Notifications);
    if (history?.status === "fulfilled") setProposals(history.value as Proposal[]);
    else setProposals([]);
    const failure = results.find(item => item.status === "rejected");
    if (failure?.status === "rejected") throw failure.reason;
  }, [page]);
  useEffect(() => { let current = true;
    setLoading(true);
    load().catch(e => { if (current) setError(e instanceof Error ? e.message : t("Không kết nối được trợ lý")); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [load]);
  const run = async (action: () => Promise<unknown>, message?: string) => {
    setBusy(true); setError(null); setNote(null);
    try { await action(); if (message) setNote(message); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : t("Chưa hoàn tất thao tác")); }
    finally { setBusy(false); }
  };
  const link = async () => {
    const result = await assistantRequest<{ url: string }>("/me/core/link", "POST", {});
    window.location.assign(result.url);
  };

  return <Box sx={{ maxWidth: 960, mx: "auto", width: "100%", minWidth: 0 }}>
    <Title title={t("Trợ lý cá nhân")} />
    <PageHeader title={t("Trợ lý cá nhân")} subtitle={t("Đọc, theo dõi và xử lý hồ sơ qua Telegram với quyền của bạn. Mọi thay đổi cần xác nhận.")}
      actions={<Button disabled={busy || loading} onClick={() => void run(load)}>{t("Tải lại")}</Button>} />
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
    {note && <Alert severity="success" sx={{ mb: 2 }}>{note}</Alert>}
    {loading && <CircularProgress size={24} />}
    <Card sx={{ mb: 2.5 }}><CardContent>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} justifyContent="space-between" alignItems={{ sm: "center" }}>
        <Box>
          <Typography variant="subtitle1" fontWeight={700}>{runtime?.active ? t("Trợ lý đang hoạt động") : telegram?.paired ? t("Sẵn sàng bật trợ lý") : t("Thiết lập kết nối của bạn")}</Typography>
          <Typography variant="body2" color="text.secondary">{runtime?.active ? t("Giữ trợ lý bật để nhận tin nhắn và thông báo trên Telegram.") : t("Kết nối tài khoản, chọn model API và liên kết bot Telegram riêng.")}</Typography>
        </Box>
        {runtime?.active && <Button variant="outlined" disabled={busy || loading}
          onClick={() => void run(() => assistantRequest("/me/runtime/stop", "POST", {}), t("Đã dừng trợ lý."))}>{t("Dừng trợ lý")}</Button>}
        {telegram?.paired && !runtime?.active && <Button variant="contained" disabled={busy || loading || !linked || !profiles.selected || !runtime?.provisioned}
          onClick={() => void run(() => assistantRequest("/me/runtime/start", "POST", {}), t("Đang khởi động trợ lý. Bot có thể cần một lúc để sẵn sàng."))}>{t("Bật trợ lý")}</Button>}
      </Stack>
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", mt: 2 }}>
        <Chip size="small" label={linked ? t("DX-OS đã liên kết") : t("DX-OS chưa liên kết")} color={linked ? "success" : "default"} />
        <Chip size="small" label={profiles.selected ? t("Đã chọn model") : t("Chưa chọn model")} color={profiles.selected ? "success" : "default"} />
        <Chip size="small" label={telegram?.paired ? t("Telegram đã liên kết") : t("Telegram chưa liên kết")} color={telegram?.paired ? "success" : "default"} />
      </Stack>
      {runtime && !runtime.provisioned && <Alert severity="info" sx={{ mt: 2 }}>{t("Chưa có chỗ chạy trợ lý. Hãy liên hệ quản trị viên để chuẩn bị thêm.")}</Alert>}
      {capacity && <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>{t("Quản trị: đã cấp {assigned}/{provisioned} chỗ chạy; tối đa {capacity}.", capacity)}</Typography>}
    </CardContent></Card>
    <Tabs value={tab} onChange={(_, value: number) => setTab(value)} aria-label={t("Cài đặt trợ lý")} variant="scrollable" scrollButtons="auto" sx={{ mb: 2.5, borderBottom: 1, borderColor: "divider" }}>
      <Tab id="assistant-tab-0" aria-controls="assistant-panel-0" label={t("Kết nối")} />
      <Tab id="assistant-tab-1" aria-controls="assistant-panel-1" label={t("Thông báo")} />
      <Tab id="assistant-tab-2" aria-controls="assistant-panel-2" label={t("Hoạt động")} />
    </Tabs>
    <Stack spacing={2} role="tabpanel" id={`assistant-panel-${tab}`} aria-labelledby={`assistant-tab-${tab}`}>
      {tab === 0 && <>
      <Card><CardContent>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} justifyContent="space-between" alignItems={{ sm: "center" }}>
          <Box><Typography variant="h6">{t("Tài khoản DX-OS")}</Typography>
            <Typography variant="body2" color="text.secondary">{t("Trợ lý chỉ đọc và xử lý hồ sơ trong quyền của tài khoản bạn.")}</Typography></Box>
          <Button variant="outlined" disabled={busy || loading} onClick={() => void run(link)}>{linked ? t("Liên kết lại DX-OS") : t("Liên kết DX-OS")}</Button>
        </Stack>
      </CardContent></Card>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>{t("Model trả lời")}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t("Chọn dịch vụ bạn sử dụng và model có hỗ trợ công cụ. Lưu key chưa gọi API; kiểm kết nối có thể tiêu quota.")}</Typography>
        <Stack spacing={2}>
          {profiles.profiles.length > 0 && <Button sx={{ alignSelf: "flex-start" }} onClick={() => setAddProfile(!addProfile)}>{addProfile ? t("Đóng biểu mẫu") : t("Thêm model API")}</Button>}
          {(!profiles.profiles.length || addProfile) && <>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField select label={t("Nhà cung cấp")} value={provider} onChange={e => setProvider(e.target.value)} sx={{ minWidth: 180 }}>
              <MenuItem value="gemini">Gemini</MenuItem><MenuItem value="openai">OpenAI</MenuItem>
              <MenuItem value="anthropic">Anthropic</MenuItem><MenuItem value="compatible" disabled>{t("OpenAI-compatible (chưa mở)")}</MenuItem>
            </TextField>
            <TextField fullWidth label={t("Tên model")} autoComplete="off" value={model} onChange={e => setModel(e.target.value)} helperText={t("Nhập đúng tên model đang khả dụng với API key của bạn.")} />
          </Stack>
          <TextField label="API key" type="password" autoComplete="new-password" value={apiKey} onChange={e => setApiKey(e.target.value)} />
          <Button sx={{ alignSelf: "flex-start" }} variant="outlined" disabled={busy || loading || !model.trim() || !apiKey.trim()}
            onClick={() => void run(async () => {
              await assistantRequest("/me/providers", "POST", { provider, model: model.trim(), apiKey }); setApiKey(""); setAddProfile(false);
            }, t("Đã lưu kết nối. Bạn có thể kiểm tra model trước khi sử dụng."))}>{t("Lưu kết nối")}</Button>
          </>}
          {profiles.profiles.map(profile => <Stack key={profile.id} direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }}>
            <Typography sx={{ flex: 1 }}>{profile.provider} · {profile.model}</Typography>
            {profiles.selected === profile.id && <Chip size="small" label={t("Đang chọn")} color="primary" />}
            <Button disabled={busy || loading || profile.provider === "compatible" || profiles.selected === profile.id} onClick={() => void run(() => assistantRequest(`/me/providers/${profile.id}/select`, "POST", {}))}>{t("Chọn")}</Button>
            <Button disabled={busy || loading || profile.provider === "compatible" || !runtime?.provisioned} onClick={() => {
              if (window.confirm(t("Kiểm tra này gọi API thật và có thể tiêu quota. Tiếp tục?"))) void run(async () => {
                await assistantRequest(`/me/providers/${profile.id}/probe`, "POST", { acknowledgeCost: true });
              }, t("Model đã kết nối và gọi được công cụ kiểm tra."));
            }}>{t("Kiểm kết nối")}</Button>
            <Button color="error" disabled={busy || loading} onClick={() => {
              if (window.confirm(t("Xóa cấu hình {name}? Thao tác này dừng trợ lý, không thu hồi API key tại nhà cung cấp.", { name: `${profile.provider} · ${profile.model}` })))
                void run(() => assistantRequest(`/me/providers/${profile.id}`, "DELETE"));
            }}>{t("Xóa")}</Button>
          </Stack>)}
        </Stack>
      </CardContent></Card>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Bot Telegram</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t("Bot là nơi bạn trò chuyện và nhận thông báo. Mỗi tài khoản dùng một bot riêng.")}</Typography>
        <Stack spacing={2}>
          {telegram?.configured && <Stack direction="row" spacing={1} alignItems="center" useFlexGap sx={{ flexWrap: "wrap" }}>
            <Button variant="outlined" component="a" href={telegram.url ?? undefined} target="_blank" rel="noreferrer">{t("Mở @{name}", { name: telegram.username ?? "" })}</Button>
            <Chip size="small" label={telegram.paired ? t("Đã liên kết") : t("Chờ liên kết")} color={telegram.paired ? "success" : "default"} />
            <Button onClick={() => setChangeBot(!changeBot)}>{changeBot ? t("Giữ bot hiện tại") : t("Đổi bot")}</Button>
            <Button color="error" disabled={busy || loading} onClick={() => {
              if (window.confirm(t("Ngắt bot sẽ dừng trợ lý và xóa liên kết Telegram cùng lịch thông báo. API key vẫn được giữ. Tiếp tục?")))
                void run(async () => { await assistantRequest("/me/telegram", "DELETE", {}); setCode(""); setToken(""); setChangeBot(false); }, t("Đã ngắt bot Telegram."));
            }}>{t("Ngắt bot")}</Button>
          </Stack>}
          {(!telegram?.configured || changeBot) && <>
            <Typography variant="body2">{t("Mở @BotFather trên Telegram, tạo bot bằng /newbot rồi chép token được cấp vào đây.")}</Typography>
            {changeBot && <Alert severity="warning">{t("Đổi bot sẽ dừng trợ lý và yêu cầu liên kết Telegram lại.")}</Alert>}
            <TextField label="Bot token" type="password" autoComplete="new-password" value={token} onChange={e => setToken(e.target.value)} helperText={t("Token của bot, không phải mã đăng nhập tài khoản Telegram.")} />
            <Button variant="outlined" sx={{ alignSelf: "flex-start" }} disabled={busy || loading || !token.trim()} onClick={() => void run(async () => {
              await assistantRequest("/me/telegram", "PUT", { token: token.trim() }); setToken(""); setChangeBot(false);
            }, t("Đã lưu bot. Tiếp tục bật bot để lấy mã liên kết."))}>{t("Lưu bot")}</Button>
          </>}
          {telegram?.configured && !telegram.paired && <Box sx={{ borderTop: 1, borderColor: "divider", pt: 2 }}>
            <Typography variant="subtitle2" gutterBottom>{t("Liên kết Telegram của bạn")}</Typography>
            {!runtime?.active ? <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t("Sau khi liên kết DX-OS và chọn model, bật bot rồi mở Telegram để nhắn /start.")}</Typography>
              <Button variant="contained" disabled={busy || loading || !linked || !profiles.selected || !runtime?.provisioned}
                onClick={() => void run(() => assistantRequest("/me/runtime/start", "POST", {}), t("Bot đang khởi động. Mở Telegram, chờ bot sẵn sàng rồi nhắn /start."))}>{t("Bật bot để lấy mã liên kết")}</Button>
            </> : <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t("Mở bot ở trên, nhắn /start rồi nhập mã bot gửi về. Mã này xác nhận ai được phép dùng bot.")}</Typography>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                <TextField label={t("Mã liên kết do bot gửi")} value={code} onChange={e => setCode(e.target.value.toUpperCase())} />
                <Button variant="contained" disabled={busy || loading || !code.trim()} onClick={() => void run(async () => {
                  await assistantRequest("/me/telegram/pairing", "POST", { code: code.trim() }); setCode("");
                }, t("Đã liên kết Telegram. Bấm Bật trợ lý ở đầu trang để bắt đầu."))}>{t("Xác nhận liên kết")}</Button>
              </Stack>
            </>}
          </Box>}
          {telegram?.paired && <Typography variant="body2" color="text.secondary">{t("Bạn đã có thể trò chuyện với bot. Các thao tác giao việc hoặc chuyển trạng thái vẫn cần bạn xác nhận.")}</Typography>}
        </Stack>
      </CardContent></Card>
      </>}
      {tab === 1 && notifications && <Card><CardContent>
        <Typography variant="h6" gutterBottom>{t("Thông báo công việc")}</Typography>
        <Typography variant="body2" color="text.secondary">{t("Nhận việc mới, nhắc trước hạn 15 phút và nhắc quá hạn trên Telegram. Trợ lý cần đang bật; lượt đầu không gửi lại toàn bộ việc cũ.")}</Typography>
        <FormControlLabel label={t("Nhận việc mới và nhắc hạn")} control={<Switch checked={notifications.enabled} disabled={busy || loading || !telegram?.paired || !linked}
          onChange={(_, enabled) => void run(() => assistantRequest("/me/notifications", "PUT", { enabled, timeZone: notifications.timeZone, digestHour: notifications.digestHour }))} />} />
        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "minmax(0, 1fr)", md: "minmax(0, 1fr) 240px auto" }, gap: 2, alignItems: "start" }}>
          <Autocomplete disableClearable options={Array.from(new Set([notifications.timeZone, ...timeZones]))}
            value={notifications.timeZone} getOptionLabel={zoneLabel} sx={{ flex: 1, minWidth: 0 }}
            onChange={(_, timeZone) => setNotifications({ ...notifications, timeZone })}
            renderInput={params => <TextField {...params} label={t("Múi giờ")} helperText={t("Bấm để chọn hoặc gõ tên khu vực.")} />} />
          <TextField select label={t("Giờ gửi tổng kết mỗi ngày")} value={notifications.digestHour} sx={{ minWidth: 220 }}
            onChange={e => setNotifications({ ...notifications, digestHour: Number(e.target.value) })} helperText={t("Theo múi giờ bạn chọn.")}>
            {Array.from({ length: 24 }, (_, hour) => <MenuItem key={hour} value={hour}>{String(hour).padStart(2, "0")}:00</MenuItem>)}
          </TextField>
          <Button disabled={busy || loading} onClick={() => void run(() => assistantRequest("/me/notifications", "PUT", { enabled: notifications.enabled,
            timeZone: notifications.timeZone, digestHour: notifications.digestHour }))}>{t("Lưu lịch")}</Button>
        </Box>
        {notifications.lastScan && <Typography variant="body2" sx={{ mt: 1 }}>{t("Quét gần nhất: {time}", { time: new Date(notifications.lastScan).toLocaleString(getLocale() === "en" ? "en-US" : "vi-VN") })}</Typography>}
        {notifications.error && <Alert severity="warning" sx={{ mt: 1 }}>{notifications.error}</Alert>}
        {notifications.uncertain > 0 && <Alert severity="warning" sx={{ mt: 1 }}>{t("Có {n} lượt gửi chưa rõ kết quả; hệ thống không tự gửi lại để tránh trùng.", { n: notifications.uncertain })}</Alert>}
      </CardContent></Card>}
      {tab === 2 && <Card><CardContent>
        <Typography variant="h6" gutterBottom>{t("Xác nhận và lịch sử thao tác")}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t("Khi bot đề xuất thay đổi hồ sơ, bạn có thể xem và xác nhận tại đây. Thao tác ảnh hưởng nhóm quyền phải được xác nhận trên web; kết quả xử lý cũng được lưu trong mục này.")}</Typography>
        {!proposals.length && <Typography variant="body2">{t("Chưa có thao tác cần hiển thị. Hãy yêu cầu bot xử lý một hồ sơ để bắt đầu; chỉ các hồ sơ bạn còn quyền đọc mới xuất hiện ở đây.")}</Typography>}
        <Stack spacing={2}>{proposals.map(proposal => <Box key={proposal.id} sx={{ borderBottom: 1, borderColor: "divider", pb: 1 }}>
          <Typography sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{proposal.snapshot.summary}</Typography>
          <Chip sx={{ mt: 1 }} size="small" label={proposal.state === "pending" && proposal.expires <= Date.now() ? t("Hết hạn") : stateLabels[proposal.state] ?? proposal.state} />
          {proposal.state === "pending" && proposal.expires > Date.now() && <>
            <Button disabled={busy || loading} onClick={() => setConfirm(proposal)}>{t("Xem và xác nhận")}</Button>
            <Button disabled={busy || loading} onClick={() => void run(() => assistantRequest(`/me/proposals/${proposal.id}/cancel`, "POST", {}))}>{t("Hủy")}</Button>
          </>}
          {proposal.state === "uncertain" && <Typography variant="body2" color="warning.main">{t("Đối chiếu lịch sử hồ sơ trước khi tạo đề xuất khác.")}</Typography>}
        </Box>)}</Stack>
        {(page > 0 || proposals.length > 0) && <Box sx={{ mt: 2 }}>
        <Button disabled={busy || loading || page === 0} onClick={() => setPage(page - 1)}>{t("Trang trước")}</Button>
        <Button disabled={busy || loading || proposals.length < 5} onClick={() => setPage(page + 1)}>Trang sau</Button>
        </Box>}
      </CardContent></Card>}
    </Stack>
    <Dialog open={!!confirm} onClose={() => { if (!busy) setConfirm(null); }} fullWidth maxWidth="sm">
      <DialogTitle>{t("Xác nhận thao tác")}</DialogTitle>
      <DialogContent>
        <Typography sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{confirm?.snapshot.summary}</Typography>
        {confirm?.snapshot.effects?.map((effect, index) => <Alert severity="warning" key={index} sx={{ mt: 2 }}>
          {t("{effect}: {group}. Đối tượng: {source}.", { effect: effect.operation === "add_to_group" ? t("Thêm vào nhóm") : effect.operation === "remove_from_group" ? t("Xóa khỏi nhóm") : t("Tác động bổ sung"), group: effect.group, source: effect.userSource })}
          {effect.asynchronous && ` ${t("Tác vụ chạy bất đồng bộ sau quyết định; ghi nhận quyết định không đồng nghĩa tác vụ đã hoàn tất.")}`}
        </Alert>)}
      </DialogContent>
      <DialogActions><Button disabled={busy || loading} onClick={() => setConfirm(null)}>{t("Quay lại")}</Button>
        <Button variant="contained" disabled={busy || loading} onClick={() => { if (confirm) void run(async () => {
          await assistantRequest(`/me/proposals/${confirm.id}/confirm`, "POST", {}); setConfirm(null);
        }, t("Đã ghi nhận thao tác. Tác vụ đi kèm có thể cần thêm thời gian để hoàn tất.")); }}>{t("Xác nhận thực hiện")}</Button>
      </DialogActions>
    </Dialog>
  </Box>;
}
