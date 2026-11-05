// SPDX-License-Identifier: Apache-2.0
import YAML from "yaml";

// The names the backend uses for what a plan changes, what a step does and how a run ended, in Vietnamese.
export const FAMILY_LABEL: Record<string, string> = {
  approval: "Chuỗi duyệt",
  work_item: "Công việc cần xử lý",
  reservation: "Chiếm dụng theo thời gian",
  none: "Không có quy trình",
};

export const CHANGE_KIND: Record<string, string> = {
  ADD_ROLE: "Thêm vai",
  ADD_UNIT: "Thêm đơn vị",
  ADD_ENTITY: "Thêm thực thể",
  DROP_ENTITY: "Xóa thực thể",
  ADD_FIELD: "Thêm trường",
  DROP_FIELD: "Xóa trường",
  RENAME_FIELD: "Đổi tên trường",
  ADD_REQUIRED: "Đặt bắt buộc nhập",
  DROP_REQUIRED: "Bỏ bắt buộc nhập",
  ADD_UNIQUE: "Đặt không trùng",
  DROP_UNIQUE: "Bỏ không trùng",
  CHANGE_OPTIONS: "Đổi danh sách lựa chọn",
  CHANGE_LABEL: "Đổi nhãn hiển thị",
  CHANGE_DEFAULT: "Đổi giá trị mặc định",
  CHANGE_ACCESS: "Đổi quyền truy cập",
  CHANGE_OWNER_VIA: "Đổi chủ sở hữu theo bản ghi cha",
  ADD_PROCESS: "Thêm quy trình",
  DROP_PROCESS: "Bỏ quy trình",
  CHANGE_HANDLER: "Đổi vai xử lý",
  CHANGE_SELF_DECISION: "Đổi quyền tự quyết định",
  CHANGE_COMPLETION: "Đổi hành động hoàn tất",
  CHANGE_BOOKING: "Đổi khối đặt chỗ",
  CHANGE_DEADLINE: "Đổi hạn xử lý",
  CHANGE_AUTO_CLOSE: "Đổi thời gian tự đóng hồ sơ",
  REPAIR_SCHEMA: "Sửa cấu trúc bảng",
  REPAIR_CLIENT: "Tạo lại client của mô hình trong Keycloak",
  REPAIR_ROLE: "Tạo lại vai trong Keycloak",
  REPAIR_UNIT: "Tạo lại nhóm đơn vị trong Keycloak",
};

export const RULE: Record<string, string> = {
  REQUIRED: "bắt buộc nhập",
  NEW_REQUIRED: "trường mới bắt buộc nhập",
  UNIQUE: "không được trùng",
  OPTIONS: "chỉ nhận các lựa chọn mới",
};

export const STEP_KIND: Record<string, string> = {
  CHANGE_SCHEMA: "Đổi cấu trúc bảng",
  ENSURE_CLIENT: "Tạo client của mô hình trong Keycloak",
  ENSURE_ROLE: "Tạo vai của mô hình trong Keycloak",
  DROP_FILES: "Xóa tệp đính kèm của thực thể",
  ENSURE_UNIT: "Tạo nhóm đơn vị trong Keycloak",
  SAVE_VERSION: "Lưu phiên bản mô hình",
  RENAME_FIELD: "Đổi tên cột giữ dữ liệu",
  DROP_SCHEMA: "Xóa schema của mô hình",
  DROP_CLIENT: "Xóa client và vai của mô hình trong Keycloak",
};

export const RUN_STATUS: Record<string, string> = {
  running: "Đang chạy",
  done: "Xong",
  failed: "Lỗi",
  applied: "Đã áp dụng",
  unchanged: "Không đổi",
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
