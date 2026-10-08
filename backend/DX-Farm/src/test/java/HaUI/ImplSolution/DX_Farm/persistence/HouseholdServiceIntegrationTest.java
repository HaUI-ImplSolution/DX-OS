// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.DX_Farm.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdCreateRequest;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdLinkRequest;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.KeycloakAdminException;
import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdRepository;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(HouseholdService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HouseholdServiceIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    MigratedDatabase.migrate(POSTGRES);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @MockitoBean
  private KeycloakAdminClient keycloakAdminClient;

  @Autowired
  private HouseholdService householdService;
  @Autowired
  private HouseholdRepository householdRepository;

  private static HouseholdCreateRequest request(String name, String username) {
    return new HouseholdCreateRequest(name, "0900000000", "Can Tho", username, null);
  }

  private long countNamed(String fullName) {
    return householdRepository.findAll().stream().filter(h -> h.getFullName().equals(fullName))
        .count();
  }

  @Test
  void createsHouseholdLinkedToTheNewAccountAndReturnsATemporaryPassword() {
    String name = "Success " + UUID.randomUUID();
    when(keycloakAdminClient.createFarmerUser(eq("farmer.ok"), eq(name), any(), anyString()))
        .thenReturn("kc-user-1");

    var response = householdService.create(request(name, "farmer.ok"));

    assertThat(response.household().accountLinked()).isTrue();
    assertThat(response.household().code()).matches("H\\d{4,}");
    assertThat(response.temporaryPassword()).hasSize(14);
    assertThat(householdRepository.findByKeycloakUserId("kc-user-1")).isPresent();
  }

  @Test
  void keycloakFailureLeavesNoHouseholdBehind() {
    String name = "Failing " + UUID.randomUUID();
    when(keycloakAdminClient.createFarmerUser(any(), eq(name), any(), anyString()))
        .thenThrow(new KeycloakAdminException("Keycloak is unreachable"));

    assertThatThrownBy(() -> householdService.create(request(name, "farmer.fail")))
        .isInstanceOf(KeycloakAdminException.class);

    assertThat(countNamed(name)).isZero();
    verify(keycloakAdminClient, never()).deleteUser(anyString());
  }

  @Test
  void duplicateUsernameIsReportedAndNothingIsCreated() {
    String name = "Duplicate " + UUID.randomUUID();
    when(keycloakAdminClient.createFarmerUser(any(), eq(name), any(), anyString()))
        .thenThrow(new InvalidStateException("Username or email already exists"));

    assertThatThrownBy(() -> householdService.create(request(name, "taken")))
        .isInstanceOf(InvalidStateException.class);

    assertThat(countNamed(name)).isZero();
  }

  @Test
  void accountCreatedBeforeAFailedCommitIsDeletedAgain() {
    // Same full name twice is allowed, so force the commit-time failure with a taken account id.
    String taken = "kc-taken-" + UUID.randomUUID();
    householdRepository.save(Household.builder().code("T-" + UUID.randomUUID()).fullName("Owner")
        .keycloakUserId(taken).build());
    String name = "Collision " + UUID.randomUUID();
    when(keycloakAdminClient.createFarmerUser(any(), eq(name), any(), anyString()))
        .thenReturn(taken);

    assertThatThrownBy(() -> householdService.create(request(name, "farmer.collision")))
        .isInstanceOf(RuntimeException.class);

    assertThat(countNamed(name)).isZero();
    verify(keycloakAdminClient).deleteUser(taken);
  }

  @Test
  void linkingAnAccountToALegacyHouseholdWorksOnce() {
    Household legacy = householdRepository
        .save(Household.builder().code("L-" + UUID.randomUUID()).fullName("Legacy").build());
    String account = "kc-link-" + UUID.randomUUID();
    when(keycloakAdminClient.userExists(account)).thenReturn(true);

    householdService.linkAccount(legacy.getCode(), new HouseholdLinkRequest(account, null));

    assertThat(householdRepository.findByKeycloakUserId(account)).isPresent();
    assertThatThrownBy(() -> householdService.linkAccount(legacy.getCode(),
        new HouseholdLinkRequest("another", null))).isInstanceOf(InvalidStateException.class);
  }
}
