// SPDX-License-Identifier: Apache-2.0
import { formatDateTime } from "../labels";
import type { ApprovalRequest } from "../types";

interface RequestDetailsProps {
  request: ApprovalRequest;
  labels?: Record<string, string>;
}

function formatValue(value: string | number | boolean | null): string {
  if (value === null || value === "") return "—";
  if (typeof value === "boolean") return value ? "Có" : "Không";
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value)) return formatDateTime(value);
  return String(value);
}

// Lists the summary values of a case; an app passes its own labels for the keys.
export default function RequestDetails({ request, labels = {} }: RequestDetailsProps) {
  const rows = Object.entries(request.details).filter(([key]) => key !== "rejectionReason");
  return (
    <div className="request-details">
      <table className="kv-table">
        <tbody>
          {rows.map(([key, value]) => (
            <tr key={key}>
              <th>{labels[key] ?? key}</th>
              <td>{formatValue(value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {request.status === "REJECTED" && typeof request.details.rejectionReason === "string" && (
        <p className="banner banner-danger">Lý do từ chối: {request.details.rejectionReason}</p>
      )}
    </div>
  );
}
