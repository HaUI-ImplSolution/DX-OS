import { useState } from "react";

interface DeleteButtonProps {
  label: string;
  // Asked before anything is deleted.
  confirmMessage: string;
  onDelete: () => Promise<void>;
  onError: (message: string) => void;
}

export default function DeleteButton({ label, confirmMessage, onDelete, onError }: DeleteButtonProps) {
  const [busy, setBusy] = useState(false);

  const run = async () => {
    if (!window.confirm(confirmMessage)) return;
    setBusy(true);
    try {
      await onDelete();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không xóa được");
    } finally {
      setBusy(false);
    }
  };

  return (
    <button className="btn btn-secondary" disabled={busy} onClick={() => void run()}>
      {busy ? "Đang xóa…" : label}
    </button>
  );
}
