import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { AppState } from "react-native";
import { getUnreadCount } from "@/api/endpoints";
import { useAuth } from "@/auth/AuthProvider";

interface NotificationsContextValue {
  unread: number;
  refreshUnread: () => void;
}

const NotificationsContext = createContext<NotificationsContextValue | null>(null);

// Keeps the unread count of the inbox. The server is asked when the app opens and each time it
// returns to the foreground; there is no push.
export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const [unread, setUnread] = useState(0);

  const refreshUnread = useCallback(() => {
    if (status !== "signedIn") {
      setUnread(0);
      return;
    }
    getUnreadCount()
      .then((result) => setUnread(result.unread))
      .catch(() => {
        // Offline or expired session: keep the last known count.
      });
  }, [status]);

  useEffect(() => {
    refreshUnread();
    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") refreshUnread();
    });
    return () => subscription.remove();
  }, [refreshUnread]);

  const value = useMemo(() => ({ unread, refreshUnread }), [unread, refreshUnread]);
  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}

export function useNotifications(): NotificationsContextValue {
  const value = useContext(NotificationsContext);
  if (!value) throw new Error("useNotifications must be used inside NotificationsProvider");
  return value;
}
