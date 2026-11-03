// SPDX-License-Identifier: Apache-2.0
import { Suspense, lazy, useEffect, useMemo, useState } from "react";
import { Route } from "react-router-dom";
import TuneIcon from "@mui/icons-material/Tune";
import { Admin, CustomRoutes, Layout, Menu, Resource, usePermissions, type LayoutProps } from "react-admin";
import { listModels } from "./api";
import authProvider from "./authProvider";
import { createDataProvider } from "./dataProvider";
import InboxPage from "./InboxPage";
import { screens } from "./resources";
import { resourceName, type EntityMeta, type ModelMeta } from "./types";

// The control panel and its editor are loaded only when an administrator opens it.
const ConsolePage = lazy(() => import("../console/ConsolePage"));
const CalendarPage = lazy(() => import("./CalendarPage"));

const AppMenu = () => {
  const { permissions } = usePermissions();
  return (
    <Menu>
      <Menu.DashboardItem primaryText="Hộp việc" />
      <Menu.ResourceItems />
      {Array.isArray(permissions) && permissions.includes("ADMIN") && (
        <Menu.Item to="/console" primaryText="Bảng điều khiển" leftIcon={<TuneIcon />} />
      )}
    </Menu>
  );
};

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
      <CustomRoutes>
        <Route
          path="/console/*"
          element={
            <Suspense fallback={<p className="section-subtitle">Đang tải...</p>}>
              <ConsolePage />
            </Suspense>
          }
        />
        <Route
          path="/calendar/:resource"
          element={
            <Suspense fallback={<p className="section-subtitle">Đang tải...</p>}>
              <CalendarPage byResource={byResource} />
            </Suspense>
          }
        />
      </CustomRoutes>
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
