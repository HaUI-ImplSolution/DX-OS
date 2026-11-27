// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.catalog.CatalogService;
import HaUI.ImplSolution.dxos.engine.data.RecordCodes;
import HaUI.ImplSolution.dxos.engine.event.AdminEventPublisher;
import HaUI.ImplSolution.dxos.engine.event.WebhookTargets;
import HaUI.ImplSolution.dxos.engine.files.AttachmentCleaner;
import HaUI.ImplSolution.dxos.engine.process.ProcessDeployer;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

// Applying model files on PostgreSQL: the plan, the refusals that protect running data, an apply
// that stops half way, and going back. The schema tool and Keycloak stand in as test doubles.
@Testcontainers
class ApplyIntegrationTest {

  private static final String FIRST =
      """
      format: 1
      name: stock
      roles:
        - name: CLERK
          staff: true
        - name: BUYER
      entities:
        order:
          label: Đơn hàng
          fields:
            title: {type: text, label: Tiêu đề, required: true}
            note: {type: text, label: Ghi chú}
          access: {CLERK: all, BUYER: own}
          process: {family: approval, handler: CLERK, deadline: 48h}
      """;

  private static final String MORE_ROLES =
      FIRST.replace("  - name: BUYER\n", "  - name: BUYER\n  - name: AUDITOR\n");

  private static final String NOTE_REQUIRED =
      FIRST.replace("label: Ghi chú}", "label: Ghi chú, required: true}");

  private static final String LONGER_DEADLINE = FIRST.replace("48h", "72h");

  private static final String NOTE_DROPPED =
      FIRST.replace("      note: {type: text, label: Ghi chú}\n", "");

  private static final ApplyService.Actor ADMIN = new ApplyService.Actor("admin", "Admin");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static JdbcTemplate jdbc;

  private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
  private final ProcessDeployer processes = mock(ProcessDeployer.class);
  private ModelRegistry registry;
  private ApplyService service;

  @BeforeAll
  static void migrate() {
    for (String[] part : new String[][] {{"core", "public"}, {"engine", "engine"}}) {
      Flyway.configure()
          .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
          .schemas(part[1])
          .locations("classpath:db/migration/" + part[0])
          .table("flyway_schema_history_" + part[0])
          .baselineOnMigrate(true)
          .baselineVersion("0")
          .load()
          .migrate();
    }
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    jdbc.execute(
        "DO $$ BEGIN CREATE ROLE report_ro; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS engine.act_ru_execution "
            + "(proc_inst_id_ text, business_key_ text)");
  }

  @BeforeEach
  void freshStart() {
    jdbc.execute("DROP SCHEMA IF EXISTS m_stock CASCADE");
    jdbc.execute(
        "TRUNCATE engine.apply_step, engine.apply_run, engine.model_version, engine.record_event,"
            + " engine.record_counter RESTART IDENTITY CASCADE");
    registry = new ModelRegistry(jdbc);
    registry.reload();
    when(keycloak.clientRoleNames(anyString()))
        .thenReturn(Optional.of(List.of("CLERK", "BUYER", "AUDITOR")));
    when(keycloak.groupExists(anyString())).thenReturn(true);
    service = service("");
  }

  private ApplyService service(String faultStep) {
    WebhookTargets webhooks = mock(WebhookTargets.class);
    when(webhooks.missing(any())).thenReturn(Optional.empty());
    return new ApplyService(
        jdbc,
        keycloak,
        registry,
        schemaTool(),
        mock(AttachmentCleaner.class),
        new ObjectMapper(),
        webhooks,
        mock(AdminEventPublisher.class),
        mock(AuditRecorder.class),
        processes,
        new RecordCodes(
            new NamedParameterJdbcTemplate(jdbc),
            Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), BusinessTime.ZONE)),
        new TemplateResolver(mock(CatalogService.class)),
        "",
        faultStep);
  }

  // Creates the tables of a model that has none yet and leaves existing tables as they are.
  private static SchemaTool schemaTool() {
    SchemaTool tool = mock(SchemaTool.class);
    String[] desired = new String[1];
    when(tool.plan(any(), anyString()))
        .thenAnswer(
            call -> {
              Model model = call.getArgument(0);
              desired[0] = call.getArgument(1);
              List<SchemaTool.Statement> statements =
                  hasTables(model)
                      ? List.of()
                      : List.of(
                          new SchemaTool.Statement(
                              desired[0], "table", "create", model.schema() + ".order"));
              return new SchemaTool.Plan("{}", statements);
            });
    doAnswer(
            call -> {
              Model model = call.getArgument(0);
              if (!hasTables(model)) {
                new JdbcTemplate(
                        new DriverManagerDataSource(
                            POSTGRES.getJdbcUrl() + "&currentSchema=" + model.schema(),
                            POSTGRES.getUsername(),
                            POSTGRES.getPassword()))
                    .execute(desired[0]);
              }
              return null;
            })
        .when(tool)
        .apply(any(), any());
    return tool;
  }

  private static boolean hasTables(Model model) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = ?",
            Long.class,
            model.schema())
        > 0;
  }

  private void applyFirst() {
    service.apply(FIRST, ADMIN, null, null);
  }

  private static void order(String note) {
    jdbc.update(
        "INSERT INTO m_stock.\"order\" (title, note, status, created_by) "
            + "VALUES ('A', ?, 'submitted', 'buyer')",
        note);
  }

  private static long versions() {
    return jdbc.queryForObject("SELECT count(*) FROM engine.model_version", Long.class);
  }

  @Test
  void aPlanOfANewModelChangesNothing() {
    ApplyService.Plan plan = service.plan(FIRST);

    assertEquals("create", plan.action());
    assertEquals(0, plan.fromVersion());
    assertEquals(1, plan.version());
    assertTrue(plan.rejections().isEmpty(), plan.rejections().toString());
    assertEquals(0, versions());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'm_stock'",
            Long.class));
    verify(keycloak, never()).ensureClient(anyString());
  }

  @Test
  void anApplySavesTheVersionAndPreparesEveryPart() {
    ApplyService.Applied applied = service.apply(FIRST, ADMIN, null, null);

    assertEquals("applied", applied.status());
    assertEquals(1, applied.version());
    assertTrue(registry.find("stock").isPresent());
    assertFalse(registry.isHeld("stock"));
    verify(keycloak).ensureClient(anyString());
    assertEquals(1, service.models().size());
    assertEquals("done", service.runs("stock").get(0).get("status"));
  }

  @Test
  void theSameFileAgainIsUnchanged() {
    applyFirst();

    assertEquals("unchanged", service.plan(FIRST).action());
    assertEquals("unchanged", service.apply(FIRST, ADMIN, null, null).status());
    assertEquals(1, versions());
  }

  @Test
  void aMissingRoleIsRepaired() {
    applyFirst();
    when(keycloak.clientRoleNames(anyString())).thenReturn(Optional.of(List.of("CLERK")));

    assertEquals("repair", service.plan(FIRST).action());
  }

  @Test
  void aChangeMustNameTheRunningVersion() {
    applyFirst();

    assertThrows(InvalidStateException.class, () -> service.apply(MORE_ROLES, ADMIN, null, null));
    assertThrows(InvalidStateException.class, () -> service.apply(MORE_ROLES, ADMIN, 7, null));
    assertEquals(2, service.apply(MORE_ROLES, ADMIN, 1, ModelDiff.Level.MIGRATION).version());
  }

  @Test
  void aRoleThatUsersStillHoldIsAskedAbout() {
    applyFirst();
    when(keycloak.usersWithClientRole(anyString(), anyString()))
        .thenReturn(List.of(mock(KeycloakAdminClient.Person.class)));

    ApplyService.Plan plan = service.plan(MORE_ROLES);

    assertEquals(1, plan.questions().size());
    assertThrows(InvalidStateException.class, () -> service.apply(MORE_ROLES, ADMIN, 1, null));
  }

  @Test
  void aRiskyChangeNeedsItsLevelAccepted() {
    applyFirst();

    ApplyService.Plan plan = service.plan(NOTE_DROPPED);

    assertEquals("LOSS", plan.level().name());
    assertThrows(InvalidStateException.class, () -> service.apply(NOTE_DROPPED, ADMIN, 1, null));
    assertEquals(1, versions());
  }

  @Test
  void aRuleThatExistingRowsBreakStopsTheApply() {
    applyFirst();
    order(null);

    ApplyService.Plan plan = service.plan(NOTE_REQUIRED);

    assertEquals("rejected", plan.action());
    assertEquals("note", plan.violations().get(0).field());
    assertEquals(1, plan.violations().get(0).rows());
    assertThrows(
        InvalidStateException.class,
        () -> service.apply(NOTE_REQUIRED, ADMIN, 1, ModelDiff.Level.LOSS));
    assertEquals(1, versions());
    assertFalse(registry.isHeld("stock"));
  }

  @Test
  void processRulesStayWhileRecordsAreOpen() {
    applyFirst();
    order("x");

    ApplyService.Plan plan = service.plan(LONGER_DEADLINE);

    assertEquals("rejected", plan.action());
    assertEquals(1L, plan.openRecords().get("order"));
    assertThrows(
        InvalidStateException.class,
        () -> service.apply(LONGER_DEADLINE, ADMIN, 1, ModelDiff.Level.LOSS));
  }

  @Test
  void anEntityWithRunningProcessesIsNotDropped() {
    applyFirst();
    jdbc.update("INSERT INTO engine.act_ru_execution VALUES ('p1', 'stock.order.1')");
    String withoutOrder =
        FIRST.substring(0, FIRST.indexOf("  order:"))
            + "  item:\n    label: Hàng\n    fields:\n      title: {type: text, label: Tên}\n"
            + "    access: {CLERK: all}\n";

    ApplyService.Plan plan = service.plan(withoutOrder);

    assertTrue(plan.rejections().toString().contains("process instances running"));
    jdbc.execute("TRUNCATE engine.act_ru_execution");
  }

  @Test
  void anApplyThatStoppedHoldsTheModelUntilItIsResumed() {
    ApplyService failing = service("SAVE_VERSION");

    assertThrows(IllegalStateException.class, () -> failing.apply(FIRST, ADMIN, null, null));
    assertTrue(registry.isHeld("stock"));
    assertEquals("failed", service.runs("stock").get(0).get("status"));
    assertEquals(true, service.models().get(0).get("held"));

    assertEquals("applied", service.resume("stock", ADMIN).status());
    assertFalse(registry.isHeld("stock"));
  }

  @Test
  void aFirstApplyThatStoppedCanBeAbandoned() {
    ApplyService failing = service("SAVE_VERSION");
    assertThrows(IllegalStateException.class, () -> failing.apply(FIRST, ADMIN, null, null));

    assertEquals(0, service.abandon("stock", ADMIN).version());

    assertFalse(registry.isHeld("stock"));
    verify(keycloak).deleteClient(anyString());
    assertEquals(0, versions());
  }

  @Test
  void aStoppedChangeIsAbandonedBackToTheRunningVersion() {
    applyFirst();
    ApplyService failing = service("SAVE_VERSION");
    assertThrows(
        IllegalStateException.class,
        () -> failing.apply(MORE_ROLES, ADMIN, 1, ModelDiff.Level.LOSS));

    assertEquals(1, service.abandon("stock", ADMIN).version());
    assertFalse(registry.isHeld("stock"));
  }

  @Test
  void aModelWithoutAStoppedApplyHasNothingToResume() {
    applyFirst();

    assertThrows(InvalidStateException.class, () -> service.resume("stock", ADMIN));
    assertThrows(InvalidStateException.class, () -> service.planRollback("stock"));
  }

  @Test
  void aRollbackBringsBackTheEarlierFile() {
    applyFirst();
    service.apply(MORE_ROLES, ADMIN, 1, ModelDiff.Level.MIGRATION);

    assertEquals(2, service.planRollback("stock").fromVersion());
    ApplyService.Applied back = service.rollback("stock", ADMIN, 2, ModelDiff.Level.LOSS);

    assertEquals("applied", back.status());
    assertEquals(2, registry.require("stock").roles().size());
  }

  @Test
  void theStartAppliesOnlyWhatIsSafe(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("stock.yaml"), FIRST);
    ModelBootstrap bootstrap =
        new ModelBootstrap(
            service, keycloak, registry, jdbc, "stock.yaml, absent.yaml", dir.toString(), 1);

    bootstrap.run();
    bootstrap.run();
    assertEquals(1, versions());

    Files.writeString(dir.resolve("stock.yaml"), NOTE_DROPPED);
    bootstrap.run();
    assertEquals(1, versions());

    Files.writeString(dir.resolve("stock.yaml"), "format: 1\nname: Bad Name\n");
    bootstrap.run();
    assertEquals(1, versions());
  }

  @Test
  void aModelLeftByAnApplyIsReadByANewRegistry() {
    applyFirst();

    ModelRegistry reread = new ModelRegistry(jdbc);
    reread.reload();

    assertEquals("Đơn hàng", reread.require("stock").entities().get("order").label());
  }
}
