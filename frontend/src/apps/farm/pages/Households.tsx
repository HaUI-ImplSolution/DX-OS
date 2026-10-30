// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { createHousehold, linkHouseholdAccount, listHouseholds } from "../api";
import { formatDateTime } from "../labels";
import type { Household, HouseholdCreateResult } from "../types";
import { Badge, Section } from "../../../shell";

interface HouseholdsProps {
  onError: (message: string) => void;
}

// ADMIN registers a household together with the account its farmer signs in with.
export default function Households({ onError }: HouseholdsProps) {
  const [households, setHouseholds] = useState<Household[]>([]);
  const [created, setCreated] = useState<HouseholdCreateResult | null>(null);
  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    listHouseholds()
      .then(setHouseholds)
      .catch((err) => onError(err.message));
  }, [onError]);

  useEffect(load, [load]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      setCreated(
        await createHousehold({
          fullName: fullName.trim(),
          username: username.trim(),
          phone: phone.trim() || undefined,
          address: address.trim() || undefined,
        }),
      );
      setFullName("");
      setUsername("");
      setPhone("");
      setAddress("");
      load();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không tạo được nông hộ");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Section
        step="🏠"
        title="Thêm nông hộ"
        subtitle="Tạo hộ cùng tài khoản đăng nhập cho nông dân trên ứng dụng di động"
      >
        <form className="inline-form" onSubmit={submit}>
          <input
            placeholder="Họ tên chủ hộ (ít nhất hai từ)"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            pattern="\S+(\s+\S+)+"
            required
          />
          <input
            placeholder="Tên đăng nhập (chữ thường, số, . _ -)"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            pattern="[a-z0-9._\-]{3,50}"
            required
          />
          <input placeholder="Số điện thoại (không bắt buộc)" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <input placeholder="Địa chỉ (không bắt buộc)" value={address} onChange={(e) => setAddress(e.target.value)} />
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Đang tạo…" : "Tạo nông hộ"}
            </button>
          </div>
        </form>

        {created && (
          <div className="banner banner-success">
            <strong>
              Đã tạo hộ {created.household.code} — {created.household.fullName}
            </strong>
            <p>
              Tên đăng nhập: <b>{created.username}</b> · Mật khẩu tạm: <b>{created.temporaryPassword}</b>
            </p>
            <p>Mật khẩu chỉ hiện một lần, hãy ghi lại. Nông dân phải đổi mật khẩu ở lần đăng nhập đầu.</p>
          </div>
        )}
      </Section>

      <Section step="📒" title="Danh sách nông hộ" subtitle={`${households.length} hộ`}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Mã hộ</th>
              <th>Chủ hộ</th>
              <th>Điện thoại</th>
              <th>Địa chỉ</th>
              <th>Tài khoản</th>
              <th>Tạo lúc</th>
            </tr>
          </thead>
          <tbody>
            {households.map((household) => (
              <tr key={household.id}>
                <td>{household.code}</td>
                <td>{household.fullName}</td>
                <td>{household.phone ?? "—"}</td>
                <td>{household.address ?? "—"}</td>
                <td>
                  {household.accountLinked ? (
                    <Badge tone="success">Đã gắn</Badge>
                  ) : (
                    <LinkAccount code={household.code} onLinked={load} onError={onError} />
                  )}
                </td>
                <td>{formatDateTime(household.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Section>
    </>
  );
}

// An existing Keycloak account (for example one made in the Keycloak console) joins a household.
function LinkAccount({
  code,
  onLinked,
  onError,
}: {
  code: string;
  onLinked: () => void;
  onError: (message: string) => void;
}) {
  const [username, setUsername] = useState("");
  const [busy, setBusy] = useState(false);

  const link = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await linkHouseholdAccount(code, username.trim());
      onLinked();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không gắn được tài khoản");
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="link-form" onSubmit={link}>
      <Badge tone="warning">Chưa gắn</Badge>
      <input
        placeholder="Tên đăng nhập có sẵn"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        required
      />
      <button className="btn" disabled={busy}>
        Gắn
      </button>
    </form>
  );
}
