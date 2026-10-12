// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HouseholdWorkspaceDirectory implements WorkspaceDirectory {

  private final HouseholdRepository householdRepository;

  public HouseholdWorkspaceDirectory(HouseholdRepository householdRepository) {
    this.householdRepository = householdRepository;
  }

  @Override
  public Optional<WorkspaceView> findById(Long id) {
    return householdRepository.findById(id).map(HouseholdWorkspaceDirectory::toView);
  }

  @Override
  public Optional<WorkspaceView> findByCode(String code) {
    return householdRepository.findByCode(code).map(HouseholdWorkspaceDirectory::toView);
  }

  @Override
  public Optional<WorkspaceView> findByAccount(String userId) {
    return householdRepository.findByKeycloakUserId(userId)
        .map(HouseholdWorkspaceDirectory::toView);
  }

  @Override
  public Map<Long, WorkspaceView> findByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return householdRepository.findAllById(ids).stream().map(HouseholdWorkspaceDirectory::toView)
        .collect(Collectors.toMap(WorkspaceView::id, Function.identity()));
  }

  private static WorkspaceView toView(Household household) {
    return new WorkspaceView(household.getId(), household.getCode(), household.getFullName(),
        household.getKeycloakUserId());
  }
}
