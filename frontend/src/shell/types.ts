export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  errorCode?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// A status is a string: each case type declares its own set.
export type CaseStatus = string;

export type Tone = "neutral" | "success" | "warning" | "danger";

// A case in the approval inbox. Fields an app adds to the summary stay readable as unknown.
export interface ApprovalRequest {
  caseId: number;
  caseType: string;
  status: CaseStatus;
  title: string;
  details: Record<string, string | number | boolean | null>;
  createdAt: string;
  assignedTo: string | null;
  assignedToName: string | null;
  updatedAt: string;
  [extra: string]: unknown;
}

export interface WorkflowEvent {
  id: number;
  action: string;
  fromStatus: string | null;
  toStatus: string;
  actorUserId: string | null;
  actorDisplayName: string | null;
  reason: string | null;
  occurredAt: string;
  metadata: Record<string, unknown> | null;
}

export interface ApprovalRequestDetail<T extends ApprovalRequest = ApprovalRequest> {
  request: T;
  events: WorkflowEvent[];
}

export type ApprovalRequestFilter = Record<string, string | number | boolean | undefined>;

export interface KnowledgeSource {
  sourceId: string;
  title: string;
  organization: string | null;
  url: string | null;
  documentUrl?: string | null;
  license: string | null;
  reviewStatus: string | null;
}

export interface KnowledgePassage {
  content: string;
  documentTitle: string;
  score: number;
  sources: KnowledgeSource[];
}

export interface KnowledgeAnswer {
  question: string;
  passages: KnowledgePassage[];
}

export interface MenuEntry {
  id: string;
  label: string;
  path: string;
  // Empty means every signed-in user.
  roles: string[];
}

// What GET /api/v1/app returns for the app the backend is running.
export interface AppManifest {
  id: string;
  name: string;
  roles: string[];
  menu: MenuEntry[];
  workspaceTypes: string[];
}

export interface Notification {
  id: number;
  type: string;
  title: string;
  body: string | null;
  relatedEntityType: string | null;
  relatedEntityRef: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface Workspace {
  id: number;
  code: string;
  type: string;
  name: string;
  phone: string | null;
  address: string | null;
  accountLinked: boolean;
  attributes: Record<string, string> | null;
  createdAt: string;
}

export interface WorkspaceCreateRequest {
  type: string;
  name: string;
  phone?: string;
  address?: string;
  username?: string;
  email?: string;
  accountRole?: string;
}

export interface WorkspaceCreateResult {
  workspace: Workspace;
  username: string | null;
  temporaryPassword: string | null;
}

export interface WorkspaceMember {
  userId: string;
  role: string;
  createdAt: string;
}
