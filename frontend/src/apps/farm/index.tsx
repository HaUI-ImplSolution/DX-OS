// SPDX-License-Identifier: Apache-2.0
import type { AppUiModule, CaseUi } from "../../shell";
import { CaseDetail, Requests } from "../../shell";
import DecisionActions from "./components/DecisionActions";
import FarmRequestDetails from "./components/FarmRequestDetails";
import { FARM_ERROR_MESSAGES } from "./errorMessages";
import { CASE_TYPE_LABEL } from "./labels";
import DemoFlow from "./pages/DemoFlow";
import Households from "./pages/Households";
import Knowledge from "./pages/Knowledge";
import Queue from "./pages/Queue";
import type { ApprovalRequest, Role } from "./types";

const caseUi: CaseUi = {
  title: "Yêu cầu",
  subtitle: "Thửa, vụ mùa và chẩn đoán đang chờ xử lý, cũ nhất ở trên",
  caseTypeLabels: CASE_TYPE_LABEL,
  statusOptions: ["PENDING", "APPROVED", "REJECTED", "CANCELLED"],
  unit: {
    urlKey: "household",
    filterParam: "householdCode",
    filterPlaceholder: "Mã hộ (vd. H0042)",
    columnLabel: "Hộ",
    detailPrefix: "hộ",
    display: (request) => ({
      code: (request.householdCode as string | null) ?? null,
      person: (request.farmerName as string | null) ?? null,
    }),
  },
  renderDetails: (request) => <FarmRequestDetails request={request as ApprovalRequest} />,
  renderActions: (request, { role, onDone, onError }) => (
    <DecisionActions
      request={request as ApprovalRequest}
      role={role as Role}
      onDone={onDone}
      onError={onError}
    />
  ),
};

const farmModule: AppUiModule = {
  tagline: "Quản lý thửa ruộng, vụ mùa và chẩn đoán sâu bệnh của hợp tác xã",
  roleLabels: {
    FARMER: "Nông dân",
    ENGINEER: "Kỹ sư",
    ADMIN: "Quản trị viên",
  },
  errorMessages: FARM_ERROR_MESSAGES,
  blockedRoles: {
    FARMER: {
      title: "Vui lòng dùng ứng dụng di động",
      message: (fullName) =>
        `Tài khoản nông dân (${fullName}) chỉ sử dụng được trên ứng dụng DX-Farm di động. Trang web dành cho kỹ sư và quản trị viên.`,
    },
  },
  routes: [
    {
      path: "/",
      render: ({ user, onError }) => <DemoFlow role={user.role as Role} onError={onError} />,
    },
    {
      path: "/requests",
      render: ({ user, onError }) => <Requests role={user.role} onError={onError} ui={caseUi} />,
    },
    {
      path: "/requests/:caseId",
      menuPath: "/requests",
      render: ({ user, onError }) => <CaseDetail role={user.role} onError={onError} ui={caseUi} />,
    },
    {
      path: "/households",
      render: ({ onError }) => <Households onError={onError} />,
    },
    {
      path: "/knowledge",
      render: ({ onError }) => <Knowledge onError={onError} />,
    },
    {
      path: "/queue",
      render: ({ user, onError }) => <Queue role={user.role as Role} onError={onError} />,
    },
  ],
};

export default farmModule;
