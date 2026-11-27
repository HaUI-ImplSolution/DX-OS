// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it, vi } from "vitest";

// The screens read the browser address through the sign-in module when they load.
vi.mock("../shell/auth", () => ({ getToken: async () => null, clearSession: () => undefined }));

import { confirmLabel, flowDetail } from "./PlanPanel";

describe("confirmLabel", () => {
  it("asks for a backup when data would be lost", () => {
    expect(confirmLabel("LOSS", true, true, true, true)).toBe("Tôi hiểu dữ liệu sẽ mất và đã sao lưu");
  });

  it("lists every part of the change the person has to accept", () => {
    expect(confirmLabel("MIGRATION", true, true, true, true)).toBe(
      "Tôi xác nhận chuyển dữ liệu và trả lại quyền cho người đang giữ vai và mở rộng quyền của vai và sửa cấu trúc bảng cho khớp mô hình",
    );
  });

  it("falls back to moving data when no part is named", () => {
    expect(confirmLabel("MIGRATION", false, false, false, false)).toBe("Tôi xác nhận chuyển dữ liệu");
  });
});

describe("flowDetail", () => {
  it("translates the parts the backend writes in English and joins them again", () => {
    expect(flowDetail("states added: REVIEW; actions removed: reject; steps of existing actions")).toBe(
      "Thêm trạng thái: REVIEW; Bỏ hành động: reject; Đổi nơi đi, nơi đến hoặc người làm của hành động có sẵn",
    );
  });

  it("leaves a part it does not know", () => {
    expect(flowDetail("something new")).toBe("something new");
  });
});
