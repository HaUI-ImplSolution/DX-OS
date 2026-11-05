// SPDX-License-Identifier: Apache-2.0
import {
  AutocompleteInput,
  Button,
  Create,
  CreateButton,
  Datagrid,
  DateField,
  Edit,
  EditButton,
  FilterButton,
  FunctionField,
  List,
  NumberField,
  NumberInput,
  ReferenceField,
  ReferenceInput,
  SelectInput,
  Show,
  SimpleForm,
  TextField,
  TextInput,
  TopToolbar,
  required,
  useRecordContext,
} from "react-admin";
import { Box, Card, Typography } from "@mui/material";
import EventIcon from "@mui/icons-material/Event";
import InsightsOutlinedIcon from "@mui/icons-material/InsightsOutlined";
import { Labeled } from "react-admin";
import { Link } from "react-router-dom";
import { timeAgo } from "../shell/format";
import { ActionButtons, AssignPanel, FilesPanel, HistoryPanel } from "./caseParts";
import DateTimeViInput from "./DateTimeViInput";
import { StatusChip } from "./StatusChip";
import { resourceName, type EntityMeta, type FieldMeta, type ModelMeta } from "./types";

interface Catalog {
  model: string;
  units: Record<string, string>;
  entities: Record<string, EntityMeta>;
}

// The field that names a record when another record refers to it.
export function displayField(entity: EntityMeta | undefined): string {
  const texts = entity?.fields.filter((field) => field.type === "text") ?? [];
  const preferred = ["name", "title", "code"].map((n) => texts.find((f) => f.name === n)).find(Boolean);
  return (preferred ?? texts[0])?.name ?? "id";
}

const DATE_OPTIONS = { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" } as const;

function statusField(entity: EntityMeta) {
  return (
    <FunctionField
      key="status"
      label="Trạng thái"
      render={(record: { status: string; overdue?: boolean }) => (
        <StatusChip entity={entity} status={record.status} overdue={record.overdue} />
      )}
    />
  );
}

function outputField(field: FieldMeta, catalog: Catalog) {
  switch (field.type) {
    case "number":
      return <NumberField key={field.name} source={field.name} label={field.label} emptyText="—" />;
    case "datetime":
      return <DateField key={field.name} source={field.name} label={field.label} locales="vi-VN" showTime options={DATE_OPTIONS} emptyText="—" />;
    case "unit":
      return (
        <FunctionField
          key={field.name}
          label={field.label}
          render={(record: Record<string, string>) => catalog.units[record[field.name]] ?? record[field.name]}
        />
      );
    case "reference": {
      const target = catalog.entities[field.reference ?? ""];
      return (
        <ReferenceField
          key={field.name}
          source={field.name}
          label={field.label}
          reference={resourceName(catalog.model, field.reference ?? "")}
          link="show"
        >
          <TextField source={displayField(target)} />
        </ReferenceField>
      );
    }
    default:
      return <TextField key={field.name} source={field.name} label={field.label} emptyText="—" />;
  }
}

function inputField(field: FieldMeta, catalog: Catalog) {
  const validate = field.required ? required() : undefined;
  switch (field.type) {
    case "number":
      return <NumberInput key={field.name} source={field.name} label={field.label} validate={validate} />;
    case "datetime":
      return <DateTimeViInput key={field.name} source={field.name} label={field.label} isRequired={field.required} />;
    case "choice":
    case "unit":
      return (
        <SelectInput
          key={field.name}
          source={field.name}
          label={field.label}
          choices={field.options.map((option) => ({ id: option, name: catalog.units[option] ?? option }))}
          validate={validate}
        />
      );
    case "reference": {
      const target = catalog.entities[field.reference ?? ""];
      return (
        <ReferenceInput
          key={field.name}
          source={field.name}
          label={field.label}
          reference={resourceName(catalog.model, field.reference ?? "")}
        >
          <AutocompleteInput label={field.label} optionText={displayField(target)} validate={validate} />
        </ReferenceInput>
      );
    }
    case "file":
      return null;
    default:
      return <TextInput key={field.name} source={field.name} label={field.label} validate={validate} />;
  }
}

function ShowActions({ model, entity }: { model: string; entity: EntityMeta }) {
  const record = useRecordContext();
  const closed = entity.process?.states.find((state) => state.name === record?.status)?.terminal ?? false;
  return (
    <Box sx={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 0.5 }}>
      {entity.canWrite && !closed && <EditButton label="Sửa" />}
      <ActionButtons model={model} entity={entity} />
    </Box>
  );
}

// The record as a page: who and what it is on top, its details and history beside the people and files.
function ShowBody({ model, entity, catalog }: { model: string; entity: EntityMeta; catalog: Catalog }) {
  const record = useRecordContext();
  if (!record) return null;
  const title = record[displayField(entity)] ?? `#${record.id}`;
  const hasDeadline = entity.process?.deadlineSeconds != null;
  return (
    <Box>
      <Card sx={{ p: 2.5, mb: 2, display: "flex", gap: 2, alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="caption" color="text.secondary">
            {entity.label} · Mã {record.id}
          </Typography>
          <Typography variant="h5" sx={{ my: 0.5 }}>
            {title}
          </Typography>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
            {entity.hasProcess && <StatusChip entity={entity} status={record.status} overdue={record.overdue} />}
            <Typography variant="body2" color="text.secondary">
              Tạo {timeAgo(record.created_at)}
            </Typography>
          </Box>
        </Box>
        <ShowActions model={model} entity={entity} />
      </Card>
      <Box sx={{ display: "grid", gap: 2, alignItems: "start", gridTemplateColumns: { xs: "1fr", md: "minmax(0, 2fr) minmax(0, 1fr)" } }}>
        <Box sx={{ display: "grid", gap: 2 }}>
          <Card sx={{ p: 2.5 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 2 }}>
              Thông tin
            </Typography>
            <Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))" }}>
              {entity.fields
                .filter((field) => field.type !== "file")
                .map((field) => (
                  <Labeled key={field.name} label={field.label}>
                    {outputField(field, catalog)}
                  </Labeled>
                ))}
              <Labeled label="Tạo lúc">
                <DateField source="created_at" locales="vi-VN" showTime options={DATE_OPTIONS} />
              </Labeled>
              {hasDeadline && (
                <Labeled label="Hạn xử lý">
                  <DateField source="handle_by" locales="vi-VN" showTime options={DATE_OPTIONS} emptyText="—" />
                </Labeled>
              )}
              {entity.hasProcess && record.decided_at && (
                <Labeled label="Quyết định lúc">
                  <DateField source="decided_at" locales="vi-VN" showTime options={DATE_OPTIONS} />
                </Labeled>
              )}
            </Box>
          </Card>
          <HistoryPanel model={model} entity={entity} />
        </Box>
        <Box sx={{ display: "grid", gap: 2 }}>
          {entity.hasProcess && <AssignPanel model={model} entity={entity} />}
          <FilesPanel model={model} entity={entity} />
        </Box>
      </Box>
    </Box>
  );
}

// Inputs fill the card, so a long text or a list of choices is as wide as the form.
const FORM_SX = { "& .MuiFormControl-root, & .RaInput-input": { width: "100%" } };
const CARD_SX = { "& .RaCreate-card, & .RaEdit-card": { maxWidth: 760 } };

// The screens of one entity, built from its metadata.
export function screens(model: ModelMeta, entity: EntityMeta) {
  const catalog: Catalog = {
    model: model.name,
    units: Object.fromEntries(model.units.map((unit) => [unit.name, unit.label])),
    entities: Object.fromEntries(model.entities.map((e) => [e.name, e])),
  };
  const filters = [
    ...(entity.hasProcess
      ? [
          <SelectInput
            key="status"
            source="status"
            alwaysOn
            label="Trạng thái"
            choices={(entity.process?.states ?? []).map((state) => ({ id: state.name, name: state.label }))}
          />,
        ]
      : []),
    ...entity.fields
      .filter((field) => field.type === "choice" || field.type === "unit")
      .map((field) => (
        <SelectInput
          key={field.name}
          source={field.name}
          label={field.label}
          choices={field.options.map((option) => ({ id: option, name: catalog.units[option] ?? option }))}
        />
      )),
  ];

  const calendar = entity.process?.booking != null;
  const ListActions = () => (
    <TopToolbar>
      <FilterButton />
      {entity.canWrite && <CreateButton />}
      {calendar && (
        <Button component={Link} to={`/calendar/${resourceName(model.name, entity.name)}`} label="Lịch">
          <EventIcon />
        </Button>
      )}
      <Button component={Link} to={`/stats/${resourceName(model.name, entity.name)}`} label="Thống kê">
        <InsightsOutlinedIcon />
      </Button>
    </TopToolbar>
  );

  const EntityList = () => (
    <List
      filters={filters}
      actions={entity.hasProcess ? <ListActions /> : undefined}
      sort={{ field: "id", order: "DESC" }}
      exporter={false}
    >
      <Datagrid rowClick="show" bulkActionButtons={false}>
        <TextField source="id" label="Mã" />
        {entity.fields.map((field) => outputField(field, catalog))}
        {entity.hasProcess && statusField(entity)}
      </Datagrid>
    </List>
  );

  const EntityShow = () => (
    <Show actions={false} component="div">
      <ShowBody model={model.name} entity={entity} catalog={catalog} />
    </Show>
  );

  const EntityCreate = () => (
    <Create redirect="show" sx={CARD_SX}>
      <SimpleForm sx={FORM_SX}>{entity.fields.map((field) => inputField(field, catalog))}</SimpleForm>
    </Create>
  );

  const EntityEdit = () => (
    <Edit mutationMode="pessimistic" redirect="show" sx={CARD_SX}>
      <SimpleForm sx={FORM_SX}>{entity.fields.map((field) => inputField(field, catalog))}</SimpleForm>
    </Edit>
  );

  return { list: EntityList, show: EntityShow, create: EntityCreate, edit: EntityEdit };
}
