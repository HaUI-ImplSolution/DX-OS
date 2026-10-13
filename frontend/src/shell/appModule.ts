import type { ReactNode } from "react";
import type { LabelRegistration } from "./labels";
import type { AppManifest, ApprovalRequest } from "./types";

export interface AppUser {
  id: string;
  username: string;
  fullName: string;
  // The strongest role of the account among the roles the app defines.
  role: string;
}

// What the shell hands to every page of an app.
export interface AppContext {
  user: AppUser;
  manifest: AppManifest;
  onError: (message: string) => void;
}

export interface AppRoute {
  path: string;
  // Path of the menu entry whose roles guard this route; defaults to the route path.
  menuPath?: string;
  render: (context: AppContext) => ReactNode;
}

// How the shared approval inbox shows the cases of an app.
export interface CaseUi {
  title: string;
  subtitle: string;
  caseTypeLabels: Record<string, string>;
  statusOptions: string[];
  // The unit a case belongs to (household, class...): filter, column and detail line.
  unit: {
    urlKey: string;
    filterParam: string;
    filterPlaceholder: string;
    columnLabel: string;
    detailPrefix: string;
    display: (request: ApprovalRequest) => { code: string | null; person: string | null };
  };
  renderDetails: (request: ApprovalRequest) => ReactNode;
  renderActions: (
    request: ApprovalRequest,
    context: { role: string; onDone: () => void; onError: (message: string) => void },
  ) => ReactNode;
}

// The web module of one app: pages, labels and the messages only that app raises.
export interface AppUiModule {
  tagline: string;
  roleLabels: Record<string, string>;
  routes: AppRoute[];
  labels?: LabelRegistration;
  errorMessages?: Record<string, string>;
  // Roles that cannot use the web client, with the message they see instead of the app.
  blockedRoles?: Record<string, { title: string; message: (fullName: string) => string }>;
}

export type AppModuleLoader = () => Promise<{ default: AppUiModule }>;

// Maps an app id to the function that loads its module.
export type AppRegistry = Record<string, AppModuleLoader>;
