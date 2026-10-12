// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.core.identity.HouseholdCreateRequest;
import HaUI.ImplSolution.dxos.core.identity.HouseholdCreateResponse;
import HaUI.ImplSolution.dxos.core.identity.HouseholdLinkRequest;
import HaUI.ImplSolution.dxos.core.identity.HouseholdResponse;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.identity.Household;
import HaUI.ImplSolution.dxos.core.identity.HouseholdRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class HouseholdService {

  private static final Logger log = LoggerFactory.getLogger(HouseholdService.class);
  // No look-alike characters (0/O, 1/l/I) so a password read out over the phone is unambiguous.
  private static final String PASSWORD_ALPHABET =
      "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int PASSWORD_LENGTH = 14;

  private final HouseholdRepository householdRepository;
  private final KeycloakAdminClient keycloakAdminClient;
  private final TransactionTemplate transactionTemplate;
  private final SecureRandom random = new SecureRandom();

  public HouseholdService(HouseholdRepository householdRepository,
      KeycloakAdminClient keycloakAdminClient, TransactionTemplate transactionTemplate) {
    this.householdRepository = householdRepository;
    this.keycloakAdminClient = keycloakAdminClient;
    this.transactionTemplate = transactionTemplate;
  }

  @Transactional(readOnly = true)
  public List<HouseholdResponse> listAll() {
    return householdRepository.findAllByOrderByCodeAsc().stream().map(HouseholdService::toResponse)
        .toList();
  }

  // Creates the household and its Keycloak account in one step. The database work and the
  // Keycloak call share one transaction, so a Keycloak failure rolls the household back; if the
  // commit itself fails after Keycloak succeeded, the new account is deleted again.
  public HouseholdCreateResponse create(HouseholdCreateRequest request) {
    String temporaryPassword = generatePassword();
    AtomicReference<String> createdUserId = new AtomicReference<>();
    try {
      Household saved = transactionTemplate.execute(status -> {
        Household household = householdRepository.saveAndFlush(
            Household.builder().code("H%04d".formatted(householdRepository.nextCodeNumber()))
                .fullName(request.fullName().trim()).phone(request.phone())
                .address(request.address()).build());
        String userId = keycloakAdminClient.createFarmerUser(request.username(),
            household.getFullName(), request.email(), temporaryPassword);
        createdUserId.set(userId);
        household.setKeycloakUserId(userId);
        return householdRepository.saveAndFlush(household);
      });
      return new HouseholdCreateResponse(toResponse(saved), request.username(), temporaryPassword);
    } catch (RuntimeException e) {
      String userId = createdUserId.get();
      if (userId != null) {
        try {
          keycloakAdminClient.deleteUser(userId);
        } catch (RuntimeException cleanupFailure) {
          log.error("Could not remove Keycloak user {} after a failed household creation", userId);
        }
      }
      throw e;
    }
  }

  // Attaches an existing Keycloak account to a household that has none (for example one created
  // by the migration from legacy parcels).
  @Transactional
  public HouseholdResponse linkAccount(String code, HouseholdLinkRequest request) {
    Household household = householdRepository.findByCode(code)
        .orElseThrow(() -> new ResourceNotFoundException("Household not found: " + code));
    if (household.getKeycloakUserId() != null) {
      throw new InvalidStateException("Household already has an account: " + code);
    }
    String userId = request.keycloakUserId() != null && !request.keycloakUserId().isBlank()
        ? request.keycloakUserId()
        : keycloakAdminClient.findUserId(request.username().strip()).orElseThrow(
            () -> new ResourceNotFoundException("Keycloak user not found: " + request.username()));
    householdRepository.findByKeycloakUserId(userId).ifPresent(other -> {
      throw new InvalidStateException("Account is already linked to household " + other.getCode());
    });
    if (!keycloakAdminClient.userExists(userId)) {
      throw new ResourceNotFoundException("Keycloak user not found: " + userId);
    }
    household.setKeycloakUserId(userId);
    return toResponse(householdRepository.save(household));
  }

  private String generatePassword() {
    StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
    for (int i = 0; i < PASSWORD_LENGTH; i++) {
      password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
    }
    return password.toString();
  }

  private static HouseholdResponse toResponse(Household household) {
    return new HouseholdResponse(household.getId(), household.getCode(), household.getFullName(),
        household.getPhone(), household.getAddress(), household.getKeycloakUserId() != null,
        household.getCreatedAt());
  }
}
