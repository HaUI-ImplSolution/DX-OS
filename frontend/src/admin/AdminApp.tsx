// SPDX-License-Identifier: Apache-2.0
import { Suspense, lazy, useEffect, useMemo, useState } from "react";
import { Route } from "react-router-dom";
import { Box, ListItemIcon, ListItemText, ListSubheader, MenuItem, Typography } from "@mui/material";
import ManageAccountsOutlinedIcon from "@mui/icons-material/ManageAccountsOutlined";
import EventSeatOutlinedIcon from "@mui/icons-material/EventSeatOutlined";
import FactCheckOutlinedIcon from "@mui/icons-material/FactCheckOutlined";
import HubIcon from "@mui/icons-material/Hub";
import SupportAgentOutlinedIcon from "@mui/icons-material/SupportAgentOutlined";
import TableChartOutlinedIcon from "@mui/icons-material/TableChartOutlined";
import TuneIcon from "@mui/icons-material/Tune";
import {
  Admin,
  AppBar,
  CustomRoutes,
  Layout,
  Logout,
  Menu,
  Resource,
  TitlePortal,
  UserMenu,
  usePermissions,
  type LayoutProps,
} from "react-admin";
import { ACCOUNT_URL } from "../shell/auth";
import { i18nProvider } from "../shell/i18n";
import LanguageSwitch from "../shell/LanguageSwitch";
import { darkTheme, lightTheme } from "../shell/theme";
import { listModels } from "./api";
import authProvider from "./authProvider";
import { createDataProvider } from "./dataProvider";
import InboxPage from "./InboxPage";
import LiveRefresh from "./LiveRefresh";
import { uiIcon } from "./uiIcons";
import { screens } from "./resources";
import { resourceName, type EntityMeta, type ModelMeta } from "./types";
import { assistantAvailable } from "../assistant/api";
import { t } from "../shell/t";

// The control panel and its editor are loaded only when an administrator opens it.
const ConsolePage = lazy(() => import("../console/ConsolePage"));
const CalendarPage = lazy(() => import("./CalendarPage"));
const StatsPage = lazy(() => import("./StatsPage"));
const AssistantPage = lazy(() => import("../assistant/AssistantPage"));

// One icon per process family, so a menu reads at a glance.
const FAMILY_ICONS = {
  approval: <FactCheckOutlinedIcon />,
  work_item: <SupportAgentOutlinedIcon />,
  reservation: <EventSeatOutlinedIcon />,
} as Record<string, JSX.Element>;

const entityIcon = (entity: EntityMeta) =>
  uiIcon(entity.ui?.icon) ?? FAMILY_ICONS[entity.process?.family ?? ""] ?? <TableChartOutlinedIcon />;

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

// Password, OTP and sessions are Keycloak's own page, which opens in a new tab.
const AccountItem = () => (
  <MenuItem component="a" href={ACCOUNT_URL} target="_blank" rel="noopener noreferrer">
    <ListItemIcon>
      <ManageAccountsOutlinedIcon fontSize="small" />
    </ListItemIcon>
    <ListItemText>{t("Tài khoản của tôi")}</ListItemText>
  </MenuItem>
);

const AppTopBar = () => (
  <AppBar
    userMenu={
      <UserMenu>
        <AccountItem />
        <Logout />
      </UserMenu>
    }
    color="inherit"
    elevation={0}
    sx={{ backgroundColor: "background.paper", color: "text.primary", borderBottom: 1, borderColor: "divider" }}
  >
    <Brand />
    <TitlePortal />
    <LanguageSwitch />
  </AppBar>
);

// The entities that name no group come first, then each named group in the order it first appears.
function menuGroups(entities: EntityMeta[]) {
  const groups: { group: string | null; items: EntityMeta[] }[] = [{ group: null, items: [] }];
  for (const entity of entities) {
    const name = entity.ui?.menu?.group ?? null;
    let found = groups.find((candidate) => candidate.group === name);
    if (!found) {
      found = { group: name, items: [] };
      groups.push(found);
    }
    found.items.push(entity);
  }
  return groups.filter((group) => group.items.length > 0);
}

// The entities are grouped under their model, so two models that both have a "Hồ sơ" stay apart.
const makeMenu = (models: ModelMeta[], assistantEnabled: boolean) =>
  function AppMenu() {
    const { permissions } = usePermissions();
    const reachable = models.map((model) => ({
      model,
      // The model file may order the entities and put some of them under a heading of their own.
      entities: model.entities
        .filter((entity) => entity.canRead)
        .map((entity, position) => ({ entity, position }))
        .sort((a, b) => (a.entity.ui?.menu?.order ?? 0) - (b.entity.ui?.menu?.order ?? 0) || a.position - b.position)
        .map(({ entity }) => entity),
    }));
    return (
      <Menu>
        <Menu.DashboardItem primaryText={t("Hộp việc")} />
        {assistantEnabled && <Menu.Item to="/assistant" primaryText={t("Trợ lý cá nhân")} leftIcon={<SupportAgentOutlinedIcon />} />}
        {reachable
          .filter((group) => group.entities.length > 0)
          .map(({ model, entities }) => (
            <Box key={model.name}>
              {models.length > 1 && (
                <ListSubheader disableSticky sx={{ background: "transparent", lineHeight: "32px", mt: 1, fontSize: 11, fontWeight: 700, letterSpacing: 0.6, textTransform: "uppercase" }}>
                  {modelTitle(model.name)}
                </ListSubheader>
              )}
              {menuGroups(entities).map(({ group, items }) => (
                <Box key={group ?? ""}>
                  {group && (
                    <ListSubheader disableSticky sx={{ background: "transparent", lineHeight: "28px", pl: 3, fontSize: 11, fontWeight: 600 }}>
                      {group}
                    </ListSubheader>
                  )}
                  {items.map((entity) => (
                    <Menu.ResourceItem key={entity.name} name={resourceName(model.name, entity.name)} />
                  ))}
                </Box>
              ))}
            </Box>
          ))}
        {Array.isArray(permissions) && permissions.includes("ADMIN") && (
          <Box>
            <ListSubheader disableSticky sx={{ background: "transparent", lineHeight: "32px", mt: 1, fontSize: 11, fontWeight: 700, letterSpacing: 0.6, textTransform: "uppercase" }}>
              {t("Quản trị")}
            </ListSubheader>
            <Menu.Item to="/console" primaryText={t("Bảng điều khiển")} leftIcon={<TuneIcon />} />
          </Box>
        )}
      </Menu>
    );
  };

// Reads the metadata of the applied models and builds one resource per entity the user can reach.
export default function AdminApp() {
  const [models, setModels] = useState<ModelMeta[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [assistantEnabled, setAssistantEnabled] = useState(false);

  useEffect(() => {
    let active = true;
    void assistantAvailable().then(enabled => { if (active) setAssistantEnabled(enabled); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    listModels().then(setModels, (e: unknown) => setError(e instanceof Error ? e.message : t("Không đọc được metadata")));
  }, []);

  const byResource = useMemo(() => {
    const map: Record<string, EntityMeta> = {};
    models?.forEach((model) => model.entities.forEach((entity) => (map[resourceName(model.name, entity.name)] = entity)));
    return map;
  }, [models]);
  const dataProvider = useMemo(() => createDataProvider((resource) => byResource[resource]), [byResource]);
  const layout = useMemo(() => {
    const menu = makeMenu(models ?? [], assistantEnabled);
    return ({ children }: LayoutProps) => (
      <Layout menu={menu} appBar={AppTopBar}>
        <LiveRefresh />
        {children}
      </Layout>
    );
  }, [models, assistantEnabled]);

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
        <Route path="/assistant" element={<Suspense fallback={<p className="section-subtitle">{t("Đang tải...")}</p>}><AssistantPage /></Suspense>} />
        <Route
          path="/console/*"
          element={
            <Suspense fallback={<p className="section-subtitle">{t("Đang tải...")}</p>}>
              <ConsolePage />
            </Suspense>
          }
        />
        <Route
          path="/calendar/:resource"
          element={
            <Suspense fallback={<p className="section-subtitle">{t("Đang tải...")}</p>}>
              <CalendarPage byResource={byResource} />
            </Suspense>
          }
        />
        <Route
          path="/stats/:resource"
          element={
            <Suspense fallback={<p className="section-subtitle">{t("Đang tải...")}</p>}>
              <StatsPage models={models} />
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
