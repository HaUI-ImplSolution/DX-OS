// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceCreateCommand;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import HaUI.ImplSolution.dxos.core.exception.KeycloakAdminException;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.core.identity.Workspace;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceCodeGenerator;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceDirectoryService;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceMember;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceMemberRepository;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceRepository;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceService;
import HaUI.ImplSolution.dxos.core.identity.WorkspaceTypes;
import HaUI.ImplSolution.dxos.farm.FarmAppModule;
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
@Import({WorkspaceService.class, WorkspaceDirectoryService.class, WorkspaceTypes.class,
    WorkspaceCodeGenerator.class, FarmAppModule.class, PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkspaceServiceIntegrationTest {

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
  private WorkspaceService workspaceService;
  @Autowired
  private WorkspaceRepository workspaceRepository;
  @Autowired
  private WorkspaceMemberRepository memberRepository;

  private static WorkspaceCreateCommand command(String name, String username) {
    return new WorkspaceCreateCommand(FarmAppModule.HOUSEHOLD, name, "0900000000", "Can Tho", null,
        username, null, "FARMER");
  }

  private long countNamed(String name) {
    return workspaceRepository.findAll().stream().filter(w -> w.getName().equals(name)).count();
  }

  @Test
  void createsWorkspaceOwnedByTheNewAccountAndReturnsATemporaryPassword() {
    String name = "Success " + UUID.randomUUID();
    when(
        keycloakAdminClient.createUser(eq("farmer.ok"), eq(name), any(), anyString(), eq("FARMER")))
        .thenReturn("kc-user-1");

    var created = workspaceService.create(command(name, "farmer.ok"));

    assertThat(created.workspace().ownerUserId()).isEqualTo("kc-user-1");
    assertThat(created.workspace().code()).matches("H\\d{4,}");
    assertThat(created.temporaryPassword()).hasSize(14);
    assertThat(memberRepository.findByUserIdAndRole("kc-user-1", WorkspaceMemberRole.OWNER))
        .hasSize(1);
  }

  @Test
  void keycloakFailureLeavesNoWorkspaceBehind() {
    String name = "Failing " + UUID.randomUUID();
    when(keycloakAdminClient.createUser(any(), eq(name), any(), anyString(), anyString()))
        .thenThrow(new KeycloakAdminException("Keycloak is unreachable"));

    assertThatThrownBy(() -> workspaceService.create(command(name, "farmer.fail")))
        .isInstanceOf(KeycloakAdminException.class);

    assertThat(countNamed(name)).isZero();
    verify(keycloakAdminClient, never()).deleteUser(anyString());
  }

  @Test
  void duplicateUsernameIsReportedAndNothingIsCreated() {
    String name = "Duplicate " + UUID.randomUUID();
    when(keycloakAdminClient.createUser(any(), eq(name), any(), anyString(), anyString()))
        .thenThrow(new InvalidStateException("Username or email already exists"));

    assertThatThrownBy(() -> workspaceService.create(command(name, "taken")))
        .isInstanceOf(InvalidStateException.class);

    assertThat(countNamed(name)).isZero();
  }

  @Test
  void accountCreatedBeforeAFailedSaveIsDeletedAgain() {
    // An account id longer than the column makes the member insert fail after Keycloak succeeded.
    String tooLong = "kc-" + "x".repeat(300);
    String name = "Collision " + UUID.randomUUID();
    when(keycloakAdminClient.createUser(any(), eq(name), any(), anyString(), anyString()))
        .thenReturn(tooLong);

    assertThatThrownBy(() -> workspaceService.create(command(name, "farmer.collision")))
        .isInstanceOf(RuntimeException.class);

    assertThat(countNamed(name)).isZero();
    verify(keycloakAdminClient).deleteUser(tooLong);
  }

  @Test
  void linkingAnAccountToAWorkspaceWithoutOwnerWorksOnce() {
    Workspace legacy = workspaceRepository.save(Workspace.builder().code("L-" + UUID.randomUUID())
        .type(FarmAppModule.HOUSEHOLD).name("Legacy").build());
    String account = "kc-link-" + UUID.randomUUID();
    when(keycloakAdminClient.userExists(account)).thenReturn(true);
    when(keycloakAdminClient.userExists("another")).thenReturn(true);

    workspaceService.linkOwner(legacy.getCode(), account, null);

    assertThat(memberRepository.findByUserIdAndRole(account, WorkspaceMemberRole.OWNER)).hasSize(1);
    assertThatThrownBy(() -> workspaceService.linkOwner(legacy.getCode(), "another", null))
        .isInstanceOf(InvalidStateException.class);
  }
}
