// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import DownloadOutlinedIcon from "@mui/icons-material/DownloadOutlined";
import { Button, useDataProvider, useListContext, useNotify, useResourceContext } from "react-admin";
import { peopleByIds } from "./api";
import { download, toCsv } from "./csvExport";
import type { EntityMeta } from "./types";
import { formatDateTime } from "../shell/format";
import { t } from "../shell/t";

// A list is written out through the same calls the screen makes, so a person gets the rows they may see.
const PAGE = 200;
const MAX_ROWS = 10000;

type Row = Record<string, unknown>;

// Exports the rows of the list with its filters and sort, as a CSV file a spreadsheet can open.
export default function ExportCsvButton({ entity, units }: { entity: EntityMeta; units: Record<string, string> }) {
  const resource = useResourceContext() ?? "";
  const { filterValues, sort } = useListContext();
  const dataProvider = useDataProvider();
  const notify = useNotify();
  const [busy, setBusy] = useState(false);

  const run = async () => {
    setBusy(true);
    try {
      const rows: Row[] = [];
      let total = 0;
      for (let page = 1; rows.length < MAX_ROWS; page += 1) {
        const result = await dataProvider.getList(resource, { pagination: { page, perPage: PAGE }, sort, filter: filterValues });
        total = result.total ?? 0;
        rows.push(...(result.data as Row[]));
        if (result.data.length < PAGE || rows.length >= total) break;
      }
      const kept = rows.slice(0, MAX_ROWS);
      const fields = entity.fields.filter((field) => field.type !== "file");
      const names = await personNames(kept, fields.filter((field) => field.type === "user").map((field) => field.name));
      const stateLabel = (status: unknown) => t(entity.process?.states.find((state) => state.name === status)?.label ?? String(status ?? ""));
      const header = [
        t("Mã"),
        ...fields.map((field) => field.label),
        ...(entity.hasProcess ? [t("Trạng thái")] : []),
        t("Tạo lúc"),
        t("Cập nhật lúc"),
      ].map((text) => ({ text }));
      const body = kept.map((row) => [
        { text: String(row.id ?? ""), keepAsIs: true },
        ...fields.map((field) => {
          const value = row[field.name];
          if (value === null || value === undefined) return { text: "" };
          if (field.multiple && Array.isArray(value)) return { text: value.map((item) => units[String(item)] ?? String(item)).join("; ") };
          switch (field.type) {
            case "boolean":
              return { text: value === true ? t("Có") : t("Không") };
            case "datetime":
              return { text: formatDateTime(String(value)) };
            case "number":
              return { text: String(value), keepAsIs: true };
            case "unit":
              return { text: units[String(value)] ?? String(value) };
            case "user":
              return { text: names[String(value)] ?? String(value) };
            default:
              return { text: String(value) };
          }
        }),
        ...(entity.hasProcess ? [{ text: stateLabel(row.status) }] : []),
        { text: row.created_at ? formatDateTime(String(row.created_at)) : "" },
        { text: row.updated_at ? formatDateTime(String(row.updated_at)) : "" },
      ]);
      download(`${entity.name}-${new Date().toISOString().slice(0, 10)}.csv`, toCsv([header, ...body]));
      notify(total > kept.length ? t("Đã xuất {n} hồ sơ đầu tiên trong {total}", { n: kept.length, total }) : t("Đã xuất {n} hồ sơ", { n: kept.length }), {
        type: total > kept.length ? "warning" : "success",
      });
    } catch (error) {
      notify(error instanceof Error ? error.message : t("Không xử lý được"), { type: "error" });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button label={t("Xuất CSV")} disabled={busy} onClick={run}>
      <DownloadOutlinedIcon />
    </Button>
  );
}

// The names of the people a user field holds, looked up fifty at a time.
async function personNames(rows: Row[], userFields: string[]): Promise<Record<string, string>> {
  const ids = [...new Set(rows.flatMap((row) => userFields.map((name) => row[name]).filter((id): id is string => typeof id === "string" && id !== "")))];
  const names: Record<string, string> = {};
  for (let start = 0; start < ids.length; start += 50) {
    try {
      (await peopleByIds(ids.slice(start, start + 50))).forEach((person) => {
        names[person.id] = person.name;
      });
    } catch {
      // A name that cannot be found is written as the id.
    }
  }
  return names;
}
