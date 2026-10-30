// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Badge, Section, Workspaces } from "../../../shell";
import {
  createCategory,
  listCategories,
  listSlaPolicies,
  saveSlaPolicy,
  updateCategory,
} from "../api";
import { runAction } from "../components/common";
import { PRIORITIES, PRIORITY_LABEL } from "../labels";
import type { SlaPolicy, TicketCategory, TicketPriority } from "../types";

interface CatalogProps {
  workspaceTypes: string[];
  onError: (message: string) => void;
}

const WORKSPACE_TYPE_LABEL: Record<string, string> = {
  CLASS: "Lớp",
  DEPARTMENT: "Bộ môn",
};

// Administrator's catalogues: incident categories, SLA per priority, and classes and departments.
export default function Catalog({ workspaceTypes, onError }: CatalogProps) {
  const types = useMemo(
    () => workspaceTypes.map((type) => ({ type, label: WORKSPACE_TYPE_LABEL[type] ?? type })),
    [workspaceTypes],
  );
  return (
    <>
      <Categories onError={onError} />
      <SlaPolicies onError={onError} />
      <Workspaces
        onError={onError}
        canCreate
        types={types}
        title="Lớp và bộ môn"
        subtitle="Đơn vị người dùng thuộc về"
      />
    </>
  );
}

function Categories({ onError }: { onError: (message: string) => void }) {
  const [categories, setCategories] = useState<TicketCategory[]>([]);
  const [editing, setEditing] = useState<TicketCategory | "new" | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    listCategories(true)
      .then(setCategories)
      .catch((err) => onError(err.message));
  }, [onError]);

  useEffect(load, [load]);

  return (
    <Section
      step="🗂️"
      title="Loại sự cố"
      subtitle="Từ khóa dùng để gợi ý loại và mức ưu tiên khi người dùng báo sự cố"
    >
      <div className="form-actions">
        <button className="btn btn-primary" onClick={() => setEditing("new")}>
          Thêm loại sự cố
        </button>
      </div>
      {editing && (
        <CategoryForm
          category={editing === "new" ? null : editing}
          onError={onError}
          onCancel={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
          }}
        />
      )}
      <table className="data-table">
        <thead>
          <tr>
            <th>Mã</th>
            <th>Tên</th>
            <th>Ưu tiên mặc định</th>
            <th>Từ khóa</th>
            <th>Trạng thái</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {categories.map((category) => (
            <tr key={category.id}>
              <td>{category.code}</td>
              <td>{category.name}</td>
              <td>{PRIORITY_LABEL[category.defaultPriority]}</td>
              <td>{category.keywords ?? "—"}</td>
              <td>
                <Badge tone={category.active ? "success" : "neutral"}>
                  {category.active ? "Đang dùng" : "Đã tắt"}
                </Badge>
              </td>
              <td>
                <div className="form-actions">
                  <button className="btn" disabled={busy} onClick={() => setEditing(category)}>
                    Sửa
                  </button>
                  <button
                    className="btn"
                    disabled={busy}
                    onClick={() =>
                      runAction(
                        () =>
                          updateCategory(category.id, {
                            code: category.code,
                            name: category.name,
                            defaultPriority: category.defaultPriority,
                            keywords: category.keywords ?? undefined,
                            active: !category.active,
                          }),
                        setBusy,
                        onError,
                        load,
                      )
                    }
                  >
                    {category.active ? "Tắt" : "Bật"}
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Section>
  );
}

function CategoryForm({
  category,
  onError,
  onCancel,
  onSaved,
}: {
  category: TicketCategory | null;
  onError: (message: string) => void;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [code, setCode] = useState(category?.code ?? "");
  const [name, setName] = useState(category?.name ?? "");
  const [priority, setPriority] = useState<TicketPriority>(category?.defaultPriority ?? "NORMAL");
  const [keywords, setKeywords] = useState(category?.keywords ?? "");
  const [saving, setSaving] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    const payload = {
      code: code.trim().toUpperCase(),
      name: name.trim(),
      defaultPriority: priority,
      keywords: keywords.trim() || undefined,
      active: category?.active ?? true,
    };
    (category ? updateCategory(category.id, payload) : createCategory(payload))
      .then(onSaved)
      .catch((err) => onError(err.message))
      .finally(() => setSaving(false));
  };

  return (
    <form className="harvest-box" onSubmit={submit}>
      <div className="filter-bar">
        <input
          placeholder="Mã (ví dụ NETWORK)"
          value={code}
          maxLength={48}
          onChange={(e) => setCode(e.target.value)}
          required
        />
        <input
          placeholder="Tên loại sự cố"
          value={name}
          maxLength={255}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <select value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {PRIORITY_LABEL[p]}
            </option>
          ))}
        </select>
        <input
          placeholder="Từ khóa, cách nhau bằng dấu phẩy"
          value={keywords}
          maxLength={1000}
          onChange={(e) => setKeywords(e.target.value)}
        />
      </div>
      <div className="form-actions">
        <button className="btn btn-primary" type="submit" disabled={saving}>
          Lưu
        </button>
        <button className="btn" type="button" onClick={onCancel}>
          Huỷ
        </button>
      </div>
    </form>
  );
}

function SlaPolicies({ onError }: { onError: (message: string) => void }) {
  const [policies, setPolicies] = useState<SlaPolicy[]>([]);
  const [drafts, setDrafts] = useState<Record<string, { response: number; resolution: number }>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    listSlaPolicies()
      .then((list) => {
        setPolicies(list);
        setDrafts(
          Object.fromEntries(
            list.map((p) => [p.priority, { response: p.responseMinutes, resolution: p.resolutionMinutes }]),
          ),
        );
      })
      .catch((err) => onError(err.message));
  }, [onError]);

  useEffect(load, [load]);

  const ordered = PRIORITIES.map((priority) => policies.find((p) => p.priority === priority)).filter(
    (p): p is SlaPolicy => p !== undefined,
  );

  return (
    <Section
      step="⏱️"
      title="Chính sách SLA"
      subtitle="Thời hạn phản hồi và xử lý theo mức ưu tiên, tính bằng phút"
    >
      <table className="data-table">
        <thead>
          <tr>
            <th>Mức ưu tiên</th>
            <th>Phản hồi (phút)</th>
            <th>Xử lý (phút)</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {ordered.map((policy) => {
            const draft = drafts[policy.priority];
            if (!draft) return null;
            const changed =
              draft.response !== policy.responseMinutes || draft.resolution !== policy.resolutionMinutes;
            return (
              <tr key={policy.priority}>
                <td>{PRIORITY_LABEL[policy.priority]}</td>
                <td>
                  <input
                    type="number"
                    min={1}
                    value={draft.response}
                    onChange={(e) =>
                      setDrafts({ ...drafts, [policy.priority]: { ...draft, response: Number(e.target.value) } })
                    }
                  />
                </td>
                <td>
                  <input
                    type="number"
                    min={1}
                    value={draft.resolution}
                    onChange={(e) =>
                      setDrafts({ ...drafts, [policy.priority]: { ...draft, resolution: Number(e.target.value) } })
                    }
                  />
                </td>
                <td>
                  <button
                    className="btn btn-primary"
                    disabled={busy || !changed || draft.response < 1 || draft.resolution < 1}
                    onClick={() =>
                      runAction(
                        () => saveSlaPolicy(policy.priority, draft.response, draft.resolution),
                        setBusy,
                        onError,
                        load,
                      )
                    }
                  >
                    Lưu
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Section>
  );
}
