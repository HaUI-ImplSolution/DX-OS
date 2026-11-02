// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState } from "react";
import { Admin, Layout, Menu, Resource, type LayoutProps } from "react-admin";
import { listModels } from "./api";
import authProvider from "./authProvider";
import { createDataProvider } from "./dataProvider";
import InboxPage from "./InboxPage";
import { screens } from "./resources";
import { resourceName, type EntityMeta, type ModelMeta } from "./types";

const AppMenu = () => (
  <Menu>
    <Menu.DashboardItem primaryText="Hộp việc" />
    <Menu.ResourceItems />
  </Menu>
);

const AppLayout = ({ children }: LayoutProps) => <Layout menu={AppMenu}>{children}</Layout>;

// Reads the metadata of the applied models and builds one resource per entity the user can reach.
export default function AdminApp() {
  const [models, setModels] = useState<ModelMeta[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listModels().then(setModels, (e: unknown) => setError(e instanceof Error ? e.message : "Không đọc được metadata"));
  }, []);

  const byResource = useMemo(() => {
    const map: Record<string, EntityMeta> = {};
    models?.forEach((model) => model.entities.forEach((entity) => (map[resourceName(model.name, entity.name)] = entity)));
    return map;
  }, [models]);
  const dataProvider = useMemo(() => createDataProvider((resource) => byResource[resource]), [byResource]);

  if (error) return <p className="section-subtitle">{error}</p>;
  if (!models) return <div className="login-screen" />;

  return (
    <Admin
      dataProvider={dataProvider}
      authProvider={authProvider}
      dashboard={() => <InboxPage models={models} />}
      layout={AppLayout}
      requireAuth
      disableTelemetry
    >
      {models.flatMap((model) =>
        model.entities.map((entity) => {
          const views = screens(model, entity);
          return (
            <Resource
              key={resourceName(model.name, entity.name)}
              name={resourceName(model.name, entity.name)}
              options={{ label: entity.label }}
              list={views.list}
              show={views.show}
              create={entity.canWrite ? views.create : undefined}
              edit={entity.canWrite ? views.edit : undefined}
              recordRepresentation="id"
            />
          );
        }),
      )}
    </Admin>
  );
}
