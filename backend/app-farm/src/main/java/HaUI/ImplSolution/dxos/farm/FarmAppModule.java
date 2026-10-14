// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm;

import HaUI.ImplSolution.dxos.core.api.app.AppMenuItem;
import HaUI.ImplSolution.dxos.core.api.app.AppModule;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceType;
import HaUI.ImplSolution.dxos.farm.service.ApprovalCaseTypes;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Declares the Farm app: a rice cooperative whose members are farming households.
@Component
public class FarmAppModule implements AppModule {

  public static final String ID = "farm";
  public static final String HOUSEHOLD = "HOUSEHOLD";
  public static final String GPS_MAX_ACCURACY_SETTING = "gpsMaxAccuracyMeters";

  private final BigDecimal gpsMaxAccuracyMeters;

  public FarmAppModule(
      @Value("${app.gps-max-accuracy-meters:30}") BigDecimal gpsMaxAccuracyMeters) {
    this.gpsMaxAccuracyMeters = gpsMaxAccuracyMeters;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "DX-Farm";
  }

  @Override
  public List<AppMenuItem> menu() {
    Set<String> staff = Set.of(FarmSecurityPolicies.ENGINEER, FarmSecurityPolicies.ADMIN);
    return List.of(new AppMenuItem("demo", "Luồng demo", "/", Set.of()),
        new AppMenuItem("requests", "Yêu cầu", "/requests", staff),
        new AppMenuItem("households", "Nông hộ", "/households", Set.of(FarmSecurityPolicies.ADMIN)),
        new AppMenuItem("queue", "Hàng đợi chẩn đoán", "/queue", staff),
        new AppMenuItem("knowledge", "Tài liệu", "/knowledge", staff));
  }

  @Override
  public List<WorkspaceType> workspaceTypes() {
    return List.of(new WorkspaceType(HOUSEHOLD, "H", "Nông hộ"));
  }

  @Override
  public Map<String, String> settings() {
    return Map.of(GPS_MAX_ACCURACY_SETTING,
        gpsMaxAccuracyMeters.stripTrailingZeros().toPlainString());
  }

  @Override
  public List<String> limitedCaseTypes() {
    return ApprovalCaseTypes.FARMER_REQUESTS;
  }
}
