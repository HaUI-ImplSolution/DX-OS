// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Badge, Section, formatDate, formatDateTime } from "../../../shell";
import { getParcelWeather } from "../api";
import type { Weather } from "../types";

// WMO weather interpretation codes grouped by how a farmer reads them.
function describe(code: number): string {
  if (code === 0) return "Trời quang";
  if (code <= 3) return "Có mây";
  if (code === 45 || code === 48) return "Sương mù";
  if (code >= 51 && code <= 57) return "Mưa phùn";
  if (code >= 61 && code <= 67) return "Mưa";
  if (code >= 80 && code <= 82) return "Mưa rào";
  if (code >= 95) return "Dông";
  return "Nhiều mây";
}

interface WeatherPanelProps {
  parcelCode: string;
}

// Current weather and the five day forecast at the position of the selected parcel.
export default function WeatherPanel({ parcelCode }: WeatherPanelProps) {
  const [weather, setWeather] = useState<Weather | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    setWeather(null);
    setMessage(null);
    let current = true;
    getParcelWeather(parcelCode)
      .then((data) => current && setWeather(data))
      .catch((err) => current && setMessage(err.message));
    return () => {
      current = false;
    };
  }, [parcelCode]);

  return (
    <Section step="🌤" title="Thời tiết" subtitle={`Thửa ${parcelCode}: hiện tại và dự báo 5 ngày`}>
      {message && <p className="empty-hint">{message}</p>}
      {!message && !weather && <p className="empty-hint">Đang tải…</p>}

      {weather && (
        <>
          {weather.isSnapshot && (
            <div className="banner banner-warning">
              {weather.note ?? "Dữ liệu tham khảo đã lưu, không phải thời gian thực."}
            </div>
          )}

          <div className="stat-grid">
            <div className="stat-card">
              <small>{describe(weather.current.weatherCode)}</small>
              <strong>{weather.current.temperatureC.toFixed(1)} °C</strong>
            </div>
            <div className="stat-card">
              <small>Độ ẩm</small>
              <strong>{Math.round(weather.current.humidityPercent)}%</strong>
            </div>
            <div className="stat-card">
              <small>Lượng mưa</small>
              <strong>{weather.current.precipitationMm.toFixed(1)} mm</strong>
            </div>
            <div className="stat-card">
              <small>Gió</small>
              <strong>{Math.round(weather.current.windSpeedKmh)} km/h</strong>
            </div>
          </div>

          <table className="data-table">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Nhiệt độ</th>
                <th>Lượng mưa</th>
                <th>Khả năng mưa</th>
              </tr>
            </thead>
            <tbody>
              {weather.daily.map((day) => (
                <tr key={day.date}>
                  <td>{formatDate(day.date)}</td>
                  <td>
                    {Math.round(day.tempMinC)}–{Math.round(day.tempMaxC)} °C
                  </td>
                  <td>{day.precipitationMm.toFixed(1)} mm</td>
                  <td>
                    {day.precipitationProbabilityPercent >= 60 ? (
                      <Badge tone="warning">{day.precipitationProbabilityPercent}%</Badge>
                    ) : (
                      `${day.precipitationProbabilityPercent}%`
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <p className="section-subtitle">
            Nguồn: {weather.source}, {weather.isSnapshot ? "chụp" : "cập nhật"}{" "}
            {formatDateTime(weather.fetchedAt)}
          </p>
        </>
      )}
    </Section>
  );
}
