import { DETAIL_LABEL, formatValue } from "../labels";
import type { ApprovalRequest } from "../types";

// Edit requests carry current.* and proposed.* pairs; every other request lists its values.
export default function RequestDetails({ request }: { request: ApprovalRequest }) {
  const { details } = request;
  const isEdit = request.caseType === "PARCEL_EDIT_REQUEST";
  const imageUrl = typeof details.imageUrl === "string" ? details.imageUrl : null;

  const rows = isEdit
    ? ["areaHectares", "location", "latitude", "longitude", "gpsAccuracyMeters"].map((key) => ({
        key,
        current: details[`current.${key}`] ?? null,
        proposed: details[`proposed.${key}`] ?? null,
      }))
    : Object.entries(details)
        .filter(([key]) => key !== "imageUrl" && key !== "rejectionReason")
        .map(([key, value]) => ({ key, current: value, proposed: undefined }));

  return (
    <div className="request-details">
      {imageUrl && <img className="request-photo" src={imageUrl} alt="Ảnh báo cáo" />}
      <table className="kv-table">
        {isEdit && (
          <thead>
            <tr>
              <th />
              <th>Hiện tại</th>
              <th>Đề xuất</th>
            </tr>
          </thead>
        )}
        <tbody>
          {rows.map((row) => (
            <tr key={row.key}>
              <th>{DETAIL_LABEL[row.key] ?? row.key}</th>
              <td>{formatValue(row.key, row.current)}</td>
              {row.proposed !== undefined && <td>{formatValue(row.key, row.proposed)}</td>}
            </tr>
          ))}
          {isEdit && (
            <tr>
              <th>{DETAIL_LABEL.reason}</th>
              <td colSpan={2}>{formatValue("reason", details.reason ?? null)}</td>
            </tr>
          )}
        </tbody>
      </table>
      {request.status === "REJECTED" && typeof details.rejectionReason === "string" && (
        <p className="banner banner-danger">Lý do từ chối: {details.rejectionReason}</p>
      )}
    </div>
  );
}
