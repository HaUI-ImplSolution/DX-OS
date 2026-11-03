// SPDX-License-Identifier: Apache-2.0
import { Suspense, lazy, useEffect, useMemo, useState } from "react";
import { Route } from "react-router-dom";
import { Box, ListSubheader, Typography } from "@mui/material";
import EventSeatOutlinedIcon from "@mui/icons-material/EventSeatOutlined";
import FactCheckOutlinedIcon from "@mui/icons-material/FactCheckOutlined";
import HubIcon from "@mui/icons-material/Hub";
import SupportAgentOutlinedIcon from "@mui/icons-material/SupportAgentOutlined";
import TableChartOutlinedIcon from "@mui/icons-material/TableChartOutlined";
import TuneIcon from "@mui/icons-material/Tune";
import { Admin, AppBar, CustomRoutes, Layout, Menu, Resource, TitlePortal, usePermissions, type LayoutProps } from "react-admin";
import { i18nProvider } from "../shell/i18n";
import { darkTheme, lightTheme } from "../shell/theme";
import { listModels } from "./api";
import authProvider from "./authProvider";
import { createDataProvider } from "./dataProvider";
import InboxPage from "./InboxPage";
import { screens } from "./resources";
import { resourceName, type EntityMeta, type ModelMeta } from "./types";

// The control panel and its editor are loaded only when an administrator opens it.
const ConsolePage = lazy(() => import("../console/ConsolePage"));
const CalendarPage = lazy(() => import("./CalendarPage"));

// One icon per process family, so a menu reads at a glance.
const FAMILY_ICONS = {
  approval: <FactCheckOutlinedIcon />,
  work_item: <SupportAgentOutlinedIcon />,
  reservation: <EventSeatOutlinedIcon />,
} as Record<string, JSX.Element>;

const entityIcon = (entity: EntityMeta) => FAMILY_ICONS[entity.process?.family ?? ""] ?? <TableChartOutlinedIcon />;

const modelTitle = (name: string) => name.replace(/_/g, " ").replace(/^./, (first) => first.toUpperCase());

function Brand() {
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 1, mr: 2, pr: 2, borderRight: 1, borderColor: "divider" }}>
      <HubIcon color="primary" />
      <Typography sx={{ fontWeight: 800, letterSpacing: 0.3 }} color="primary">
        DX-OS
      </Typography>
    </Box>
  );
}

const AppTopBar = () => (
  <AppBar
    color="inherit"
    elevation={0}
    sx={{ backgroundColor: "background.paper", color: "text.primary", borderBottom: 1, borderColor: "divider" }}
  >
    <Brand />
    <TitlePortal />
  </AppBar>
);

// The entities are grouped under their model, so two models that both have a "Hồ sơ" stay apart.
const makeMenu = (models: ModelMeta[]) =>
  function AppMenu() {
    const { permissions } = usePermissions();
    const reachable = models.map((model) => ({ model, entities: model.entities.filter((entity) => entity.canRead) }));
    return (
      <Menu>
        <Menu.DashboardItem primaryText="Hộp việc" />
        {reachable
          .filter((group) => group.entities.length > 0)
          .map(({ model, entities }) => (
            <Box key={model.name}>
              {models.length > 1 && (
                <ListSubheader disableSticky sx={{ background: "transparent", lineHeight: "32px", mt: 1, fontSize: 11, fontWeight: 700, letterSpacing: 0.6, textTransform: "uppercase" }}>
                  {modelTitle(model.name)}
                </ListSubheader>
              )}
              {entities.map((entity) => (
                <Menu.ResourceItem key={entity.name} name={resourceName(model.name, entity.name)} />
              ))}
            </Box>
          ))}
        {Array.isArray(permissions) && permissions.includes("ADMIN") && (
          <Box>
            <ListSubheader disableSticky sx={{ background: "transparent", lineHeight: "32px", mt: 1, fontSize: 11, fontWeight: 700, letterSpacing: 0.6, textTransform: "uppercase" }}>
              Quản trị
            </ListSubheader>
            <Menu.Item to="/console" primaryText="Bảng điều khiển" leftIcon={<TuneIcon />} />
          </Box>
        )}
      </Menu>
    );
  };

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
  const layout = useMemo(() => {
    const menu = makeMenu(models ?? []);
    return ({ children }: LayoutProps) => (
      <Layout menu={menu} appBar={AppTopBar}>
        {children}
      </Layout>
    );
  }, [models]);

  if (error) return <p className="section-subtitle">{error}</p>;
  if (!models) return <div className="login-screen" />;

  return (
    <Admin
      dataProvider={dataProvider}
      authProvider={authProvider}
      dashboard={() => <InboxPage models={models} />}
      layout={layout}
      theme={lightTheme}
      darkTheme={darkTheme}
      i18nProvider={i18nProvider}
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
              icon={() => entityIcon(entity)}
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
