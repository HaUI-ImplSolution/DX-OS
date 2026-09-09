import { useEffect, useState } from "react";
import { listDiseases, listPesticides, listTreatmentRules } from "../api";
import type { Disease, Pesticide, TreatmentRule } from "../types";

interface ReferencePanelProps {
  onError: (message: string) => void;
}

export default function ReferencePanel({ onError }: ReferencePanelProps) {
  const [diseases, setDiseases] = useState<Disease[]>([]);
  const [pesticides, setPesticides] = useState<Pesticide[]>([]);
  const [rules, setRules] = useState<TreatmentRule[]>([]);

  useEffect(() => {
    Promise.all([listDiseases(), listPesticides(), listTreatmentRules()])
      .then(([d, p, r]) => {
        setDiseases(d);
        setPesticides(p);
        setRules(r);
      })
      .catch((err) => onError(err instanceof Error ? err.message : "Không tải được danh mục"));
  }, [onError]);

  return (
    <div className="reference-grid">
      <div className="reference-card">
        <h3>Bệnh (Disease)</h3>
        <table>
          <thead>
            <tr>
              <th>Mã</th>
              <th>Tên</th>
              <th>Mô tả</th>
            </tr>
          </thead>
          <tbody>
            {diseases.map((d) => (
              <tr key={d.code}>
                <td>{d.code}</td>
                <td>{d.name}</td>
                <td>{d.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="reference-card">
        <h3>Thuốc BVTV (Pesticide)</h3>
        <table>
          <thead>
            <tr>
              <th>Mã</th>
              <th>Tên</th>
              <th>Đơn vị</th>
            </tr>
          </thead>
          <tbody>
            {pesticides.map((p) => (
              <tr key={p.code}>
                <td>{p.code}</td>
                <td>{p.name}</td>
                <td>{p.unit}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="reference-card reference-card-wide">
        <h3>Phác đồ xử lý (TreatmentRule)</h3>
        <table>
          <thead>
            <tr>
              <th>Bệnh</th>
              <th>Thuốc</th>
              <th>Liều lượng</th>
              <th>PHI (ngày)</th>
              <th>Nguồn</th>
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => (
              <tr key={r.id}>
                <td>{r.diseaseCode}</td>
                <td>{r.pesticideCode}</td>
                <td>
                  {r.doseAmount} {r.doseUnit}
                </td>
                <td>{r.phiDays}</td>
                <td>
                  {r.source} {r.version}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
