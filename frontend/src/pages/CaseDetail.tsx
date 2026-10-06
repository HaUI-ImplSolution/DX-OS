import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getApprovalRequest } from "../api";
import Badge from "../components/Badge";
import DecisionActions from "../components/DecisionActions";
import RequestDetails from "../components/RequestDetails";
import Section from "../components/Section";
import Timeline from "../components/Timeline";
import { CASE_TYPE_LABEL, STATUS_LABEL, STATUS_TONE } from "../labels";
import type { ApprovalRequestDetail, Role } from "../types";

interface CaseDetailProps {
  role: Role;
  onError: (message: string) => void;
}

export default function CaseDetail({ role, onError }: CaseDetailProps) {
  const { caseId } = useParams();
  const [detail, setDetail] = useState<ApprovalRequestDetail | null>(null);

  const load = useCallback(() => {
    getApprovalRequest(Number(caseId))
      .then(setDetail)
      .catch((err) => onError(err.message));
  }, [caseId, onError]);

  useEffect(load, [load]);

  if (!detail) return <Section step="…" title="Đang tải yêu cầu" children={null} />;
  const { request, events } = detail;

  return (
    <>
      <Section
        step="🔎"
        title={request.title}
        subtitle={`${CASE_TYPE_LABEL[request.caseType] ?? request.caseType} · hộ ${request.householdCode ?? "—"}${
          request.farmerName ? ` (${request.farmerName})` : ""
        }`}
      >
        <p>
          <Badge tone={STATUS_TONE[request.status]}>{STATUS_LABEL[request.status]}</Badge>
          {request.assignedToName && <span> · Đang xử lý: {request.assignedToName}</span>}
        </p>
        <RequestDetails request={request} />
        <DecisionActions request={request} role={role} onDone={load} onError={onError} />
      </Section>
      <Section step="🕓" title="Lịch sử xử lý" subtitle="Mỗi bước được ghi lại, không sửa hay xoá">
        <Timeline events={events} />
      </Section>
      <Link className="btn btn-link" to="/requests">
        ← Về danh sách yêu cầu
      </Link>
    </>
  );
}
