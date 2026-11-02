// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import { Button as MuiButton } from "@mui/material";
import {
  AutocompleteInput,
  Create,
  Datagrid,
  DateField,
  DateTimeInput,
  Edit,
  FunctionField,
  List,
  NumberField,
  NumberInput,
  ReferenceField,
  ReferenceInput,
  SelectInput,
  Show,
  SimpleForm,
  SimpleShowLayout,
  TextField,
  TextInput,
  TopToolbar,
  required,
  useGetIdentity,
  useNotify,
  useRecordContext,
  useRefresh,
} from "react-admin";
import { decide } from "./api";
import { resourceName, type EntityMeta, type FieldMeta, type ModelMeta } from "./types";

interface Catalog {
  model: string;
  entities: Record<string, EntityMeta>;
}

// The field that names a record when another record refers to it.
function displayField(entity: EntityMeta | undefined): string {
  const texts = entity?.fields.filter((field) => field.type === "text") ?? [];
  const preferred = ["name", "title", "code"].map((n) => texts.find((f) => f.name === n)).find(Boolean);
  return (preferred ?? texts[0])?.name ?? "id";
}

const STATUS_LABELS: Record<string, string> = {
  submitted: "Chờ duyệt",
  approved: "Đã duyệt",
  rejected: "Từ chối",
};

function statusField() {
  return (
    <FunctionField
      key="status"
      label="Trạng thái"
      render={(record: { status: string }) => STATUS_LABELS[record.status] ?? record.status}
    />
  );
}

function outputField(field: FieldMeta, catalog: Catalog) {
  switch (field.type) {
    case "number":
      return <NumberField key={field.name} source={field.name} label={field.label} />;
    case "datetime":
      return <DateField key={field.name} source={field.name} label={field.label} showTime />;
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
      return <TextField key={field.name} source={field.name} label={field.label} />;
  }
}

function inputField(field: FieldMeta, catalog: Catalog) {
  const validate = field.required ? required() : undefined;
  switch (field.type) {
    case "number":
      return <NumberInput key={field.name} source={field.name} label={field.label} validate={validate} />;
    case "datetime":
      return <DateTimeInput key={field.name} source={field.name} label={field.label} validate={validate} />;
    case "choice":
      return (
        <SelectInput
          key={field.name}
          source={field.name}
          label={field.label}
          choices={field.options.map((option) => ({ id: option, name: option }))}
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
    default:
      return <TextInput key={field.name} source={field.name} label={field.label} validate={validate} />;
  }
}

function DecisionButtons({ model, entity }: { model: string; entity: EntityMeta }) {
  const record = useRecordContext();
  const { data: identity } = useGetIdentity();
  const notify = useNotify();
  const refresh = useRefresh();
  const [busy, setBusy] = useState(false);
  if (!record || !entity.canDecide || record.status !== "submitted") return null;
  if (!entity.process?.allowSelfDecision && record.created_by === identity?.id) return null;

  const send = async (outcome: "approve" | "reject") => {
    setBusy(true);
    try {
      await decide(model, entity.name, record.id as number, outcome);
      notify(outcome === "approve" ? "Đã duyệt" : "Đã từ chối", { type: "success" });
      refresh();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Không xử lý được", { type: "error" });
    } finally {
      setBusy(false);
    }
  };

  return (
    <TopToolbar>
      <MuiButton variant="contained" color="success" disabled={busy} onClick={() => send("approve")}>
        Duyệt
      </MuiButton>
      <MuiButton variant="outlined" color="error" disabled={busy} onClick={() => send("reject")}>
        Từ chối
      </MuiButton>
    </TopToolbar>
  );
}

// The screens of one entity, built from its metadata.
export function screens(model: ModelMeta, entity: EntityMeta) {
  const catalog: Catalog = {
    model: model.name,
    entities: Object.fromEntries(model.entities.map((e) => [e.name, e])),
  };
  const filters = [
    ...(entity.hasProcess
      ? [
          <SelectInput
            key="status"
            source="status"
            alwaysOn
            choices={Object.entries(STATUS_LABELS).map(([id, name]) => ({ id, name }))}
          />,
        ]
      : []),
    ...entity.fields
      .filter((field) => field.type === "choice")
      .map((field) => (
        <SelectInput
          key={field.name}
          source={field.name}
          label={field.label}
          choices={field.options.map((option) => ({ id: option, name: option }))}
        />
      )),
  ];

  const EntityList = () => (
    <List filters={filters} sort={{ field: "id", order: "DESC" }} exporter={false}>
      <Datagrid rowClick="show" bulkActionButtons={false}>
        <TextField source="id" />
        {entity.fields.map((field) => outputField(field, catalog))}
        {entity.hasProcess && statusField()}
      </Datagrid>
    </List>
  );

  const EntityShow = () => (
    <Show actions={<DecisionButtons model={model.name} entity={entity} />}>
      <SimpleShowLayout>
        <TextField source="id" />
        {entity.fields.map((field) => outputField(field, catalog))}
        {entity.hasProcess && statusField()}
        <DateField source="created_at" label="Tạo lúc" showTime />
      </SimpleShowLayout>
    </Show>
  );

  const EntityCreate = () => (
    <Create redirect="show">
      <SimpleForm>{entity.fields.map((field) => inputField(field, catalog))}</SimpleForm>
    </Create>
  );

  const EntityEdit = () => (
    <Edit mutationMode="pessimistic">
      <SimpleForm>{entity.fields.map((field) => inputField(field, catalog))}</SimpleForm>
    </Edit>
  );

  return { list: EntityList, show: EntityShow, create: EntityCreate, edit: EntityEdit };
}
