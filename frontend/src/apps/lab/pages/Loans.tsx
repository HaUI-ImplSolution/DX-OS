import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { Badge, Pagination, Section, formatDate, formatDateTime } from "../../../shell";
import type { Page } from "../../../shell";
import { approveLoan, cancelLoan, createLoan, listDevices, listLoans, rejectLoan, returnLoan } from "../api";
import { runAction, toIsoDate } from "../components/common";
import { LOAN_STATUS_LABEL, ROLE_LABEL } from "../labels";
import type { Device, Loan, ReturnCondition } from "../types";

interface LoansProps {
  userId: string;
  role: string;
  onError: (message: string) => void;
}

const PAGE_SIZE = 10;
const MAX_LOAN_DAYS = 7;

// A loan is decided by the role above the borrower; nobody decides their own.
function canDecide(loan: Loan, userId: string, role: string): boolean {
  if (loan.borrowerId === userId || loan.status !== "PENDING") return false;
  if (role === "ADMIN") return true;
  if (loan.borrowerRole === "STUDENT") return role === "LECTURER";
  if (loan.borrowerRole === "LECTURER") return role === "TECHNICIAN";
  return false;
}

export default function Loans({ userId, role, onError }: LoansProps) {
  const [available, setAvailable] = useState<Device[]>([]);
  const [result, setResult] = useState<Page<Loan> | null>(null);
  // Links from the overview open the list already filtered (?status=PENDING, ?mine=true, ?toApprove=true).
  const [params] = useSearchParams();
  const [status, setStatus] = useState(params.get("status") ?? "");
  const [mine, setMine] = useState(params.get("mine") === "true");
  const [toApprove, setToApprove] = useState(params.get("toApprove") === "true");
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);

  const sees = role !== "STUDENT";
  const isStaff = role === "TECHNICIAN" || role === "ADMIN";

  const loadLoans = useCallback(() => {
    listLoans({
      status: status || undefined,
      mine: mine || undefined,
      toApprove: toApprove || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [status, mine, toApprove, page, onError]);

  const loadDevices = useCallback(() => {
    listDevices({ status: "AVAILABLE" })
      .then(setAvailable)
      .catch((err) => onError(err.message));
  }, [onError]);

  useEffect(loadLoans, [loadLoans]);
  useEffect(loadDevices, [loadDevices]);

  const refresh = () => {
    loadLoans();
    loadDevices();
  };
  const act = (action: () => Promise<unknown>) => runAction(action, setBusy, onError, refresh);

  return (
    <>
      {role !== "TECHNICIAN" && (
        <RequestForm devices={available} onError={onError} onCreated={refresh} />
      )}

      <Section
        step="📦"
        title={sees && !mine ? "Phiếu mượn thiết bị" : "Phiếu mượn của tôi"}
        subtitle="Phiếu của sinh viên do giảng viên duyệt, phiếu của giảng viên do kỹ thuật viên duyệt"
      >
        <div className="filter-bar">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Mọi trạng thái</option>
            {Object.entries(LOAN_STATUS_LABEL).map(([key, value]) => (
              <option key={key} value={key}>
                {value.label}
              </option>
            ))}
          </select>
          {sees && (
            <label className="field-label check-label">
              <input
                type="checkbox"
                checked={mine}
                onChange={(e) => {
                  setMine(e.target.checked);
                  if (e.target.checked) setToApprove(false);
                  setPage(0);
                }}
              />{" "}
              Chỉ phiếu của tôi
            </label>
          )}
          {sees && (
            <label className="field-label check-label">
              <input
                type="checkbox"
                checked={toApprove}
                onChange={(e) => {
                  setToApprove(e.target.checked);
                  if (e.target.checked) setMine(false);
                  setPage(0);
                }}
              />{" "}
              Chỉ phiếu chờ tôi duyệt
            </label>
          )}
        </div>

        {result && result.items.length === 0 && <p className="empty-hint">Chưa có phiếu mượn nào.</p>}

        {result && result.items.length > 0 && (
          <table className="data-table">
            <thead>
              <tr>
                <th>Thiết bị</th>
                <th>Người mượn</th>
                <th>Mục đích</th>
                <th>Hạn trả</th>
                <th>Trạng thái</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {result.items.map((loan) => (
                <LoanRow
                  key={loan.id}
                  loan={loan}
                  userId={userId}
                  role={role}
                  isStaff={isStaff}
                  busy={busy}
                  open={expanded === loan.id}
                  onToggle={() => setExpanded(expanded === loan.id ? null : loan.id)}
                  act={act}
                />
              ))}
            </tbody>
          </table>
        )}

        {result && <Pagination page={result} onChange={setPage} noun="phiếu" />}
      </Section>
    </>
  );
}

function RequestForm({
  devices,
  onError,
  onCreated,
}: {
  devices: Device[];
  onError: (message: string) => void;
  onCreated: () => void;
}) {
  const today = new Date();
  const latest = new Date();
  latest.setDate(today.getDate() + MAX_LOAN_DAYS);
  const [deviceId, setDeviceId] = useState("");
  const [purpose, setPurpose] = useState("");
  const [dueDate, setDueDate] = useState(toIsoDate(latest));
  const [submitting, setSubmitting] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    createLoan(Number(deviceId), purpose.trim(), dueDate)
      .then(() => {
        setDeviceId("");
        setPurpose("");
        onCreated();
      })
      .catch((err) => onError(err.message))
      .finally(() => setSubmitting(false));
  };

  return (
    <Section
      step="🎒"
      title="Mượn thiết bị"
      subtitle={`Mượn tối đa ${MAX_LOAN_DAYS} ngày và tối đa 2 phiếu chưa trả cùng lúc`}
    >
      <form className="inline-form" onSubmit={submit}>
        <select value={deviceId} onChange={(e) => setDeviceId(e.target.value)} required>
          <option value="">Chọn thiết bị đang sẵn sàng</option>
          {devices.map((device) => (
            <option key={device.id} value={device.id}>
              {device.assetCode} · {device.deviceType}
              {device.model ? ` ${device.model}` : ""}
              {device.roomCode ? ` (${device.roomCode})` : ""}
            </option>
          ))}
        </select>
        <input
          placeholder="Mục đích mượn"
          value={purpose}
          maxLength={500}
          onChange={(e) => setPurpose(e.target.value)}
          required
        />
        <label className="field-label">
          Hạn trả
          <input
            type="date"
            value={dueDate}
            min={toIsoDate(today)}
            max={toIsoDate(latest)}
            onChange={(e) => setDueDate(e.target.value)}
            required
          />
        </label>
        <button className="btn btn-primary" type="submit" disabled={submitting || !deviceId || !purpose.trim()}>
          Gửi yêu cầu mượn
        </button>
      </form>
    </Section>
  );
}

function LoanRow({
  loan,
  userId,
  role,
  isStaff,
  busy,
  open,
  onToggle,
  act,
}: {
  loan: Loan;
  userId: string;
  role: string;
  isStaff: boolean;
  busy: boolean;
  open: boolean;
  onToggle: () => void;
  act: (action: () => Promise<unknown>) => Promise<void>;
}) {
  const [dialog, setDialog] = useState<null | "reject" | "return">(null);
  const [text, setText] = useState("");
  const [condition, setCondition] = useState<ReturnCondition>("GOOD");
  const definition = LOAN_STATUS_LABEL[loan.status] ?? { label: loan.status, tone: "neutral" as const };

  const decide = canDecide(loan, userId, role);
  const own = loan.borrowerId === userId;
  const canCancel = loan.status === "PENDING" && (own || role === "ADMIN");
  const canReturn = loan.status === "APPROVED" && isStaff;

  const close = () => {
    setDialog(null);
    setText("");
    setCondition("GOOD");
  };

  return (
    <>
      <tr>
        <td>
          {loan.deviceAssetCode}
          <div className="cell-sub">{loan.deviceType}</div>
        </td>
        <td>
          {loan.borrowerName}
          <div className="cell-sub">{ROLE_LABEL[loan.borrowerRole] ?? loan.borrowerRole}</div>
        </td>
        <td>{loan.purpose}</td>
        <td>
          {formatDate(loan.dueDate)}
          {loan.overdue && <Badge tone="danger">Quá hạn</Badge>}
        </td>
        <td>
          <Badge tone={definition.tone}>{definition.label}</Badge>
        </td>
        <td>
          {(decide || canCancel || canReturn || loan.rejectionReason || loan.returnedAt) && (
            <button className="btn btn-link" onClick={onToggle}>
              {open ? "Thu gọn" : "Xem & xử lý"}
            </button>
          )}
        </td>
      </tr>
      {open && (
        <tr className="expanded-row">
          <td colSpan={6}>
            <table className="kv-table">
              <tbody>
                <tr>
                  <th>Gửi lúc</th>
                  <td>{formatDateTime(loan.createdAt)}</td>
                </tr>
                {loan.approvedByName && (
                  <tr>
                    <th>Người duyệt</th>
                    <td>
                      {loan.approvedByName}
                      {loan.approvedAt && ` · ${formatDateTime(loan.approvedAt)}`}
                    </td>
                  </tr>
                )}
                {loan.rejectionReason && (
                  <tr>
                    <th>Lý do từ chối</th>
                    <td>{loan.rejectionReason}</td>
                  </tr>
                )}
                {loan.returnedAt && (
                  <tr>
                    <th>Đã trả</th>
                    <td>
                      {formatDateTime(loan.returnedAt)} ·{" "}
                      {loan.returnCondition === "DAMAGED" ? "Hư hỏng" : "Bình thường"}
                      {loan.returnNote && ` · ${loan.returnNote}`}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            <div className="form-actions">
              {decide && (
                <>
                  <button className="btn btn-primary" disabled={busy} onClick={() => act(() => approveLoan(loan.id))}>
                    Duyệt
                  </button>
                  <button className="btn btn-secondary" disabled={busy} onClick={() => setDialog("reject")}>
                    Từ chối
                  </button>
                </>
              )}
              {canCancel && (
                <button className="btn" disabled={busy} onClick={() => act(() => cancelLoan(loan.id))}>
                  Huỷ phiếu
                </button>
              )}
              {canReturn && (
                <button className="btn btn-primary" disabled={busy} onClick={() => setDialog("return")}>
                  Nhận lại thiết bị
                </button>
              )}
            </div>
            {dialog === "reject" && (
              <form
                className="inline-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  void act(() => rejectLoan(loan.id, text.trim())).then(close);
                }}
              >
                <input
                  placeholder="Lý do từ chối (bắt buộc)"
                  value={text}
                  maxLength={1000}
                  onChange={(e) => setText(e.target.value)}
                  required
                />
                <button type="submit" className="btn btn-secondary" disabled={busy}>
                  Xác nhận từ chối
                </button>
                <button type="button" className="btn" onClick={close}>
                  Huỷ
                </button>
              </form>
            )}
            {dialog === "return" && (
              <form
                className="inline-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  void act(() => returnLoan(loan.id, condition, text.trim())).then(close);
                }}
              >
                <select value={condition} onChange={(e) => setCondition(e.target.value as ReturnCondition)}>
                  <option value="GOOD">Bình thường</option>
                  <option value="DAMAGED">Hư hỏng</option>
                </select>
                <input
                  placeholder={condition === "DAMAGED" ? "Mô tả hư hỏng (bắt buộc)" : "Ghi chú (không bắt buộc)"}
                  value={text}
                  maxLength={1000}
                  onChange={(e) => setText(e.target.value)}
                  required={condition === "DAMAGED"}
                />
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  Xác nhận
                </button>
                <button type="button" className="btn" onClick={close}>
                  Huỷ
                </button>
              </form>
            )}
          </td>
        </tr>
      )}
    </>
  );
}
