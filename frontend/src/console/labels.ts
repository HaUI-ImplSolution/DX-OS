// SPDX-License-Identifier: Apache-2.0
import YAML from "yaml";
import { t } from "../shell/t";

// The names the backend uses for what a plan changes, what a step does and how a run ended, in the language of the interface.
export const FAMILY_LABEL: Record<string, string> = {
  approval: t("Chuỗi duyệt"),
  work_item: t("Công việc cần xử lý"),
  reservation: t("Chiếm dụng theo thời gian"),
  custom: t("Tự khai báo"),
  none: t("Không có quy trình"),
};

export const CHANGE_KIND: Record<string, string> = {
  ADD_ROLE: t("Thêm vai"),
  ADD_UNIT: t("Thêm đơn vị"),
  ADD_ENTITY: t("Thêm thực thể"),
  DROP_ENTITY: t("Xóa thực thể"),
  ADD_FIELD: t("Thêm trường"),
  DROP_FIELD: t("Xóa trường"),
  RENAME_FIELD: t("Đổi tên trường"),
  ADD_REQUIRED: t("Đặt bắt buộc nhập"),
  DROP_REQUIRED: t("Bỏ bắt buộc nhập"),
  ADD_UNIQUE: t("Đặt không trùng"),
  DROP_UNIQUE: t("Bỏ không trùng"),
  CHANGE_OPTIONS: t("Đổi danh sách lựa chọn"),
  CHANGE_LABEL: t("Đổi nhãn hiển thị"),
  CHANGE_DEFAULT: t("Đổi giá trị mặc định"),
  CHANGE_ACCESS: t("Đổi quyền truy cập"),
  CHANGE_OWNER_VIA: t("Đổi chủ sở hữu theo bản ghi cha"),
  ADD_PROCESS: t("Thêm quy trình"),
  DROP_PROCESS: t("Bỏ quy trình"),
  CHANGE_HANDLER: t("Đổi vai xử lý"),
  CHANGE_FLOW: t("Đổi trạng thái hoặc hành động của quy trình"),
  CHANGE_ACTIONS: t("Đổi nhãn, vai hoặc trường bắt buộc của hành động"),
  CHANGE_SELF_DECISION: t("Đổi quyền tự quyết định"),
  CHANGE_COMPLETION: t("Đổi hành động hoàn tất"),
  CHANGE_BOOKING: t("Đổi khối đặt chỗ"),
  CHANGE_DEADLINE: t("Đổi hạn xử lý"),
  CHANGE_AUTO_CLOSE: t("Đổi thời gian tự đóng hồ sơ"),
  REPAIR_SCHEMA: t("Sửa cấu trúc bảng"),
  REPAIR_CLIENT: t("Tạo lại client của mô hình trong Keycloak"),
  REPAIR_ROLE: t("Tạo lại vai trong Keycloak"),
  REPAIR_UNIT: t("Tạo lại nhóm đơn vị trong Keycloak"),
};

export const RULE: Record<string, string> = {
  REQUIRED: t("bắt buộc nhập"),
  NEW_REQUIRED: t("trường mới bắt buộc nhập"),
  UNIQUE: t("không được trùng"),
  OPTIONS: t("chỉ nhận các lựa chọn mới"),
};

export const STEP_KIND: Record<string, string> = {
  CHANGE_SCHEMA: t("Đổi cấu trúc bảng"),
  ENSURE_CLIENT: t("Tạo client của mô hình trong Keycloak"),
  ENSURE_ROLE: t("Tạo vai của mô hình trong Keycloak"),
  DROP_FILES: t("Xóa tệp đính kèm của thực thể"),
  ENSURE_UNIT: t("Tạo nhóm đơn vị trong Keycloak"),
  DEPLOY_PROCESS: t("Nạp quy trình riêng của mô hình"),
  UNDEPLOY_PROCESS: t("Gỡ quy trình riêng của mô hình"),
  SAVE_VERSION: t("Lưu phiên bản mô hình"),
  RENAME_FIELD: t("Đổi tên cột giữ dữ liệu"),
  DROP_SCHEMA: t("Xóa schema của mô hình"),
  DROP_CLIENT: t("Xóa client và vai của mô hình trong Keycloak"),
};

export const RUN_STATUS: Record<string, string> = {
  running: t("Đang chạy"),
  done: t("Xong"),
  failed: t("Lỗi"),
  applied: t("Đã áp dụng"),
  unchanged: t("Không đổi"),
};

export const label = (map: Record<string, string>, key: string) => map[key] ?? key;

export interface ModelSummary {
  entities: { name: string; label: string; family: string }[];
  roles: number;
  units: number;
}

// Reads what a model file declares, to show it on a card; a file that does not parse has no summary.
export function summarize(content: string): ModelSummary | null {
  try {
    const value = YAML.parse(content) as {
      roles?: unknown[];
      units?: unknown[];
      entities?: Record<string, { label?: string; process?: { family?: string } }>;
    };
    return {
      entities: Object.entries(value.entities ?? {}).map(([name, entity]) => ({
        name,
        label: entity?.label ?? name,
        family: entity?.process?.family ?? "none",
      })),
      roles: value.roles?.length ?? 0,
      units: value.units?.length ?? 0,
    };
  } catch {
    return null;
  }
}
