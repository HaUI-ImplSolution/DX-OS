// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService;
import HaUI.ImplSolution.dxos.engine.event.AdminEventPublisher;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.engine.users.UserAdminService;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Who may administer what: the platform administrator everything, a project administrator only the
// accounts and models of their projects, anyone else nothing.
@Testcontainers
class ProjectAdministrationTest {

  private static final Model SHOP =
      ModelParser.parse(
          """
          format: 1
          name: shop
          roles:
            - name: CLERK
              staff: true
          units:
            - name: NORTH
              label: Phòng Bắc
          entities:
            order:
              label: Đơn hàng
              fields:
                title: {type: text, label: Tiêu đề}
                department: {type: unit, label: Đơn vị}
              access: {CLERK: all}
          """);

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static JdbcTemplate jdbc;

  private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
  private final ModelRegistry registry = mock(ModelRegistry.class);
  private final UserContext user = mock(UserContext.class);
  private ProjectService projects;
  private UserAdminService users;
  private AppClientService apps;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas("engine")
        .locations("classpath:db/migration/engine")
        .table("flyway_schema_history_engine")
        .load()
        .migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
  }

  @BeforeEach
  void wire() {
    jdbc.execute(
        "TRUNCATE engine.project, engine.project_member, engine.model_project,"
            + " engine.client_project CASCADE");
    when(registry.find("shop")).thenReturn(Optional.of(SHOP));
    when(registry.all()).thenReturn(List.of(SHOP));
    ProjectLinks links = new ProjectLinks(jdbc);
    ProjectMembers members = new ProjectMembers(jdbc);
    UserScope scope = new UserScope(user, members, links);
    AuditRecorder audit = mock(AuditRecorder.class);
    AdminEventPublisher events = mock(AdminEventPublisher.class);
    apps = new AppClientService(keycloak, registry, links, user, audit, events);
    projects =
        new ProjectService(
            jdbc, links, members, scope, keycloak, apps, registry, user, audit, events);
    users = new UserAdminService(keycloak, registry, user, audit, events, scope, members, links);
    account("anna", "An Binh");
    account("binh", "Binh Chu");
    administrator();
  }

  private void account(String id, String name) {
    when(keycloak.findAccount(id))
        .thenReturn(
            Optional.of(new KeycloakAdminClient.Account(id, id, name, id + "@example.test", true)));
  }

  private void administrator() {
    when(user.currentUserId()).thenReturn("root");
    when(user.hasRole("ADMIN")).thenReturn(true);
  }

  private void signIn(String id) {
    when(user.currentUserId()).thenReturn(id);
    when(user.hasRole("ADMIN")).thenReturn(false);
  }

  private void salesProjectAdministeredByAnna() {
    projects.create(new ProjectService.NewProject("sales", "Bán hàng", " "));
    projects.setMember("sales", "anna", new ProjectService.MemberLevel("ADMIN"));
    projects.setModelProject("shop", new ProjectService.Link("sales"));
  }

  @Test
  void aProjectGathersModelsAndMembers() {
    salesProjectAdministeredByAnna();

    ProjectService.ProjectView view = projects.get("sales");

    assertEquals(List.of("shop"), view.models());
    assertEquals(1, view.memberCount());
    assertNull(view.description());
    assertEquals("Bán", projects.update("sales", change("Bán")).label());
    assertThrows(InvalidStateException.class, () -> projects.remove("sales"));
    projects.setModelProject("shop", new ProjectService.Link(null));
    projects.remove("sales");
    assertTrue(projects.list().isEmpty());
  }

  private static ProjectService.ChangedProject change(String label) {
    return new ProjectService.ChangedProject(label, "mô tả");
  }

  @Test
  void aProjectNeedsAFreeAndValidName() {
    projects.create(new ProjectService.NewProject("sales", null, null));

    assertThrows(
        InvalidStateException.class,
        () -> projects.create(new ProjectService.NewProject("sales", null, null)));
    assertThrows(
        ModelValidationException.class,
        () -> projects.create(new ProjectService.NewProject("Sales!", null, null)));
    assertThrows(ResourceNotFoundException.class, () -> projects.get("none"));
    assertThrows(ResourceNotFoundException.class, () -> projects.update("none", change("x")));
    assertThrows(
        ResourceNotFoundException.class,
        () -> projects.setModelProject("shop", new ProjectService.Link("none")));
  }

  @Test
  void aProjectAdministratorManagesMembersButNoAdministrators() {
    salesProjectAdministeredByAnna();
    signIn("anna");

    projects.setMember("sales", "binh", new ProjectService.MemberLevel("MEMBER"));

    assertEquals(2, projects.members("sales").size());
    assertThrows(
        AccessDeniedException.class,
        () -> projects.setMember("sales", "binh", new ProjectService.MemberLevel("ADMIN")));
    assertThrows(AccessDeniedException.class, () -> projects.removeMember("sales", "anna"));
    projects.removeMember("sales", "binh");
    assertEquals(1, projects.members("sales").size());
    assertEquals("sales", projects.mine().projects().get(0).name());
  }

  @Test
  void aProjectAdministratorStaysInsideTheirProjects() {
    salesProjectAdministeredByAnna();
    projects.create(new ProjectService.NewProject("other", "Khác", null));
    signIn("anna");

    assertThrows(AccessDeniedException.class, () -> projects.members("other"));
    assertThrows(AccessDeniedException.class, () -> users.get("binh"));
    assertThrows(AccessDeniedException.class, () -> users.grantRole("binh", "shop", "CLERK"));
    assertThrows(AccessDeniedException.class, () -> users.create(newUser("c", "C D")));
    assertThrows(AccessDeniedException.class, () -> users.setEnabled("binh", false));
    assertThrows(AccessDeniedException.class, () -> users.resetPassword("binh"));
    assertThrows(AccessDeniedException.class, () -> users.addUnit("binh", "NORTH"));
    verify(keycloak, never()).grantClientRole(anyString(), anyString(), anyString());
  }

  @Test
  void aProjectAdministratorGrantsRolesToMembersOnly() {
    salesProjectAdministeredByAnna();
    projects.setMember("sales", "binh", new ProjectService.MemberLevel("MEMBER"));
    signIn("anna");

    users.grantRole("binh", "shop", "CLERK");
    users.revokeRole("binh", "shop", "CLERK");

    verify(keycloak).grantClientRole("binh", "dxos-m-shop", "CLERK");
    assertEquals(2, users.list("", 0, 10).totalElements());
    assertEquals(1, users.list("chu", 0, 10).totalElements());
    assertEquals(List.of("shop"), users.options().models().stream().map(m -> m.model()).toList());
    assertTrue(users.options().units().isEmpty());
  }

  @Test
  void someoneWithoutAProjectAdministersNothing() {
    signIn("binh");

    assertThrows(AccessDeniedException.class, () -> users.list("", 0, 10));
    assertThrows(AccessDeniedException.class, () -> users.options());
  }

  private static UserAdminService.NewUser newUser(String username, String name) {
    return new UserAdminService.NewUser(username, name, null, null);
  }

  @Test
  void aNewAccountGetsATemporaryPassword() {
    when(keycloak.createUser(
            anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString()))
        .thenReturn("id-1");

    UserAdminService.CreatedUser made = users.create(newUser(" chi ", "Chi Le"));

    assertEquals("chi", made.username());
    assertEquals(14, made.temporaryPassword().length());
    assertTrue(made.temporaryPassword().matches(".*[a-z].*"));
    assertTrue(made.temporaryPassword().matches(".*[A-Z].*"));
    assertTrue(made.temporaryPassword().matches(".*[0-9].*"));
    assertThrows(ModelValidationException.class, () -> users.create(newUser(" ", "")));
  }

  @Test
  void theAdministratorChangesAccounts() {
    users.addUnit("binh", "NORTH");
    users.removeUnit("binh", "NORTH");
    users.setEnabled("binh", false);
    users.resetPassword("binh");

    verify(keycloak).addUserToGroup("binh", "NORTH");
    verify(keycloak).setEnabled("binh", false);
    assertThrows(ResourceNotFoundException.class, () -> users.addUnit("binh", "SOUTH"));
    assertThrows(ResourceNotFoundException.class, () -> users.grantRole("binh", "shop", "NONE"));
    assertThrows(ResourceNotFoundException.class, () -> users.grantRole("binh", "none", "CLERK"));
    assertThrows(ResourceNotFoundException.class, () -> users.setEnabled("ghost", true));
    account("root", "Root");
    assertThrows(InvalidStateException.class, () -> users.setEnabled("root", false));
  }

  @Test
  void theAdministratorSeesRolesAndUnitsOfAnAccount() {
    when(keycloak.clientRolesOf("binh"))
        .thenReturn(Map.of("dxos-m-shop", List.of("CLERK"), "account", List.of("view-profile")));
    when(keycloak.groupsOf("binh")).thenReturn(List.of("NORTH", "unrelated"));
    when(keycloak.searchAccounts(anyString(), anyInt(), anyInt()))
        .thenReturn(new KeycloakAdminClient.Accounts(List.of(), 41));

    UserAdminService.UserDetail detail = users.get("binh");

    assertEquals(List.of("shop"), List.copyOf(detail.roles().keySet()));
    assertEquals(List.of("NORTH"), detail.units());
    assertEquals(3, users.list("", null, null).totalPages());
    assertEquals("NORTH", users.options().units().get(0).name());
  }

  private static KeycloakAdminClient.AppClient app(String role) {
    return new KeycloakAdminClient.AppClient("dxos-app-pos", "shop", role, true);
  }

  @Test
  void anApplicationGetsOneRoleOfAModelAndAProject() {
    projects.create(new ProjectService.NewProject("sales", "Bán hàng", null));
    when(keycloak.createAppClient("dxos-app-pos", "shop", "dxos-m-shop", "CLERK"))
        .thenReturn("secret");
    when(keycloak.findAppClient("dxos-app-pos")).thenReturn(Optional.of(app("CLERK")));
    when(keycloak.appClients()).thenReturn(List.of(app("CLERK")));
    when(keycloak.rotateAppSecret("dxos-app-pos")).thenReturn("next");
    when(keycloak.changeAppRole("dxos-app-pos", "dxos-m-shop", "CLERK")).thenReturn(app("CLERK"));

    AppClientService.CreatedApp made =
        apps.create(new AppClientService.NewApp("pos", "shop", "CLERK", "sales"));

    assertEquals("secret", made.secret());
    assertEquals("sales", apps.get("pos").project());
    assertEquals(1, projects.get("sales").clients().size());
    assertEquals("next", apps.renewSecret("pos").secret());
    assertEquals("CLERK", apps.changeRole("pos", new AppClientService.ChangedRole("CLERK")).role());
    projects.setClientProject("pos", new ProjectService.Link(null));
    assertNull(apps.get("pos").project());
    apps.remove("pos");
    verify(keycloak).deleteAppClient("dxos-app-pos");
  }

  @Test
  void anApplicationIsRefusedForWhatDoesNotExist() {
    for (AppClientService.NewApp request :
        List.of(
            new AppClientService.NewApp("pos", "none", "CLERK", null),
            new AppClientService.NewApp("pos", "shop", "NONE", null),
            new AppClientService.NewApp("pos", "shop", "CLERK", "none"))) {
      assertThrows(ResourceNotFoundException.class, () -> apps.create(request));
    }
    assertThrows(
        ModelValidationException.class,
        () -> apps.create(new AppClientService.NewApp("../x", "", "", null)));
    assertThrows(ResourceNotFoundException.class, () -> apps.get("../x"));
    assertThrows(ResourceNotFoundException.class, () -> apps.remove("pos"));
    verify(keycloak, never()).createAppClient(anyString(), anyString(), anyString(), anyString());
  }
}
