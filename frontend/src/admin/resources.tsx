// SPDX-License-Identifier: Apache-2.0
import { Fragment } from "react";
import {
  AutocompleteInput,
  BooleanField,
  BooleanInput,
  Button,
  Create,
  CreateButton,
  DatagridConfigurable,
  DateField,
  DateInput,
  FormDataConsumer,
  Edit,
  EditButton,
  FilterButton,
  FunctionField,
  List,
  NumberField,
  NumberInput,
  ReferenceField,
  ReferenceInput,
  SelectColumnsButton,
  SelectInput,
  Show,
  SimpleForm,
  SimpleList,
  TextField,
  TextInput,
  SaveButton,
  SelectArrayInput,
  Toolbar,
  TopToolbar,
  maxValue,
  useListContext,
  useStore,
  minValue,
  regex,
  required,
  type Validator,
  useRecordContext,
} from "react-admin";
import { Box, Card, ToggleButton, ToggleButtonGroup, Tooltip, Typography, useMediaQuery, type Theme } from "@mui/material";
import ViewKanbanOutlinedIcon from "@mui/icons-material/ViewKanbanOutlined";
import ViewListOutlinedIcon from "@mui/icons-material/ViewListOutlined";
import EventIcon from "@mui/icons-material/Event";
import InsightsOutlinedIcon from "@mui/icons-material/InsightsOutlined";
import { Labeled } from "react-admin";
import { Link } from "react-router-dom";
import { BulkBar } from "./BulkActions";
import { ActionButtons, AssignPanel, FilesPanel, HistoryPanel } from "./caseParts";
import CodeField from "./CodeField";
import DateTimeViInput from "./DateTimeViInput";
import ExportCsvButton from "./ExportCsvButton";
import KanbanBoard from "./KanbanBoard";
import { conditionMet, conditionText } from "./condition";
import { PersonField, PersonInput } from "./people";
import { StatusChip } from "./StatusChip";
import { resourceName, type EntityMeta, type FieldMeta, type ModelMeta } from "./types";
import { t } from "../shell/t";
import { intlLocale, timeAgo } from "../shell/format";

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
      source="status"
      label={t("Trạng thái")}
      render={(record: { status: string; overdue?: boolean }) => (
        <StatusChip entity={entity} status={record.status} overdue={record.overdue} />
      )}
    />
  );
}

function outputField(field: FieldMeta, catalog: Catalog) {
  if (field.multiple) {
    return (
      <FunctionField
        key={field.name}
        source={field.name}
        sortable={false}
        label={field.label}
        render={(record: Record<string, unknown>) => {
          const values = record[field.name];
          return Array.isArray(values) && values.length > 0 ? (values as string[]).map((value) => catalog.units[value] ?? value).join(", ") : "—";
        }}
      />
    );
  }
  switch (field.type) {
    case "boolean":
      return <BooleanField key={field.name} source={field.name} label={field.label} />;
    case "date":
      return (
        <DateField
          key={field.name}
          source={field.name}
          label={field.label}
          locales={intlLocale()}
          options={{ day: "2-digit", month: "2-digit", year: "numeric" }}
          emptyText="—"
        />
      );
    case "number":
      return <NumberField key={field.name} source={field.name} label={field.label} emptyText="—" />;
    case "datetime":
      return <DateField key={field.name} source={field.name} label={field.label} locales={intlLocale()} showTime options={DATE_OPTIONS} emptyText="—" />;
    case "unit":
      return (
        <FunctionField
          key={field.name}
          source={field.name}
          label={field.label}
          render={(record: Record<string, string>) => catalog.units[record[field.name]] ?? record[field.name]}
        />
      );
    case "user":
      return <PersonField key={field.name} source={field.name} label={field.label} />;
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

// Columns a list has but hides until the model file or the person asks for them.
const EXTRA_COLUMNS = ["assignee", "created_at", "updated_at"];

// The inputs of a form: in the groups the model file declares, with the fields it did not list last.
function formInputs(entity: EntityMeta, catalog: Catalog) {
  // A code is made when the record is created and cannot be typed or changed.
  const fields = entity.fields.filter((field) => field.type !== "code");
  const input = (field: FieldMeta) =>
    field.requiredWhen ? (
      <RequiredWhenInput
        key={field.name}
        field={field}
        build={(required) => inputField(field, catalog, entity.fields, entity.ui?.hints?.[field.name], required)}
      />
    ) : (
      inputField(field, catalog, entity.fields, entity.ui?.hints?.[field.name])
    );
  const groups = entity.ui?.form?.groups;
  if (!groups || groups.length === 0) return fields.map(input);
  const listed = new Set(groups.flatMap((group) => group.fields));
  const rest = fields.filter((field) => !listed.has(field.name));
  const heading = (label: string) => (
    <Typography variant="subtitle2" sx={{ fontWeight: 700, mt: 1, mb: 1.5, width: "100%" }}>
      {label}
    </Typography>
  );
  const sections = groups.map((group) => (
    <Fragment key={group.label}>
      {heading(group.label)}
      {group.fields.map((name) => fields.find((field) => field.name === name)).filter((field): field is FieldMeta => field !== undefined).map(input)}
    </Fragment>
  ));
  if (rest.length > 0) {
    sections.push(
      <Fragment key="__rest">
        {heading(t("Khác"))}
        {rest.map(input)}
      </Fragment>,
    );
  }
  return sections;
}

// The rules the API checks, so a form reports a problem before it sends the record.
function rules(field: FieldMeta, fields: FieldMeta[]) {
  const checks: Validator[] = [];
  if (field.required) checks.push(required());
  const when = field.requiredWhen;
  if (when) {
    checks.push((value: unknown, all: Record<string, unknown>) =>
      conditionMet(when, all) && (value === undefined || value === null || value === "" || (Array.isArray(value) && value.length === 0))
        ? t("Bắt buộc nhập khi {condition}", { condition: conditionText(when, fields) })
        : undefined,
    );
  }
  if (field.type === "number") {
    if (field.min != null) checks.push(minValue(Number(field.min)));
    if (field.max != null) checks.push(maxValue(Number(field.max)));
  }
  if (field.type === "date") {
    const { min, max } = field;
    if (min != null) checks.push((value) => (typeof value === "string" && value !== "" && value < min ? t("Từ ngày {date} trở đi", { date: min }) : undefined));
    if (max != null) checks.push((value) => (typeof value === "string" && value !== "" && value > max ? t("Đến hết ngày {date}", { date: max }) : undefined));
  }
  if (field.type === "text" && field.pattern) {
    checks.push(regex(new RegExp(`^(?:${field.pattern})$`), t("Không đúng định dạng")));
  }
  return checks.length > 0 ? checks : undefined;
}

// The limits of a number or a date, and the condition that makes a field required, as a line under the input.
function limitHint(field: FieldMeta, fields: FieldMeta[]): string | undefined {
  const parts: string[] = [];
  if (field.type === "number" || field.type === "date") {
    if (field.min != null && field.max != null) parts.push(t("Từ {min} đến {max}", { min: field.min, max: field.max }));
    else if (field.min != null) parts.push(t("Từ {min} trở lên", { min: field.min }));
    else if (field.max != null) parts.push(t("Tối đa {max}", { max: field.max }));
  }
  if (field.requiredWhen) parts.push(t("Bắt buộc khi {condition}", { condition: conditionText(field.requiredWhen, fields) }));
  return parts.length > 0 ? parts.join(" · ") : undefined;
}

// A field whose requirement depends on another one is marked required while its condition holds.
function RequiredWhenInput({ field, build }: { field: FieldMeta; build: (required: boolean) => JSX.Element | null }) {
  return <FormDataConsumer>{({ formData }) => build(field.requiredWhen != null && conditionMet(field.requiredWhen, formData))}</FormDataConsumer>;
}

function inputField(field: FieldMeta, catalog: Catalog, fields: FieldMeta[], hint?: string, required = field.required) {
  const validate = rules(field, fields);
  const helperText = [hint, limitHint(field, fields)].filter(Boolean).join(" · ") || undefined;
  const mark = (label: string) => (required && !field.required ? `${label} *` : label);
  switch (field.type) {
    case "number":
      return <NumberInput key={field.name} source={field.name} label={mark(field.label)} validate={validate} helperText={helperText} />;
    case "boolean":
      return <BooleanInput key={field.name} source={field.name} label={mark(field.label)} helperText={helperText} />;
    case "date":
      return <DateInput key={field.name} source={field.name} label={mark(field.label)} validate={validate} helperText={helperText} />;
    case "datetime":
      return <DateTimeViInput key={field.name} source={field.name} label={mark(field.label)} isRequired={field.required} />;
    case "choice":
    case "unit": {
      const choices = field.options.map((option) => ({ id: option, name: catalog.units[option] ?? option }));
      return field.multiple ? (
        <SelectArrayInput key={field.name} source={field.name} label={mark(field.label)} choices={choices} validate={validate} helperText={helperText} />
      ) : (
        <SelectInput key={field.name} source={field.name} label={mark(field.label)} choices={choices} validate={validate} helperText={helperText} />
      );
    }
    case "reference": {
      const target = catalog.entities[field.reference ?? ""];
      return (
        <ReferenceInput
          key={field.name}
          source={field.name}
          label={mark(field.label)}
          reference={resourceName(catalog.model, field.reference ?? "")}
        >
          <AutocompleteInput label={mark(field.label)} optionText={displayField(target)} validate={validate} />
        </ReferenceInput>
      );
    }
    case "user":
      return <PersonInput key={field.name} source={field.name} label={mark(field.label)} required={field.required} />;
    case "file":
      return null;
    default:
      return <TextInput key={field.name} source={field.name} label={mark(field.label)} validate={validate} helperText={helperText} />;
  }
}

// The readable code of a record when its entity has a field of that type.
function codeOf(entity: EntityMeta, record: Record<string, unknown>): string | null {
  const field = entity.fields.find((candidate) => candidate.type === "code");
  const value = field ? record[field.name] : null;
  return typeof value === "string" && value !== "" ? value : null;
}

function ShowActions({ model, entity }: { model: string; entity: EntityMeta }) {
  const record = useRecordContext();
  const closed = entity.process?.states.find((state) => state.name === record?.status)?.terminal ?? false;
  return (
    <Box sx={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 0.5 }}>
      {entity.canWrite && !closed && <EditButton label={t("Sửa")} />}
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
            {entity.label} · {t("Mã")} {codeOf(entity, record) ?? record.id}
          </Typography>
          <Typography variant="h5" sx={{ my: 0.5 }}>
            {title}
          </Typography>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
            {entity.hasProcess && <StatusChip entity={entity} status={record.status} overdue={record.overdue} />}
            <Typography variant="body2" color="text.secondary">
              {t("Tạo {when}", { when: timeAgo(record.created_at) })}
            </Typography>
          </Box>
        </Box>
        <ShowActions model={model} entity={entity} />
      </Card>
      <Box sx={{ display: "grid", gap: 2, alignItems: "start", gridTemplateColumns: { xs: "1fr", md: "minmax(0, 2fr) minmax(0, 1fr)" } }}>
        <Box sx={{ display: "grid", gap: 2 }}>
          <Card sx={{ p: 2.5 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 2 }}>
              {t("Thông tin")}
            </Typography>
            <Box sx={{ display: "grid", gap: 2.5, gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))" }}>
              {entity.fields
                .filter((field) => field.type !== "file")
                .map((field) => (
                  <Labeled key={field.name} label={field.label}>
                    {field.type === "code" ? <CodeField source={field.name} /> : outputField(field, catalog)}
                  </Labeled>
                ))}
              <Labeled label={t("Tạo lúc")}>
                <DateField source="created_at" locales={intlLocale()} showTime options={DATE_OPTIONS} />
              </Labeled>
              {hasDeadline && (
                <Labeled label={t("Hạn xử lý")}>
                  <DateField source="handle_by" locales={intlLocale()} showTime options={DATE_OPTIONS} emptyText="—" />
                </Labeled>
              )}
              {entity.hasProcess && record.decided_at && (
                <Labeled label={t("Quyết định lúc")}>
                  <DateField source="decided_at" locales={intlLocale()} showTime options={DATE_OPTIONS} />
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
// A record with a process is withdrawn by its owner, never deleted.
const SAVE_ONLY = (
  <Toolbar>
    <SaveButton />
  </Toolbar>
);

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
            label={t("Trạng thái")}
            choices={(entity.process?.states ?? []).map((state) => ({ id: state.name, name: t(state.label) }))}
          />,
        ]
      : []),
    ...entity.fields.flatMap((field) => {
      const choices = field.options.map((option) => ({ id: option, name: catalog.units[option] ?? option }));
      if (field.type === "boolean") {
        return [
          <SelectInput
            key={field.name}
            source={field.name}
            label={field.label}
            choices={[
              { id: "true", name: t("Có") },
              { id: "false", name: t("Không") },
            ]}
          />,
        ];
      }
      if (field.type === "date") {
        return [
          <DateInput key={`${field.name}.gte`} source={`${field.name}.gte`} label={t("{label} từ", { label: field.label })} />,
          <DateInput key={`${field.name}.lte`} source={`${field.name}.lte`} label={t("{label} đến", { label: field.label })} />,
        ];
      }
      // A code is searched by any part of it.
      if (field.type === "code") {
        return [<TextInput key={`${field.name}.like`} source={`${field.name}.like`} label={field.label} alwaysOn />];
      }
      // A field with several values is filtered by one option it holds.
      if ((field.type === "choice" || field.type === "unit") && field.multiple) {
        return [<SelectInput key={field.name} source={`${field.name}.contains`} label={field.label} choices={choices} />];
      }
      return field.type === "choice" || field.type === "unit"
        ? [<SelectInput key={field.name} source={field.name} label={field.label} choices={choices} />]
        : [];
    }),
  ];

  // A person who handles records can take one action on several of them at once.
  const bulk = entity.hasProcess && entity.canHandle && (entity.handlerActions?.length ?? 0) > 0;
  const calendar = entity.process?.booking != null;
  // The filters, the sort and the columns a person picks are kept in the browser, one set per entity.
  const viewKey = `dxos.view.${resourceName(model.name, entity.name)}`;
  const ListActions = () => {
    const [view, setView] = useStore<"list" | "board">(viewKey, "list");
    return (
    <TopToolbar>
      {entity.hasProcess && (
        <ToggleButtonGroup exclusive size="small" value={view} onChange={(_, next: "list" | "board" | null) => next && setView(next)} sx={{ mr: 0.5 }}>
          <ToggleButton value="list" aria-label={t("Danh sách")}>
            <Tooltip title={t("Danh sách")}>
              <ViewListOutlinedIcon fontSize="small" />
            </Tooltip>
          </ToggleButton>
          <ToggleButton value="board" aria-label={t("Bảng theo trạng thái")}>
            <Tooltip title={t("Bảng theo trạng thái")}>
              <ViewKanbanOutlinedIcon fontSize="small" />
            </Tooltip>
          </ToggleButton>
        </ToggleButtonGroup>
      )}
      <FilterButton />
      {view === "list" && <SelectColumnsButton />}
      <ExportCsvButton entity={entity} units={catalog.units} />
      {entity.canWrite && <CreateButton />}
      {calendar && (
        <Button component={Link} to={`/calendar/${resourceName(model.name, entity.name)}`} label={t("Lịch")}>
          <EventIcon />
        </Button>
      )}
      {entity.hasProcess && (
        <Button component={Link} to={`/stats/${resourceName(model.name, entity.name)}`} label={t("Thống kê")}>
          <InsightsOutlinedIcon />
        </Button>
      )}
    </TopToolbar>
    );
  };

  // The model file may choose the columns and the sort a list opens with; what a person picks later with
  // the Columns button is kept in the browser and wins over it.
  const listUi = entity.ui?.list;
  const shown = listUi?.columns ?? null;
  const known = [...entity.fields.filter((field) => field.type !== "file").map((field) => field.name), ...(entity.hasProcess ? ["status"] : [])];
  const omit = [
    ...known.filter((name) => shown !== null && !shown.includes(name)),
    ...EXTRA_COLUMNS.filter((name) => !(shown ?? []).includes(name)),
  ];
  const defaultSort = listUi?.sort
    ? { field: listUi.sort.replace(/^-/, ""), order: listUi.sort.startsWith("-") ? ("DESC" as const) : ("ASC" as const) }
    : { field: "id", order: "DESC" as const };

  // The records as a table, or, for an entity with a process, by state; the choice is kept in the browser.
  const ListBody = () => {
    const [view, setView] = useStore<"list" | "board">(viewKey, "list");
    const { filterValues, setFilters } = useListContext();
    // A phone shows each record as a row of text, not a table that needs sideways scrolling.
    const phone = useMediaQuery((theme: Theme) => theme.breakpoints.down("sm"));
    if (view === "board" && entity.hasProcess) {
      return (
        <KanbanBoard
          entity={entity}
          titleField={displayField(entity)}
          onAll={(status) => {
            setFilters({ ...filterValues, status }, {});
            setView("list");
          }}
        />
      );
    }
    if (phone) {
      const titleField = displayField(entity);
      const detail = entity.fields.filter((field) => field.name !== titleField && ["code", "text", "choice", "unit", "number", "date"].includes(field.type));
      return (
        <SimpleList
          rowClick="show"
          primaryText={(record: Record<string, unknown>) => String(record[titleField] ?? `#${record.id}`)}
          secondaryText={(record: { status?: string; overdue?: boolean } & Record<string, unknown>) => (
            <Box component="span" sx={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 0.5 }}>
              {entity.hasProcess && record.status && <StatusChip entity={entity} status={record.status} overdue={record.overdue} />}
              {detail
                .filter((field) => record[field.name] != null && record[field.name] !== "")
                .slice(0, 2)
                .map((field) => (
                  <Box component="span" key={field.name} sx={{ fontSize: 13 }}>
                    {field.label}: {catalog.units[String(record[field.name])] ?? String(record[field.name])}
                  </Box>
                ))}
            </Box>
          )}
          tertiaryText={(record: Record<string, unknown>) => (typeof record.updated_at === "string" ? timeAgo(record.updated_at) : "")}
        />
      );
    }
    return (
      <DatagridConfigurable
        rowClick="show"
        omit={omit}
        bulkActionButtons={bulk ? <span /> : false}
        bulkActionsToolbar={bulk ? <BulkBar model={model.name} entity={entity} /> : undefined}
      >
        {entity.fields.filter((field) => field.type === "code").map((field) => outputField(field, catalog))}
        <TextField source="id" label={t("Mã")} />
        {entity.fields.filter((field) => field.type !== "code").map((field) => outputField(field, catalog))}
        {entity.hasProcess && statusField(entity)}
        {entity.hasProcess && <PersonField source="assignee" label={t("Người xử lý")} />}
        <DateField source="created_at" label={t("Tạo lúc")} locales={intlLocale()} showTime options={DATE_OPTIONS} />
        <DateField source="updated_at" label={t("Cập nhật lúc")} locales={intlLocale()} showTime options={DATE_OPTIONS} />
      </DatagridConfigurable>
    );
  };

  const EntityList = () => {
    const [view] = useStore<"list" | "board">(viewKey, "list");
    return (
      <List
        filters={filters}
        actions={<ListActions />}
        sort={defaultSort}
        exporter={false}
        pagination={view === "board" && entity.hasProcess ? false : undefined}
      >
        <ListBody />
      </List>
    );
  };

  const EntityShow = () => (
    <Show actions={false} component="div">
      <ShowBody model={model.name} entity={entity} catalog={catalog} />
    </Show>
  );

  const EntityCreate = () => (
    <Create redirect="show" sx={CARD_SX}>
      <SimpleForm sx={FORM_SX}>{formInputs(entity, catalog)}</SimpleForm>
    </Create>
  );

  const EntityEdit = () => (
    <Edit mutationMode="pessimistic" redirect="show" sx={CARD_SX}>
      <SimpleForm sx={FORM_SX} toolbar={entity.hasProcess ? SAVE_ONLY : undefined}>
        {formInputs(entity, catalog)}
      </SimpleForm>
    </Edit>
  );

  return { list: EntityList, show: EntityShow, create: EntityCreate, edit: EntityEdit };
}
