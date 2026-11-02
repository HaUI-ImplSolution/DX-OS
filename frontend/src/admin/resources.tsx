// SPDX-License-Identifier: Apache-2.0
import {
  AutocompleteInput,
  Create,
  Datagrid,
  DateField,
  DateTimeInput,
  Edit,
  EditButton,
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
  useRecordContext,
} from "react-admin";
import { ActionButtons, AssignPanel, HistoryPanel } from "./caseParts";
import { resourceName, type EntityMeta, type FieldMeta, type ModelMeta } from "./types";

interface Catalog {
  model: string;
  units: Record<string, string>;
  entities: Record<string, EntityMeta>;
}

// The field that names a record when another record refers to it.
function displayField(entity: EntityMeta | undefined): string {
  const texts = entity?.fields.filter((field) => field.type === "text") ?? [];
  const preferred = ["name", "title", "code"].map((n) => texts.find((f) => f.name === n)).find(Boolean);
  return (preferred ?? texts[0])?.name ?? "id";
}

function statusField(entity: EntityMeta) {
  const labels = Object.fromEntries((entity.process?.states ?? []).map((state) => [state.name, state.label]));
  return (
    <FunctionField
      key="status"
      label="Trạng thái"
      render={(record: { status: string }) => labels[record.status] ?? record.status}
    />
  );
}

function outputField(field: FieldMeta, catalog: Catalog) {
  switch (field.type) {
    case "number":
      return <NumberField key={field.name} source={field.name} label={field.label} />;
    case "datetime":
      return <DateField key={field.name} source={field.name} label={field.label} locales="vi-VN" showTime />;
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
    default:
      return <TextInput key={field.name} source={field.name} label={field.label} validate={validate} />;
  }
}

function ShowActions({ model, entity }: { model: string; entity: EntityMeta }) {
  const record = useRecordContext();
  const closed = entity.process?.states.find((state) => state.name === record?.status)?.terminal ?? false;
  return (
    <TopToolbar>
      {entity.canWrite && !closed && <EditButton label="Sửa" />}
      <ActionButtons model={model} entity={entity} />
    </TopToolbar>
  );
}

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

  const EntityList = () => (
    <List filters={filters} sort={{ field: "id", order: "DESC" }} exporter={false}>
      <Datagrid rowClick="show" bulkActionButtons={false}>
        <TextField source="id" />
        {entity.fields.map((field) => outputField(field, catalog))}
        {entity.hasProcess && statusField(entity)}
      </Datagrid>
    </List>
  );

  const EntityShow = () => (
    <Show actions={<ShowActions model={model.name} entity={entity} />}>
      <SimpleShowLayout>
        <TextField source="id" />
        {entity.fields.map((field) => outputField(field, catalog))}
        {entity.hasProcess && statusField(entity)}
        <DateField source="created_at" label="Tạo lúc" locales="vi-VN" showTime />
        {entity.hasProcess && <DateField source="decided_at" label="Quyết định lúc" locales="vi-VN" showTime />}
        {entity.hasProcess && <AssignPanel model={model.name} entity={entity} />}
        <HistoryPanel model={model.name} entity={entity} />
      </SimpleShowLayout>
    </Show>
  );

  const EntityCreate = () => (
    <Create redirect="show">
      <SimpleForm>{entity.fields.map((field) => inputField(field, catalog))}</SimpleForm>
    </Create>
  );

  const EntityEdit = () => (
    <Edit mutationMode="pessimistic" redirect="show">
      <SimpleForm>{entity.fields.map((field) => inputField(field, catalog))}</SimpleForm>
    </Edit>
  );

  return { list: EntityList, show: EntityShow, create: EntityCreate, edit: EntityEdit };
}
