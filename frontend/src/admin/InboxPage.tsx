// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Card, CardContent, Table, TableBody, TableCell, TableHead, TableRow, Typography } from "@mui/material";
import { Link, Title } from "react-admin";
import { listInbox } from "./api";
import { resourceName, type InboxItem, type ModelMeta } from "./types";

// The records waiting for the signed-in user, on every model.
export default function InboxPage({ models }: { models: ModelMeta[] }) {
  const [items, setItems] = useState<InboxItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listInbox().then(setItems, (e: unknown) => setError(e instanceof Error ? e.message : "Không đọc được hộp việc"));
  }, []);

  const stateLabel = (item: InboxItem) =>
    models
      .find((model) => model.name === item.model)
      ?.entities.find((entity) => entity.name === item.entity)
      ?.process?.states.find((state) => state.name === item.status)?.label ?? item.status;

  return (
    <Card sx={{ mt: 2 }}>
      <Title title="Hộp việc" />
      <CardContent>
        <Typography variant="h6">Việc cần xử lý</Typography>
        {error && <Typography color="error">{error}</Typography>}
        {items?.length === 0 && <Typography sx={{ mt: 1 }}>Không có việc nào đang chờ bạn.</Typography>}
        {items && items.length > 0 && (
          <Table size="small" sx={{ mt: 1 }}>
            <TableHead>
              <TableRow>
                <TableCell>Loại</TableCell>
                <TableCell>Hồ sơ</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell>Nộp lúc</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((item) => (
                <TableRow key={`${item.model}-${item.entity}-${item.id}`}>
                  <TableCell>{item.entityLabel}</TableCell>
                  <TableCell>
                    <Link to={`/${resourceName(item.model, item.entity)}/${item.id}/show`}>
                      {item.title ?? `#${item.id}`}
                    </Link>
                  </TableCell>
                  <TableCell>{stateLabel(item)}</TableCell>
                  <TableCell>{new Date(item.createdAt).toLocaleString("vi-VN")}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
