import { useCallback, useEffect, useState } from "react";
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "../api";
import Badge from "../components/Badge";
import Pagination from "../components/Pagination";
import Section from "../components/Section";
import { formatDateTime } from "../labels";
import type { Notification, Page } from "../types";

interface NotificationsProps {
  onError: (message: string) => void;
  // Called after a notification is read so the unread counter in the header refreshes.
  onChanged: () => void;
}

const PAGE_SIZE = 15;

// The signed-in account's own notifications, newest first.
export default function Notifications({ onError, onChanged }: NotificationsProps) {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Notification> | null>(null);

  const load = useCallback(() => {
    listNotifications(unreadOnly, page, PAGE_SIZE)
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [unreadOnly, page, onError]);

  useEffect(load, [load]);

  const read = (notification: Notification) => {
    if (notification.readAt) return;
    markNotificationRead(notification.id)
      .then(() => {
        load();
        onChanged();
      })
      .catch((err) => onError(err.message));
  };

  const readAll = () => {
    markAllNotificationsRead()
      .then(() => {
        load();
        onChanged();
      })
      .catch((err) => onError(err.message));
  };

  return (
    <Section step="🔔" title="Thông báo" subtitle="Các thông báo gửi riêng cho tài khoản của bạn">
      <div className="filter-bar">
        <label className="field-label check-label">
          <input
            type="checkbox"
            checked={unreadOnly}
            onChange={(e) => {
              setUnreadOnly(e.target.checked);
              setPage(0);
            }}
          />{" "}
          Chỉ hiện thông báo chưa đọc
        </label>
        <button className="btn" onClick={readAll}>
          Đánh dấu đã đọc tất cả
        </button>
      </div>

      {result && result.items.length === 0 && <p className="empty-hint">Chưa có thông báo nào.</p>}

      {result && result.items.length > 0 && (
        <ul className="notification-list">
          {result.items.map((notification) => (
            <li
              key={notification.id}
              className={notification.readAt ? "notification" : "notification notification-unread"}
              onClick={() => read(notification)}
            >
              <div>
                <strong>{notification.title}</strong>{" "}
                {!notification.readAt && <Badge tone="warning">Mới</Badge>}
              </div>
              {notification.body && <p>{notification.body}</p>}
              <small className="timeline-time">{formatDateTime(notification.createdAt)}</small>
            </li>
          ))}
        </ul>
      )}

      {result && <Pagination page={result} onChange={setPage} noun="thông báo" />}
    </Section>
  );
}
