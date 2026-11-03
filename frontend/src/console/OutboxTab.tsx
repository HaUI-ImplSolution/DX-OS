// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  Pagination,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from "@mui/material";
import { listOutbox, retryOutbox, type OutboxPage, type OutboxStatus } from "./api";

const SIZE = 20;
const STATUS_LABEL: Record<OutboxStatus, string> = { failed: "Lỗi", pending: "Chờ gửi", delivered: "Đã gửi" };
const STATUSES: OutboxStatus[] = ["failed", "pending", "delivered"];

const TOPIC_LABEL: Record<string, string> = { completion: "Hành động hoàn tất" };

const message = (error: unknown) => (error instanceof Error ? error.message : "Không xử lý được");
const when = (value: string | null) => (value ? new Date(value).toLocaleString("vi-VN") : "");

// The messages that leave the system after a change, with the error of those that failed.
export default function OutboxTab() {
  const [status, setStatus] = useState<OutboxStatus>("failed");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<OutboxPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  const load = useCallback(() => {
    listOutbox(status, page, SIZE).then(
      (loaded) => {
        setData(loaded);
        setError(null);
      },
      (e: unknown) => setError(message(e)),
    );
  }, [status, page]);

  useEffect(load, [load]);

  const retry = async (id: number) => {
    try {
      await retryOutbox(id);
      setNote(`Đã xếp thông điệp ${id} để thử lại; mở lại tab này sau vài giây để xem kết quả.`);
      load();
    } catch (e) {
      setError(message(e));
    }
  };

  return (
    <Box>
      {error && <Alert severity="error">{error}</Alert>}
      {note && <Alert severity="info">{note}</Alert>}
      <Tabs
        value={status}
        onChange={(_, next: OutboxStatus) => {
          setStatus(next);
          setPage(0);
          setNote(null);
        }}
        sx={{ mb: 1 }}
      >
        {STATUSES.map((name) => (
          <Tab key={name} value={name} label={`${STATUS_LABEL[name]} (${data?.counts[name] ?? 0})`} />
        ))}
      </Tabs>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        Các thông điệp gửi đi sau khi một thay đổi đã lưu, như thêm người dùng vào nhóm. Thông điệp lỗi được thử lại dần;
        bấm "Thử lại ngay" khi đã sửa nguyên nhân.
      </Typography>
      {data && data.page.items.length === 0 && (
        <Card sx={{ py: 6, textAlign: "center", color: "text.secondary" }}>Không có thông điệp nào.</Card>
      )}
      {data && data.page.items.length > 0 && (
        <Card>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Mã</TableCell>
              <TableCell>Loại</TableCell>
              <TableCell>Hồ sơ</TableCell>
              <TableCell>Số lần thử</TableCell>
              <TableCell>{status === "delivered" ? "Đã gửi lúc" : "Thử lại lúc"}</TableCell>
              <TableCell>Lỗi gần nhất</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {data.page.items.map((item) => (
              <TableRow key={item.id}>
                <TableCell>{item.id}</TableCell>
                <TableCell>{TOPIC_LABEL[item.topic] ?? item.topic}</TableCell>
                <TableCell>{`${item.model}.${item.entity} #${item.record_id}`}</TableCell>
                <TableCell>{item.attempts}</TableCell>
                <TableCell>{when(item.delivered_at ?? item.next_attempt_at)}</TableCell>
                <TableCell sx={{ maxWidth: 360, wordBreak: "break-word" }}>{item.last_error}</TableCell>
                <TableCell align="right">
                  {item.delivered_at === null && (
                    <Button size="small" onClick={() => retry(item.id)}>
                      Thử lại ngay
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        </Card>
      )}
      {data && data.page.totalPages > 1 && (
        <Pagination
          sx={{ mt: 2 }}
          count={data.page.totalPages}
          page={page + 1}
          onChange={(_, next) => setPage(next - 1)}
        />
      )}
    </Box>
  );
}
