// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.SlaPolicyRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.SlaPolicyResponse;
import HaUI.ImplSolution.dxos.lab.model.SlaPolicy;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import HaUI.ImplSolution.dxos.lab.repository.SlaPolicyRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The time limits tickets are held to, one policy per priority. Changes apply to tickets created
// afterwards; the deadlines of existing tickets stay as they were.
@Service
@Transactional(readOnly = true)
public class SlaPolicyService {

  private final SlaPolicyRepository policyRepository;

  public SlaPolicyService(SlaPolicyRepository policyRepository) {
    this.policyRepository = policyRepository;
  }

  public List<SlaPolicyResponse> list() {
    return policyRepository.findAll().stream().sorted(Comparator.comparing(SlaPolicy::getPriority))
        .map(SlaPolicyResponse::of).toList();
  }

  @Transactional
  public SlaPolicyResponse put(TicketPriority priority, SlaPolicyRequest request) {
    if (request.resolutionMinutes() < request.responseMinutes()) {
      throw new IllegalArgumentException(
          "The resolution time cannot be shorter than the response time");
    }
    SlaPolicy policy = policyRepository.findByPriority(priority)
        .orElseGet(() -> SlaPolicy.builder().priority(priority).build());
    policy.setResponseMinutes(request.responseMinutes());
    policy.setResolutionMinutes(request.resolutionMinutes());
    return SlaPolicyResponse.of(policyRepository.save(policy));
  }

  // Throws when no policy exists for the priority.
  SlaPolicy require(TicketPriority priority) {
    return policyRepository.findByPriority(priority)
        .orElseThrow(() -> new IllegalStateException("No SLA policy for priority " + priority));
  }
}
