// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.lab.model.DeviceLoan;
import HaUI.ImplSolution.dxos.lab.repository.DeviceLoanRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Describes device loans for the work queue.
@Component
@Transactional(readOnly = true)
public class DeviceLoanSummaryProvider implements CaseSummaryProvider {

  private final DeviceLoanRepository loanRepository;

  public DeviceLoanSummaryProvider(DeviceLoanRepository loanRepository) {
    this.loanRepository = loanRepository;
  }

  @Override
  public String entityType() {
    return LabCaseTypes.DEVICE_LOAN_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return loanRepository.findByIdIn(entityIds).stream()
        .collect(Collectors.toMap(DeviceLoan::getId, DeviceLoanSummaryProvider::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    return loanRepository.findByWorkspaceId(workspaceId).stream().map(DeviceLoan::getId).toList();
  }

  private static CaseSummary summary(DeviceLoan loan) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("device", loan.getDevice().getAssetCode());
    details.put("deviceType", loan.getDevice().getDeviceType());
    details.put("purpose", loan.getPurpose());
    details.put("dueDate", loan.getDueDate());
    details.put("borrowerRole", loan.getBorrowerRole());
    details.put("rejectionReason", loan.getRejectionReason());
    return new CaseSummary("%s #%d".formatted(loan.getDevice().getAssetCode(), loan.getId()),
        loan.getWorkspaceId(), loan.getBorrowerName(), details);
  }
}
