// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { createWorkspace, listWorkspaces } from "../api";
import Badge from "../components/Badge";
import Section from "../components/Section";
import { formatDateTime } from "../labels";
import type { Workspace, WorkspaceCreateResult } from "../types";

interface WorkspacesProps {
  onError: (message: string) => void;
  // Workspace types of the running app, with the label shown for each.
  types: { type: string; label: string }[];
  canCreate: boolean;
  title?: string;
  subtitle?: string;
}

// Workspaces of the running app (classes, departments...): list and, for an administrator, create.
export default function Workspaces({ onError, types, canCreate, title, subtitle }: WorkspacesProps) {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [created, setCreated] = useState<WorkspaceCreateResult | null>(null);
  const [type, setType] = useState(types[0]?.type ?? "");
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    listWorkspaces()
      .then((all) => setWorkspaces(all.filter((w) => types.some((t) => t.type === w.type))))
      .catch((err) => onError(err.message));
  }, [onError, types]);

  useEffect(load, [load]);

  const typeLabel = (value: string) => types.find((t) => t.type === value)?.label ?? value;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    createWorkspace({ type, name: name.trim() })
      .then((result) => {
        setCreated(result);
        setName("");
        load();
      })
      .catch((err) => onError(err.message))
      .finally(() => setSubmitting(false));
  };

  return (
    <Section
      step="🏫"
      title={title ?? "Đơn vị"}
      subtitle={subtitle ?? "Các đơn vị người dùng thuộc về trong ứng dụng"}
    >
      {canCreate && (
        <form className="inline-form" onSubmit={submit}>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            {types.map((t) => (
              <option key={t.type} value={t.type}>
                {t.label}
              </option>
            ))}
          </select>
          <input
            placeholder="Tên đơn vị"
            value={name}
            maxLength={255}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <button className="btn btn-primary" type="submit" disabled={submitting || !name.trim()}>
            Thêm
          </button>
        </form>
      )}
      {created && (
        <p className="banner banner-success">
          Đã tạo {typeLabel(created.workspace.type).toLowerCase()} {created.workspace.name} (
          {created.workspace.code}).
        </p>
      )}

      {workspaces.length === 0 ? (
        <p className="empty-hint">Chưa có đơn vị nào.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Tên</th>
              <th>Loại</th>
              <th>Tài khoản chủ</th>
              <th>Tạo lúc</th>
            </tr>
          </thead>
          <tbody>
            {workspaces.map((workspace) => (
              <tr key={workspace.id}>
                <td>{workspace.code}</td>
                <td>{workspace.name}</td>
                <td>{typeLabel(workspace.type)}</td>
                <td>
                  <Badge tone={workspace.accountLinked ? "success" : "neutral"}>
                    {workspace.accountLinked ? "Đã liên kết" : "Chưa có"}
                  </Badge>
                </td>
                <td>{formatDateTime(workspace.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Section>
  );
}
