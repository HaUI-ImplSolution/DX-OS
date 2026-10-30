// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { getApprovalRequest } from "../api";
import type { CaseUi } from "../appModule";
import Badge from "../components/Badge";
import Section from "../components/Section";
import Timeline from "../components/Timeline";
import { statusLabel, statusTone } from "../labels";
import type { ApprovalRequestDetail } from "../types";

interface CaseDetailProps {
  role: string;
  onError: (message: string) => void;
  ui: CaseUi;
}

export default function CaseDetail({ role, onError, ui }: CaseDetailProps) {
  const { caseId } = useParams();
  const location = useLocation();
  // Back to the list as it was opened, with its filters.
  const from = (location.state as { from?: string } | null)?.from;
  const [detail, setDetail] = useState<ApprovalRequestDetail | null>(null);

  const load = useCallback(() => {
    getApprovalRequest(Number(caseId))
      .then(setDetail)
      .catch((err) => onError(err.message));
  }, [caseId, onError]);

  useEffect(load, [load]);

  if (!detail) return <Section step="…" title="Đang tải yêu cầu" children={null} />;
  const { request, events } = detail;
  const unit = ui.unit.display(request);

  return (
    <>
      <Section
        step="🔎"
        title={request.title}
        subtitle={`${ui.caseTypeLabels[request.caseType] ?? request.caseType} · ${ui.unit.detailPrefix} ${
          unit.code ?? "—"
        }${unit.person ? ` (${unit.person})` : ""}`}
      >
        <p>
          <Badge tone={statusTone(request.status)}>{statusLabel(request.status)}</Badge>
          {request.assignedToName && <span> · Đang xử lý: {request.assignedToName}</span>}
        </p>
        {ui.renderDetails(request)}
        {ui.renderActions(request, { role, onDone: load, onError })}
      </Section>
      <Section step="🕓" title="Lịch sử xử lý" subtitle="Mỗi bước được ghi lại, không sửa hay xoá">
        <Timeline events={events} />
      </Section>
      <Link className="btn btn-link" to={from?.startsWith("/requests") ? from : "/requests"}>
        ← Về danh sách yêu cầu
      </Link>
    </>
  );
}
