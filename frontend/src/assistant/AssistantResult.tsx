// SPDX-License-Identifier: Apache-2.0
import { useEffect, useId, useState } from "react";
import { Link } from "react-router-dom";
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Card, Chip, Stack, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { resourceName, type EntityMeta, type ModelMeta } from "../admin/types";
import { formatDateTime } from "../shell/format";
import type { AssistantResult as Result } from "./api";

const REASONS: Record<string, string> = {
  DISABLED: "Trợ lý chưa được bật. Bạn vẫn có thể dùng danh sách và bộ lọc hồ sơ.",
  AI_UNAVAILABLE: "Trợ lý đang không sẵn sàng. Bạn vẫn có thể dùng danh sách và bộ lọc hồ sơ.",
  QUERY_NOT_INTERPRETED: "Chưa xác định đầy đủ yêu cầu. Hãy diễn đạt rõ hơn, tắt diễn giải điều kiện hoặc dùng bộ lọc danh sách.",
  FILTER_CONFLICT: "Câu hỏi và bộ lọc có điều kiện khác nhau. Hãy kiểm tra trước khi tìm lại.",
  CANDIDATE_LIMIT: "Có quá nhiều hồ sơ. Hãy thêm bộ lọc trong danh sách để thu hẹp phạm vi tìm kiếm.",
  HISTORY_LIMIT: "Lịch sử quá dài. Hãy chọn khoảng thời gian nhỏ hơn trong tùy chọn nâng cao.",
  FIELD_LIMIT: "Nội dung hồ sơ vượt giới hạn đọc của trợ lý. Bạn có thể mở hồ sơ để xem nguyên văn.",
  CONTEXT_LIMIT: "Nội dung vượt giới hạn xử lý. Hãy thu hẹp bộ lọc hoặc khoảng lịch sử.",
  SOURCES_CHANGED: "Dữ liệu đã thay đổi trong lúc xử lý. Hãy chạy lại để đọc thông tin mới nhất.",
  INVALID_EVIDENCE: "Chưa kiểm chứng được nguồn kết quả. Hãy thử lại sau.",
  NO_MATCH: "Không tìm thấy hồ sơ phù hợp trong phạm vi đã chọn.",
  TIMEOUT: "Trợ lý đã hết thời gian xử lý. Hãy thu hẹp phạm vi hoặc thử lại sau.",
};

export type RecordOperation = "summarize" | "similar";

export default function AssistantResult({
  result,
  model,
  entity,
  onSelect,
}: {
  result: Result;
  model: ModelMeta;
  entity: EntityMeta;
  onSelect?: (id: number, operation: RecordOperation) => void;
}) {
  const prefix = useId();
  const [sourcesOpen, setSourcesOpen] = useState(false);
  const [source, setSource] = useState<string | null>(null);
  useEffect(() => {
    setSourcesOpen(false);
    setSource(null);
  }, [result]);
  useEffect(() => {
    if (!sourcesOpen || !source) return;
    const frame = requestAnimationFrame(() => {
      const node = document.getElementById(`${prefix}-${source}`);
      node?.scrollIntoView({ block: "nearest" });
      node?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(frame);
  }, [sourcesOpen, source, prefix]);

  if (result.status !== "FOUND")
    return (
      <Alert severity={result.status === "NO_EVIDENCE" ? "info" : "warning"}>
        {REASONS[result.reasonCode] ?? "Chưa thể trả kết quả. Bạn có thể tiếp tục tra cứu trong danh sách hồ sơ."}
      </Alert>
    );

  return (
    <Stack spacing={2} aria-live="polite" sx={{ minWidth: 0, overflowWrap: "anywhere" }}>
      <Typography variant="body2" color="text.secondary">
        Hiển thị {result.records.length} kết quả; đã xét {result.coverage.candidateCount} trong {result.coverage.totalMatching} hồ sơ theo
        quyền và bộ lọc. Thông tin đọc lúc {formatDateTime(result.readAt)}.
      </Typography>
      {Object.keys(result.filters).length > 0 && (
        <Typography variant="body2">
          Điều kiện:{" "}
          {Object.entries(result.filters)
            .map(([key, value]) => {
              const [name, operator] = key.split(".");
              const label =
                entity.fields.find((field) => field.name === name)?.label ??
                ({ status: "Trạng thái", created_at: "Ngày tạo", id: "Mã hồ sơ" } as Record<string, string>)[name] ??
                name;
              const choice =
                name === "status"
                  ? entity.process?.states.find((state) => state.name === value)?.label
                  : model.units.find((unit) => unit.name === value)?.label;
              const relation =
                (
                  { eq: "bằng", ne: "khác", like: "chứa", gte: "từ", lte: "đến", gt: "lớn hơn", lt: "nhỏ hơn", in: "thuộc" } as Record<
                    string,
                    string
                  >
                )[operator] ?? operator;
              return `${label} ${relation} ${choice ?? value}`;
            })
            .join("; ")}
          .
        </Typography>
      )}
      {result.records.map((item) => (
        <Card key={item.id} variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Typography
              component={Link}
              to={`/${resourceName(result.model, result.entity)}/${item.id}/show`}
              sx={{ color: "primary.main", fontWeight: 600, textDecoration: "none" }}
            >
              {item.title}
            </Typography>
            {item.status && (
              <Box>
                <Chip size="small" label={item.status} variant="outlined" />
                <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                  Trạng thái từ hồ sơ
                </Typography>
              </Box>
            )}
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              <Button component={Link} to={`/${resourceName(result.model, result.entity)}/${item.id}/show`} size="small">
                Mở hồ sơ
              </Button>
              {onSelect && (
                <>
                  <Button size="small" onClick={() => onSelect(item.id, "summarize")}>
                    Tóm tắt diễn biến
                  </Button>
                  <Button size="small" onClick={() => onSelect(item.id, "similar")}>
                    Tìm trường hợp tương tự
                  </Button>
                </>
              )}
            </Box>
          </Stack>
        </Card>
      ))}
      {result.summary.length > 0 && (
        <Box>
          <Typography variant="subtitle1" fontWeight={700}>
            Tổng hợp bằng AI
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Nội dung tham khảo; đối chiếu trạng thái hồ sơ và nguồn trước khi quyết định.
          </Typography>
          <Box component="ul" sx={{ pl: 2.5, mb: 0 }}>
            {result.summary.map((item, index) => (
              <Box component="li" key={index} sx={{ mb: 1 }}>
                <Typography component="span">{item.text}</Typography>{" "}
                {item.evidenceIds.map((id) => (
                  <Button
                    key={id}
                    size="small"
                    onClick={() => {
                      setSource(id);
                      setSourcesOpen(true);
                    }}
                  >
                    Nguồn {result.evidence.findIndex((evidence) => evidence.id === id) + 1}
                  </Button>
                ))}
              </Box>
            ))}
          </Box>
        </Box>
      )}
      {!["GENERATED", "NOT_NEEDED"].includes(result.generationStatus) && (
        <Alert severity="info">Chưa có phần tổng hợp AI. Bạn vẫn có thể đọc hồ sơ và đối chiếu nguồn bên dưới.</Alert>
      )}
      {result.coverage.historyWindow && (
        <Typography variant="body2" color="text.secondary">
          Khoảng lịch sử: {formatDateTime(result.coverage.historyWindow.from) || "từ đầu"} →{" "}
          {formatDateTime(result.coverage.historyWindow.to) || "hiện tại"}.
        </Typography>
      )}
      {result.evidence.length > 0 && (
        <Accordion expanded={sourcesOpen} onChange={(_, open) => setSourcesOpen(open)} disableGutters>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography>Xem nguồn đối chiếu ({result.evidence.length})</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              {result.evidence.map((item, index) => (
                <Box
                  key={item.id}
                  id={`${prefix}-${item.id}`}
                  tabIndex={-1}
                  sx={{ p: 1, borderLeft: 2, borderColor: source === item.id ? "primary.main" : "divider" }}
                >
                  <Typography variant="subtitle2">
                    Nguồn {index + 1} · {item.title}
                  </Typography>
                  <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", my: 1 }}>
                    {item.content}
                  </Typography>
                  <Button component={Link} to={`/${resourceName(result.model, result.entity)}/${item.recordId}/show`} size="small">
                    Mở hồ sơ gốc
                  </Button>
                </Box>
              ))}
            </Stack>
          </AccordionDetails>
        </Accordion>
      )}
      {result.operation === "similar" && (
        <Typography variant="body2" color="text.secondary">
          Trường hợp cũ chỉ dùng tham khảo, không khẳng định hồ sơ hiện tại có cùng nguyên nhân hoặc cách xử lý.
        </Typography>
      )}
    </Stack>
  );
}
