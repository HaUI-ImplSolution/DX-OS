// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { Title } from "react-admin";
import { Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions,
  DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, Switch, Tab, Tabs, TextField, Typography } from "@mui/material";
import PageHeader from "../shell/PageHeader";
import { assistantRequest, type Profiles, type Notifications, type Proposal } from "./api";

type Runtime = { active: boolean; provisioned: boolean };
type Telegram = { configured: boolean; paired: boolean; username: string | null; url: string | null };
const stateLabels: Record<string, string> = { pending: "Chờ xác nhận", executing: "Đang thực hiện", succeeded: "Đã thực hiện",
  failed: "Bị từ chối", uncertain: "Chưa rõ kết quả", cancelled: "Đã hủy", expired: "Hết hạn" };

const supportedTimeZones = (Intl as typeof Intl & { supportedValuesOf?: (key: "timeZone") => string[] }).supportedValuesOf;
const timeZones = Array.from(new Set(["Asia/Ho_Chi_Minh", "UTC",
  ...(supportedTimeZones ? supportedTimeZones("timeZone") :
    ["Asia/Bangkok", "Asia/Singapore", "Asia/Tokyo", "Europe/London", "America/New_York"])]));
const zoneLabel = (zone: string) => zone === "Asia/Ho_Chi_Minh" ? "Việt Nam · Asia/Ho_Chi_Minh" : zone.replace(/_/g, " ");

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
    const [p, r, t, n, history] = results;
    if (p?.status === "fulfilled") setProfiles(p.value as Profiles);
    if (r?.status === "fulfilled") setRuntime(r.value as Runtime);
    if (t?.status === "fulfilled") setTelegram(t.value as Telegram);
    if (n?.status === "fulfilled") setNotifications(n.value as Notifications);
    if (history?.status === "fulfilled") setProposals(history.value as Proposal[]);
    else setProposals([]);
    const failure = results.find(item => item.status === "rejected");
    if (failure?.status === "rejected") throw failure.reason;
  }, [page]);
  useEffect(() => { let current = true;
    setLoading(true);
    load().catch(e => { if (current) setError(e instanceof Error ? e.message : "Không kết nối được trợ lý"); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [load]);
  const run = async (action: () => Promise<unknown>, message?: string) => {
    setBusy(true); setError(null); setNote(null);
    try { await action(); if (message) setNote(message); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "Chưa hoàn tất thao tác"); }
    finally { setBusy(false); }
  };
  const link = async () => {
    const result = await assistantRequest<{ url: string }>("/me/core/link", "POST", {});
    window.location.assign(result.url);
  };

  return <Box sx={{ maxWidth: 960, mx: "auto", width: "100%", minWidth: 0 }}>
    <Title title="Trợ lý cá nhân" />
    <PageHeader title="Trợ lý cá nhân" subtitle="Đọc, theo dõi và xử lý hồ sơ qua Telegram với quyền của bạn. Mọi thay đổi cần xác nhận."
      actions={<Button disabled={busy || loading} onClick={() => void run(load)}>Tải lại</Button>} />
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
    {note && <Alert severity="success" sx={{ mb: 2 }}>{note}</Alert>}
    {loading && <CircularProgress size={24} />}
    <Card sx={{ mb: 2.5 }}><CardContent>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} justifyContent="space-between" alignItems={{ sm: "center" }}>
        <Box>
          <Typography variant="subtitle1" fontWeight={700}>{runtime?.active ? "Trợ lý đang hoạt động" : telegram?.paired ? "Sẵn sàng bật trợ lý" : "Thiết lập kết nối của bạn"}</Typography>
          <Typography variant="body2" color="text.secondary">{runtime?.active ? "Giữ trợ lý bật để nhận tin nhắn và thông báo trên Telegram." : "Kết nối tài khoản, chọn model API và liên kết bot Telegram riêng."}</Typography>
        </Box>
        {runtime?.active && <Button variant="outlined" disabled={busy || loading}
          onClick={() => void run(() => assistantRequest("/me/runtime/stop", "POST", {}), "Đã dừng trợ lý.")}>Dừng trợ lý</Button>}
        {telegram?.paired && !runtime?.active && <Button variant="contained" disabled={busy || loading || !linked || !profiles.selected || !runtime?.provisioned}
          onClick={() => void run(() => assistantRequest("/me/runtime/start", "POST", {}), "Đang khởi động trợ lý. Bot có thể cần một lúc để sẵn sàng.")}>Bật trợ lý</Button>}
      </Stack>
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", mt: 2 }}>
        <Chip size="small" label={linked ? "DX-OS đã liên kết" : "DX-OS chưa liên kết"} color={linked ? "success" : "default"} />
        <Chip size="small" label={profiles.selected ? "Đã chọn model" : "Chưa chọn model"} color={profiles.selected ? "success" : "default"} />
        <Chip size="small" label={telegram?.paired ? "Telegram đã liên kết" : "Telegram chưa liên kết"} color={telegram?.paired ? "success" : "default"} />
      </Stack>
      {runtime && !runtime.provisioned && <Alert severity="info" sx={{ mt: 2 }}>Chưa có chỗ chạy trợ lý. Hãy liên hệ quản trị viên để chuẩn bị thêm.</Alert>}
      {capacity && <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>Quản trị: đã cấp {capacity.assigned}/{capacity.provisioned} chỗ chạy; tối đa {capacity.capacity}.</Typography>}
    </CardContent></Card>
    <Tabs value={tab} onChange={(_, value: number) => setTab(value)} aria-label="Cài đặt trợ lý" variant="scrollable" scrollButtons="auto" sx={{ mb: 2.5, borderBottom: 1, borderColor: "divider" }}>
      <Tab id="assistant-tab-0" aria-controls="assistant-panel-0" label="Kết nối" />
      <Tab id="assistant-tab-1" aria-controls="assistant-panel-1" label="Thông báo" />
      <Tab id="assistant-tab-2" aria-controls="assistant-panel-2" label="Hoạt động" />
    </Tabs>
    <Stack spacing={2} role="tabpanel" id={`assistant-panel-${tab}`} aria-labelledby={`assistant-tab-${tab}`}>
      {tab === 0 && <>
      <Card><CardContent>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} justifyContent="space-between" alignItems={{ sm: "center" }}>
          <Box><Typography variant="h6">Tài khoản DX-OS</Typography>
            <Typography variant="body2" color="text.secondary">Trợ lý chỉ đọc và xử lý hồ sơ trong quyền của tài khoản bạn.</Typography></Box>
          <Button variant="outlined" disabled={busy || loading} onClick={() => void run(link)}>{linked ? "Liên kết lại DX-OS" : "Liên kết DX-OS"}</Button>
        </Stack>
      </CardContent></Card>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Model trả lời</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Chọn dịch vụ bạn sử dụng và model có hỗ trợ công cụ. Lưu key chưa gọi API; kiểm kết nối có thể tiêu quota.</Typography>
        <Stack spacing={2}>
          {profiles.profiles.length > 0 && <Button sx={{ alignSelf: "flex-start" }} onClick={() => setAddProfile(!addProfile)}>{addProfile ? "Đóng biểu mẫu" : "Thêm model API"}</Button>}
          {(!profiles.profiles.length || addProfile) && <>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField select label="Nhà cung cấp" value={provider} onChange={e => setProvider(e.target.value)} sx={{ minWidth: 180 }}>
              <MenuItem value="gemini">Gemini</MenuItem><MenuItem value="openai">OpenAI</MenuItem>
              <MenuItem value="anthropic">Anthropic</MenuItem><MenuItem value="compatible" disabled>OpenAI-compatible (chưa mở)</MenuItem>
            </TextField>
            <TextField fullWidth label="Tên model" value={model} onChange={e => setModel(e.target.value)} helperText="Nhập đúng tên model đang khả dụng với API key của bạn." />
          </Stack>
          <TextField label="API key" type="password" autoComplete="off" value={apiKey} onChange={e => setApiKey(e.target.value)} />
          <Button sx={{ alignSelf: "flex-start" }} variant="outlined" disabled={busy || loading || !model.trim() || !apiKey.trim()}
            onClick={() => void run(async () => {
              await assistantRequest("/me/providers", "POST", { provider, model: model.trim(), apiKey }); setApiKey(""); setAddProfile(false);
            }, "Đã lưu kết nối. Bạn có thể kiểm tra model trước khi sử dụng.")}>Lưu kết nối</Button>
          </>}
          {profiles.profiles.map(profile => <Stack key={profile.id} direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }}>
            <Typography sx={{ flex: 1 }}>{profile.provider} · {profile.model}</Typography>
            {profiles.selected === profile.id && <Chip size="small" label="Đang chọn" color="primary" />}
            <Button disabled={busy || loading || profile.provider === "compatible" || profiles.selected === profile.id} onClick={() => void run(() => assistantRequest(`/me/providers/${profile.id}/select`, "POST", {}))}>Chọn</Button>
            <Button disabled={busy || loading || profile.provider === "compatible" || !runtime?.provisioned} onClick={() => {
              if (window.confirm("Kiểm tra này gọi API thật và có thể tiêu quota. Tiếp tục?")) void run(async () => {
                await assistantRequest(`/me/providers/${profile.id}/probe`, "POST", { acknowledgeCost: true });
              }, "Model đã kết nối và gọi được công cụ kiểm tra.");
            }}>Kiểm kết nối</Button>
            <Button color="error" disabled={busy || loading} onClick={() => {
              if (window.confirm(`Xóa cấu hình ${profile.provider} · ${profile.model}? Thao tác này dừng trợ lý, không thu hồi API key tại nhà cung cấp.`))
                void run(() => assistantRequest(`/me/providers/${profile.id}`, "DELETE"));
            }}>Xóa</Button>
          </Stack>)}
        </Stack>
      </CardContent></Card>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Bot Telegram</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Bot là nơi bạn trò chuyện và nhận thông báo. Mỗi tài khoản dùng một bot riêng.</Typography>
        <Stack spacing={2}>
          {telegram?.configured && <Stack direction="row" spacing={1} alignItems="center" useFlexGap sx={{ flexWrap: "wrap" }}>
            <Button variant="outlined" component="a" href={telegram.url ?? undefined} target="_blank" rel="noreferrer">Mở @{telegram.username}</Button>
            <Chip size="small" label={telegram.paired ? "Đã liên kết" : "Chờ liên kết"} color={telegram.paired ? "success" : "default"} />
            <Button onClick={() => setChangeBot(!changeBot)}>{changeBot ? "Giữ bot hiện tại" : "Đổi bot"}</Button>
            <Button color="error" disabled={busy || loading} onClick={() => {
              if (window.confirm("Ngắt bot sẽ dừng trợ lý và xóa liên kết Telegram cùng lịch thông báo. API key vẫn được giữ. Tiếp tục?"))
                void run(async () => { await assistantRequest("/me/telegram", "DELETE", {}); setCode(""); setToken(""); setChangeBot(false); }, "Đã ngắt bot Telegram.");
            }}>Ngắt bot</Button>
          </Stack>}
          {(!telegram?.configured || changeBot) && <>
            <Typography variant="body2">Mở @BotFather trên Telegram, tạo bot bằng /newbot rồi chép token được cấp vào đây.</Typography>
            {changeBot && <Alert severity="warning">Đổi bot sẽ dừng trợ lý và yêu cầu liên kết Telegram lại.</Alert>}
            <TextField label="Bot token" type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)} helperText="Token của bot, không phải mã đăng nhập tài khoản Telegram." />
            <Button variant="outlined" sx={{ alignSelf: "flex-start" }} disabled={busy || loading || !token.trim()} onClick={() => void run(async () => {
              await assistantRequest("/me/telegram", "PUT", { token: token.trim() }); setToken(""); setChangeBot(false);
            }, "Đã lưu bot. Tiếp tục bật bot để lấy mã liên kết.")}>Lưu bot</Button>
          </>}
          {telegram?.configured && !telegram.paired && <Box sx={{ borderTop: 1, borderColor: "divider", pt: 2 }}>
            <Typography variant="subtitle2" gutterBottom>Liên kết Telegram của bạn</Typography>
            {!runtime?.active ? <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Sau khi liên kết DX-OS và chọn model, bật bot rồi mở Telegram để nhắn /start.</Typography>
              <Button variant="contained" disabled={busy || loading || !linked || !profiles.selected || !runtime?.provisioned}
                onClick={() => void run(() => assistantRequest("/me/runtime/start", "POST", {}), "Bot đang khởi động. Mở Telegram, chờ bot sẵn sàng rồi nhắn /start.")}>Bật bot để lấy mã liên kết</Button>
            </> : <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Mở bot ở trên, nhắn /start rồi nhập mã bot gửi về. Mã này xác nhận ai được phép dùng bot.</Typography>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                <TextField label="Mã liên kết do bot gửi" value={code} onChange={e => setCode(e.target.value.toUpperCase())} />
                <Button variant="contained" disabled={busy || loading || !code.trim()} onClick={() => void run(async () => {
                  await assistantRequest("/me/telegram/pairing", "POST", { code: code.trim() }); setCode("");
                }, "Đã liên kết Telegram. Bấm Bật trợ lý ở đầu trang để bắt đầu.")}>Xác nhận liên kết</Button>
              </Stack>
            </>}
          </Box>}
          {telegram?.paired && <Typography variant="body2" color="text.secondary">Bạn đã có thể trò chuyện với bot. Các thao tác giao việc hoặc chuyển trạng thái vẫn cần bạn xác nhận.</Typography>}
        </Stack>
      </CardContent></Card>
      </>}
      {tab === 1 && notifications && <Card><CardContent>
        <Typography variant="h6" gutterBottom>Thông báo công việc</Typography>
        <Typography variant="body2" color="text.secondary">Nhận việc mới, nhắc trước hạn 15 phút và nhắc quá hạn trên Telegram. Trợ lý cần đang bật; lượt đầu không gửi lại toàn bộ việc cũ.</Typography>
        <FormControlLabel label="Nhận việc mới và nhắc hạn" control={<Switch checked={notifications.enabled} disabled={busy || loading || !telegram?.paired || !linked}
          onChange={(_, enabled) => void run(() => assistantRequest("/me/notifications", "PUT", { enabled, timeZone: notifications.timeZone, digestHour: notifications.digestHour }))} />} />
        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "minmax(0, 1fr)", md: "minmax(0, 1fr) 240px auto" }, gap: 2, alignItems: "start" }}>
          <Autocomplete disableClearable options={Array.from(new Set([notifications.timeZone, ...timeZones]))}
            value={notifications.timeZone} getOptionLabel={zoneLabel} sx={{ flex: 1, minWidth: 0 }}
            onChange={(_, timeZone) => setNotifications({ ...notifications, timeZone })}
            renderInput={params => <TextField {...params} label="Múi giờ" helperText="Bấm để chọn hoặc gõ tên khu vực." />} />
          <TextField select label="Giờ gửi tổng kết mỗi ngày" value={notifications.digestHour} sx={{ minWidth: 220 }}
            onChange={e => setNotifications({ ...notifications, digestHour: Number(e.target.value) })} helperText="Theo múi giờ bạn chọn.">
            {Array.from({ length: 24 }, (_, hour) => <MenuItem key={hour} value={hour}>{String(hour).padStart(2, "0")}:00</MenuItem>)}
          </TextField>
          <Button disabled={busy || loading} onClick={() => void run(() => assistantRequest("/me/notifications", "PUT", { enabled: notifications.enabled,
            timeZone: notifications.timeZone, digestHour: notifications.digestHour }))}>Lưu lịch</Button>
        </Box>
        {notifications.lastScan && <Typography variant="body2" sx={{ mt: 1 }}>Quét gần nhất: {new Date(notifications.lastScan).toLocaleString("vi-VN")}</Typography>}
        {notifications.error && <Alert severity="warning" sx={{ mt: 1 }}>{notifications.error}</Alert>}
        {notifications.uncertain > 0 && <Alert severity="warning" sx={{ mt: 1 }}>Có {notifications.uncertain} lượt gửi chưa rõ kết quả; hệ thống không tự gửi lại để tránh trùng.</Alert>}
      </CardContent></Card>}
      {tab === 2 && <Card><CardContent>
        <Typography variant="h6" gutterBottom>Xác nhận và lịch sử thao tác</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Khi bot đề xuất thay đổi hồ sơ, bạn có thể xem và xác nhận tại đây. Thao tác ảnh hưởng nhóm quyền phải được xác nhận trên web; kết quả xử lý cũng được lưu trong mục này.</Typography>
        {!proposals.length && <Typography variant="body2">Chưa có thao tác cần hiển thị. Hãy yêu cầu bot xử lý một hồ sơ để bắt đầu; chỉ các hồ sơ bạn còn quyền đọc mới xuất hiện ở đây.</Typography>}
        <Stack spacing={2}>{proposals.map(proposal => <Box key={proposal.id} sx={{ borderBottom: 1, borderColor: "divider", pb: 1 }}>
          <Typography sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{proposal.snapshot.summary}</Typography>
          <Chip sx={{ mt: 1 }} size="small" label={proposal.state === "pending" && proposal.expires <= Date.now() ? "Hết hạn" : stateLabels[proposal.state] ?? proposal.state} />
          {proposal.state === "pending" && proposal.expires > Date.now() && <>
            <Button disabled={busy || loading} onClick={() => setConfirm(proposal)}>Xem và xác nhận</Button>
            <Button disabled={busy || loading} onClick={() => void run(() => assistantRequest(`/me/proposals/${proposal.id}/cancel`, "POST", {}))}>Hủy</Button>
          </>}
          {proposal.state === "uncertain" && <Typography variant="body2" color="warning.main">Đối chiếu lịch sử hồ sơ trước khi tạo đề xuất khác.</Typography>}
        </Box>)}</Stack>
        {(page > 0 || proposals.length > 0) && <Box sx={{ mt: 2 }}>
        <Button disabled={busy || loading || page === 0} onClick={() => setPage(page - 1)}>Trang trước</Button>
        <Button disabled={busy || loading || proposals.length < 5} onClick={() => setPage(page + 1)}>Trang sau</Button>
        </Box>}
      </CardContent></Card>}
    </Stack>
    <Dialog open={!!confirm} onClose={() => { if (!busy) setConfirm(null); }} fullWidth maxWidth="sm">
      <DialogTitle>Xác nhận thao tác</DialogTitle>
      <DialogContent>
        <Typography sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{confirm?.snapshot.summary}</Typography>
        {confirm?.snapshot.effects?.map((effect, index) => <Alert severity="warning" key={index} sx={{ mt: 2 }}>
          {effect.operation === "add_to_group" ? "Thêm vào nhóm" : effect.operation === "remove_from_group" ? "Xóa khỏi nhóm" : "Tác động bổ sung"}: {effect.group}. Đối tượng: {effect.userSource}.
          {effect.asynchronous && " Tác vụ chạy bất đồng bộ sau quyết định; ghi nhận quyết định không đồng nghĩa tác vụ đã hoàn tất."}
        </Alert>)}
      </DialogContent>
      <DialogActions><Button disabled={busy || loading} onClick={() => setConfirm(null)}>Quay lại</Button>
        <Button variant="contained" disabled={busy || loading} onClick={() => { if (confirm) void run(async () => {
          await assistantRequest(`/me/proposals/${confirm.id}/confirm`, "POST", {}); setConfirm(null);
        }, "Đã ghi nhận thao tác. Tác vụ đi kèm có thể cần thêm thời gian để hoàn tất."); }}>Xác nhận thực hiện</Button>
      </DialogActions>
    </Dialog>
  </Box>;
}
