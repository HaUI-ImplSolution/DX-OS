// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab;

import HaUI.ImplSolution.dxos.core.api.app.AppMenuItem;
import HaUI.ImplSolution.dxos.core.api.app.AppModule;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceType;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

// Declares the Lab app: IT operations of a faculty for its students, lecturers and technicians.
@Component
public class LabAppModule implements AppModule {

  public static final String ID = "lab";
  public static final String CLASS = "CLASS";
  public static final String DEPARTMENT = "DEPARTMENT";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "DX-Lab HaUI";
  }

  @Override
  public List<AppMenuItem> menu() {
    Set<String> staff = Set.of(LabSecurityPolicies.TECHNICIAN, LabSecurityPolicies.ADMIN);
    return List.of(new AppMenuItem("home", "Tổng quan", "/", Set.of()),
        new AppMenuItem("tickets", "Yêu cầu hỗ trợ", "/tickets", Set.of()),
        new AppMenuItem("loans", "Mượn thiết bị", "/device-loans", Set.of()),
        new AppMenuItem("bookings", "Đặt phòng", "/room-bookings", Set.of()),
        new AppMenuItem("knowledge", "Tri thức", "/knowledge", Set.of()),
        new AppMenuItem("queue", "Hàng đợi xử lý", "/queue", staff),
        new AppMenuItem("assets", "Phòng và thiết bị", "/assets", staff),
        new AppMenuItem("stats", "Thống kê", "/stats", staff),
        new AppMenuItem("catalog", "Danh mục", "/catalog", Set.of(LabSecurityPolicies.ADMIN)));
  }

  @Override
  public List<WorkspaceType> workspaceTypes() {
    return List.of(new WorkspaceType(CLASS, "L", "Lớp"),
        new WorkspaceType(DEPARTMENT, "B", "Bộ môn"));
  }
}
