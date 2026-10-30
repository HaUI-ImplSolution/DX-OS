// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Section, statusLabel } from "../../../shell";
import { getLabStats, listCategories } from "../api";
import { Stars } from "../components/common";
import { toIsoDate } from "../components/common";
import { DEVICE_STATUS_LABEL, formatMinutes, formatPercent } from "../labels";
import type { CountEntry, DeviceStatus, LabStats } from "../types";

interface StatsProps {
  onError: (message: string) => void;
}

const DEFAULT_RANGE_DAYS = 30;

function Bars({ entries, label }: { entries: CountEntry[]; label: (key: string) => string }) {
  const max = Math.max(1, ...entries.map((entry) => entry.count));
  if (entries.length === 0) return <p className="empty-hint">Chưa có dữ liệu.</p>;
  return (
    <div className="bars">
      {entries.map((entry) => (
        <div className="bar-row" key={entry.key}>
          <span className="bar-label">{label(entry.key)}</span>
          <span className="bar-track">
            <span className="bar-fill" style={{ width: `${(entry.count / max) * 100}%` }} />
          </span>
          <span className="bar-value">{entry.count}</span>
        </div>
      ))}
    </div>
  );
}

function StatCard({ title, value, hint }: { title: string; value: string | number; hint?: string }) {
  return (
    <div className="stat-card">
      <small>{title}</small>
      <strong>{value}</strong>
      {hint && <small>{hint}</small>}
    </div>
  );
}

export default function Stats({ onError }: StatsProps) {
  const today = new Date();
  const start = new Date();
  start.setDate(today.getDate() - (DEFAULT_RANGE_DAYS - 1));
  const [from, setFrom] = useState(toIsoDate(start));
  const [to, setTo] = useState(toIsoDate(today));
  const [stats, setStats] = useState<LabStats | null>(null);
  const [categoryNames, setCategoryNames] = useState<Record<string, string>>({});

  useEffect(() => {
    getLabStats(from, to)
      .then(setStats)
      .catch((err) => onError(err.message));
  }, [from, to, onError]);

  // Inactive categories are listed for administrators only; other users see their code.
  useEffect(() => {
    listCategories(true)
      .then((categories) => setCategoryNames(Object.fromEntries(categories.map((c) => [c.code, c.name]))))
      .catch((err) => onError(err.message));
  }, [onError]);

  return (
    <>
      <Section step="📈" title="Thống kê vận hành" subtitle="Số liệu của yêu cầu hỗ trợ, kỹ thuật viên, phòng và thiết bị">
        <div className="filter-bar">
          <label className="field-label">
            Từ ngày
            <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field-label">
            Đến ngày
            <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </label>
        </div>

        {stats && (
          <div className="stat-grid">
            <StatCard title="Yêu cầu đã tạo" value={stats.ticketsCreated} />
            <StatCard title="Đúng hạn SLA" value={formatPercent(stats.slaAttainment)} />
            <StatCard
              title="Phản hồi đầu tiên (trung bình)"
              value={formatMinutes(stats.averageFirstResponseMinutes)}
            />
            <StatCard title="Thời gian xử lý (trung bình)" value={formatMinutes(stats.averageResolutionMinutes)} />
            <StatCard title="Yêu cầu quá hạn" value={stats.overdueTickets} />
            <StatCard
              title="Mức hài lòng"
              value={stats.averageRating === null ? "—" : `${stats.averageRating.toFixed(1)} / 5`}
              hint={`${stats.ratedTickets} lượt đánh giá`}
            />
            <StatCard title="Phiếu mượn quá hạn" value={stats.overdueLoans} />
          </div>
        )}
      </Section>

      {stats && (
        <>
          <Section step="🎫" title="Yêu cầu theo trạng thái và loại">
            <div className="two-columns">
              <div>
                <h3>Theo trạng thái</h3>
                <Bars entries={stats.ticketsByStatus} label={statusLabel} />
              </div>
              <div>
                <h3>Theo loại sự cố</h3>
                <Bars entries={stats.ticketsByCategory} label={(key) => categoryNames[key] ?? key} />
              </div>
            </div>
          </Section>

          <Section step="👷" title="Tải của kỹ thuật viên">
            {stats.technicians.length === 0 ? (
              <p className="empty-hint">Chưa có dữ liệu.</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Kỹ thuật viên</th>
                    <th>Đang xử lý</th>
                    <th>Đã hoàn tất</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.technicians.map((technician) => (
                    <tr key={technician.userId}>
                      <td>{technician.name}</td>
                      <td>{technician.openTickets}</td>
                      <td>{technician.closedTickets}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Section>

          <Section step="🏫" title="Mức sử dụng phòng" subtitle="Giờ đã đặt và tỷ lệ so với giờ sử dụng trong kỳ">
            <Bars
              entries={stats.roomUsage.map((usage) => ({
                key: `${usage.roomCode} · ${usage.bookedHours.toFixed(1)} giờ · ${formatPercent(usage.utilization)}`,
                count: Math.round(usage.utilization * 100),
              }))}
              label={(key) => key}
            />
          </Section>

          <Section step="🖥️" title="Thiết bị theo trạng thái">
            <Bars
              entries={Object.entries(stats.devicesByStatus).map(([key, count]) => ({ key, count }))}
              label={(key) => DEVICE_STATUS_LABEL[key as DeviceStatus]?.label ?? key}
            />
          </Section>

          {stats.ratedTickets > 0 && stats.averageRating !== null && (
            <p>
              Đánh giá trung bình: <Stars value={Math.round(stats.averageRating)} />
            </p>
          )}
        </>
      )}
    </>
  );
}
