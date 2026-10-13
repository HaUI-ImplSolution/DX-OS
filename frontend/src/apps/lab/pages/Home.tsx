import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Badge, Section, formatDate, formatDateTime, statusLabel, statusTone } from "../../../shell";
import type { AppUser } from "../../../shell";
import { getLabStats, listBookings, listLoans, listTickets } from "../api";
import { PriorityBadge, toIsoDate } from "../components/common";
import { LOAN_STATUS_LABEL, formatPercent } from "../labels";
import type { Booking, LabStats, Loan, Ticket } from "../types";

interface HomeProps {
  user: AppUser;
  onError: (message: string) => void;
}

const LIST_SIZE = 5;
const STATS_DAYS = 30;

function TicketList({ tickets, empty }: { tickets: Ticket[]; empty: string }) {
  if (tickets.length === 0) return <p className="empty-hint">{empty}</p>;
  return (
    <ul className="plain-list">
      {tickets.map((ticket) => (
        <li key={ticket.id}>
          <Link to={`/tickets/${ticket.id}`}>
            <strong>{ticket.code}</strong> · {ticket.title}
          </Link>{" "}
          <PriorityBadge priority={ticket.priority} />{" "}
          <Badge tone={statusTone(ticket.status)}>{statusLabel(ticket.status)}</Badge>
          {ticket.overdue && <Badge tone="danger">Quá hạn</Badge>}
        </li>
      ))}
    </ul>
  );
}

function Counter({ to, title, value }: { to: string; title: string; value: number | null }) {
  return (
    <Link to={to} className="stat-card">
      <small>{title}</small>
      <strong>{value ?? "…"}</strong>
    </Link>
  );
}

// Landing page: what matters to the signed-in role first.
export default function Home({ user, onError }: HomeProps) {
  const isStaff = user.role === "TECHNICIAN" || user.role === "ADMIN";
  const canBook = user.role !== "STUDENT";

  return (
    <>
      <Section step="👋" title={`Xin chào, ${user.fullName}`} subtitle="Chọn việc bạn cần làm">
        <div className="form-actions">
          <Link className="btn btn-primary" to="/tickets">
            Báo sự cố
          </Link>
          <Link className="btn" to="/device-loans">
            Mượn thiết bị
          </Link>
          {canBook && (
            <Link className="btn" to="/room-bookings">
              Đặt phòng
            </Link>
          )}
          <Link className="btn" to="/knowledge">
            Quy chế và hướng dẫn
          </Link>
        </div>
      </Section>
      {isStaff ? (
        <StaffHome user={user} onError={onError} />
      ) : (
        <MemberHome user={user} onError={onError} />
      )}
    </>
  );
}

function MemberHome({ user, onError }: HomeProps) {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [toApprove, setToApprove] = useState<Loan[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const isLecturer = user.role === "LECTURER";

  useEffect(() => {
    listTickets({ size: LIST_SIZE })
      .then((page) => setTickets(page.items))
      .catch((err) => onError(err.message));
    listLoans({ mine: true, size: LIST_SIZE })
      .then((page) => setLoans(page.items))
      .catch((err) => onError(err.message));
    if (isLecturer) {
      listLoans({ status: "PENDING", size: 20 })
        .then((page) =>
          setToApprove(
            page.items.filter((loan) => loan.borrowerRole === "STUDENT" && loan.borrowerId !== user.id),
          ),
        )
        .catch((err) => onError(err.message));
      listBookings({ size: LIST_SIZE })
        .then((page) => setBookings(page.items))
        .catch((err) => onError(err.message));
    }
  }, [isLecturer, user.id, onError]);

  return (
    <>
      <Section step="🎫" title="Yêu cầu hỗ trợ của tôi" subtitle="Các yêu cầu gần đây nhất">
        <TicketList tickets={tickets} empty="Bạn chưa gửi yêu cầu nào." />
        <Link className="btn btn-link" to="/tickets">
          Xem tất cả →
        </Link>
      </Section>
      <Section step="📦" title="Thiết bị tôi mượn">
        {loans.length === 0 ? (
          <p className="empty-hint">Bạn chưa mượn thiết bị nào.</p>
        ) : (
          <ul className="plain-list">
            {loans.map((loan) => {
              const definition = LOAN_STATUS_LABEL[loan.status] ?? { label: loan.status, tone: "neutral" as const };
              return (
                <li key={loan.id}>
                  {loan.deviceAssetCode} · {loan.deviceType} · hạn trả {formatDate(loan.dueDate)}{" "}
                  <Badge tone={definition.tone}>{definition.label}</Badge>
                  {loan.overdue && <Badge tone="danger">Quá hạn</Badge>}
                </li>
              );
            })}
          </ul>
        )}
        <Link className="btn btn-link" to="/device-loans">
          Mượn hoặc trả thiết bị →
        </Link>
      </Section>
      {isLecturer && (
        <>
          <Section step="✅" title="Phiếu mượn chờ bạn duyệt" subtitle="Phiếu mượn của sinh viên">
            {toApprove.length === 0 ? (
              <p className="empty-hint">Không có phiếu nào đang chờ.</p>
            ) : (
              <ul className="plain-list">
                {toApprove.map((loan) => (
                  <li key={loan.id}>
                    {loan.borrowerName} · {loan.deviceAssetCode} · hạn trả {formatDate(loan.dueDate)}
                  </li>
                ))}
              </ul>
            )}
            <Link className="btn btn-link" to="/device-loans">
              Duyệt phiếu mượn →
            </Link>
          </Section>
          <Section step="🗓️" title="Lịch đặt phòng của tôi">
            {bookings.length === 0 ? (
              <p className="empty-hint">Bạn chưa đặt phòng nào.</p>
            ) : (
              <ul className="plain-list">
                {bookings.map((booking) => (
                  <li key={booking.id}>
                    {booking.roomCode} · {booking.title} · {formatDateTime(booking.startsAt)}
                  </li>
                ))}
              </ul>
            )}
            <Link className="btn btn-link" to="/room-bookings">
              Đặt phòng →
            </Link>
          </Section>
        </>
      )}
    </>
  );
}

function StaffHome({ user, onError }: HomeProps) {
  const isAdmin = user.role === "ADMIN";
  const [unassigned, setUnassigned] = useState<number | null>(null);
  const [mine, setMine] = useState<number | null>(null);
  const [overdue, setOverdue] = useState<number | null>(null);
  const [overdueTickets, setOverdueTickets] = useState<Ticket[]>([]);
  const [pendingBookings, setPendingBookings] = useState<number | null>(null);
  const [pendingLoans, setPendingLoans] = useState<number | null>(null);
  const [stats, setStats] = useState<LabStats | null>(null);

  useEffect(() => {
    const fail = (err: Error) => onError(err.message);
    listTickets({ unassigned: true, status: "NEW", size: 1 })
      .then((page) => setUnassigned(page.totalElements))
      .catch(fail);
    listTickets({ assignedTo: user.id, size: 1 })
      .then((page) => setMine(page.totalElements))
      .catch(fail);
    listTickets({ overdue: true, size: LIST_SIZE })
      .then((page) => {
        setOverdue(page.totalElements);
        setOverdueTickets(page.items);
      })
      .catch(fail);
    listBookings({ status: "PENDING", size: 1 })
      .then((page) => setPendingBookings(page.totalElements))
      .catch(fail);
    listLoans({ status: "PENDING", size: 1 })
      .then((page) => setPendingLoans(page.totalElements))
      .catch(fail);
    if (isAdmin) {
      const to = new Date();
      const from = new Date();
      from.setDate(to.getDate() - STATS_DAYS);
      getLabStats(toIsoDate(from), toIsoDate(to))
        .then(setStats)
        .catch(fail);
    }
  }, [isAdmin, user.id, onError]);

  return (
    <>
      <Section step="👷" title="Việc cần xử lý">
        <div className="stat-grid">
          <Counter to="/queue" title="Yêu cầu chưa có người nhận" value={unassigned} />
          <Counter to="/queue" title="Đang giao cho tôi" value={mine} />
          <Counter to="/queue" title="Yêu cầu quá hạn" value={overdue} />
          <Counter to="/room-bookings" title="Lịch đặt phòng chờ duyệt" value={pendingBookings} />
          <Counter to="/device-loans" title="Phiếu mượn chờ duyệt" value={pendingLoans} />
        </div>
      </Section>
      <Section step="⏰" title="Yêu cầu quá hạn xử lý">
        <TicketList tickets={overdueTickets} empty="Không có yêu cầu nào quá hạn." />
      </Section>
      {isAdmin && stats && (
        <Section step="📈" title={`Tổng quan ${STATS_DAYS} ngày qua`}>
          <div className="stat-grid">
            <div className="stat-card">
              <small>Yêu cầu đã tạo</small>
              <strong>{stats.ticketsCreated}</strong>
            </div>
            <div className="stat-card">
              <small>Đúng hạn SLA</small>
              <strong>{formatPercent(stats.slaAttainment)}</strong>
            </div>
            <div className="stat-card">
              <small>Mức hài lòng</small>
              <strong>{stats.averageRating === null ? "—" : `${stats.averageRating.toFixed(1)} / 5`}</strong>
            </div>
            <div className="stat-card">
              <small>Phiếu mượn quá hạn</small>
              <strong>{stats.overdueLoans}</strong>
            </div>
          </div>
          <Link className="btn btn-link" to="/stats">
            Xem thống kê chi tiết →
          </Link>
        </Section>
      )}
    </>
  );
}
