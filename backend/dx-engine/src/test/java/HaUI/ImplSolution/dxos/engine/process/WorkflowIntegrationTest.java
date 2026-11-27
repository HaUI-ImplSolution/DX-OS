// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.data.EventLog;
import HaUI.ImplSolution.dxos.engine.data.HistoryChain;
import HaUI.ImplSolution.dxos.engine.data.RecordCodes;
import HaUI.ImplSolution.dxos.engine.data.WebLinks;
import HaUI.ImplSolution.dxos.engine.event.ActionCheck;
import HaUI.ImplSolution.dxos.engine.event.Outbox;
import HaUI.ImplSolution.dxos.engine.event.OverdueSweep;
import HaUI.ImplSolution.dxos.engine.event.RecordEventPublisher;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.files.AttachmentCleaner;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

// Records going through their process on Flowable and PostgreSQL: who may take which action in
// which state, which rows each person reaches, and what the background jobs do with waiting
// records.
@Testcontainers
class WorkflowIntegrationTest {

  private static final String YAML =
      """
      format: 1
      name: desk
      roles:
        - name: STUDENT
        - name: OFFICER
          staff: true
        - name: SUPERVISOR
          staff: true
      units:
        - name: NORTH
          label: Phòng Bắc
        - name: SOUTH
          label: Phòng Nam
      entities:
        request:
          label: Hồ sơ
          fields:
            title: {type: text, label: Tiêu đề, required: true}
            department: {type: unit, label: Đơn vị, required: true}
            note: {type: text, label: Ghi chú}
          access: {STUDENT: own, OFFICER: unit, SUPERVISOR: read}
          process: {family: approval, handler: OFFICER, deadline: 48h}
        ticket:
          label: Phiếu
          fields:
            title: {type: text, label: Tiêu đề, required: true}
          access: {STUDENT: own, OFFICER: all}
          process: {family: work_item, handler: OFFICER, auto_close: 7d}
      """;

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final Person USER = new Person();

  private static JdbcTemplate jdbc;
  private static TransactionTemplate transaction;
  private static ProcessEngine engine;
  private static Model model;
  private static Model.Entity request;
  private static Model.Entity ticket;
  private static DataService data;
  private static OverdueSweep overdue;
  private static AutoCloseSweep autoClose;

  private static final class Person implements UserContext {
    String id = "stu";
    Set<String> roles = Set.of("STUDENT");
    Set<String> groups = Set.of();

    @Override
    public String currentUserId() {
      return id;
    }

    @Override
    public String currentFullName() {
      return "Name of " + id;
    }

    @Override
    public Set<String> roles() {
      return Set.of();
    }

    @Override
    public boolean hasRole(String role) {
      return false;
    }

    @Override
    public Set<String> clientRoles(String clientId) {
      return roles;
    }

    @Override
    public Set<String> groups() {
      return groups;
    }

    @Override
    public boolean isStaff() {
      return !roles.contains("STUDENT");
    }
  }

  @BeforeAll
  static void start() {
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
    DataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    model = ModelParser.parse(YAML);
    request = model.entities().get("request");
    ticket = model.entities().get("ticket");
    jdbc.execute(
        "DO $$ BEGIN CREATE ROLE report_ro; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
    jdbc.execute(DdlGenerator.createSchema(model));
    new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + model.schema(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()))
        .execute(DdlGenerator.desiredState(model, Map.of()));

    DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);
    transaction = new TransactionTemplate(transactions);
    ModelRegistry registry = mock(ModelRegistry.class);
    when(registry.require("desk")).thenReturn(model);
    when(registry.find("desk")).thenReturn(Optional.of(model));
    when(registry.all()).thenReturn(List.of(model));
    JobLock lock = mock(JobLock.class);
    when(lock.runIfFree(anyString(), any()))
        .thenAnswer(
            call -> {
              call.getArgument(1, Runnable.class).run();
              return true;
            });

    ObjectMapper json = new ObjectMapper();
    Clock clock = Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), BusinessTime.ZONE);
    Deadlines deadlines = new Deadlines(clock);
    HistoryChain chain = new HistoryChain(jdbc, transactions);
    RecordEventPublisher publisher = mock(RecordEventPublisher.class);
    EventLog events = new EventLog(jdbc, USER, publisher, chain, json);
    overdue =
        new OverdueSweep(
            jdbc, registry, lock, publisher, transactions, chain, Duration.ofHours(24));
    RecordStatus status =
        new RecordStatus(jdbc, registry, new Outbox(jdbc, json), events, overdue, deadlines);

    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
    configuration.setDataSource(source);
    configuration.setTransactionManager(transactions);
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    configuration.setDatabaseSchema("engine");
    configuration.setDatabaseTablePrefix("engine.");
    configuration.setTablePrefixIsSchema(true);
    configuration.setAsyncExecutorActivate(false);
    configuration.setBeans(new java.util.HashMap<>(Map.of("recordStatus", status)));
    engine = configuration.buildProcessEngine();
    for (String name : List.of("approval", "work_item")) {
      engine
          .getRepositoryService()
          .createDeployment()
          .addClasspathResource("processes/" + name + ".bpmn20.xml")
          .deploy();
    }
    ProcessService process =
        new ProcessService(engine.getRuntimeService(), engine.getTaskService(), status, false);
    autoClose = new AutoCloseSweep(jdbc, registry, lock, process, publisher, chain, transactions);
    data =
        new DataService(
            new NamedParameterJdbcTemplate(source),
            USER,
            process,
            new ProcessRules(),
            new BookingGuard(jdbc),
            events,
            mock(KeycloakAdminClient.class),
            mock(AttachmentCleaner.class),
            mock(ActionCheck.class),
            new RecordCodes(new NamedParameterJdbcTemplate(source), clock),
            deadlines,
            new WebLinks("", "core"),
            transactions);
  }

  @AfterAll
  static void stop() {
    engine.close();
  }

  @BeforeEach
  void emptyTables() {
    for (Model.Entity entity : List.of(request, ticket)) {
      engine
          .getRuntimeService()
          .createProcessInstanceQuery()
          .list()
          .forEach(i -> engine.getRuntimeService().deleteProcessInstance(i.getId(), "reset"));
      jdbc.execute("TRUNCATE " + table(entity) + " RESTART IDENTITY CASCADE");
    }
    jdbc.execute("TRUNCATE engine.record_event RESTART IDENTITY CASCADE");
    student("stu");
  }

  private static String table(Model.Entity entity) {
    return "\"" + model.schema() + "\".\"" + entity.name() + "\"";
  }

  private static void signIn(String id, Set<String> roles, Set<String> groups) {
    USER.id = id;
    USER.roles = roles;
    USER.groups = groups;
  }

  private static void student(String id) {
    signIn(id, Set.of("STUDENT"), Set.of());
  }

  private static void officer(String id, String unit) {
    signIn(id, Set.of("OFFICER"), Set.of(unit));
  }

  private static <T> T tx(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }

  private static long submit(String department) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "Xin giấy xác nhận");
    body.put("department", department);
    return id(tx(() -> data.create(model, request, body)));
  }

  private static long openTicket() {
    return id(tx(() -> data.create(model, ticket, new LinkedHashMap<>(Map.of("title", "Hỏng")))));
  }

  private static long id(Map<String, Object> record) {
    return ((Number) record.get("id")).longValue();
  }

  private static Map<String, Object> act(Model.Entity entity, long id, String action, String note) {
    return tx(() -> data.act(model, entity, id, action, note));
  }

  private static String refusal(Model.Entity entity, long id, String action, String note) {
    RuntimeException e = assertThrows(RuntimeException.class, () -> act(entity, id, action, note));
    if (e instanceof ActionNotAllowedException refused) {
      return refused.getReason();
    }
    if (e instanceof InvalidStateException state) {
      return state.getReason();
    }
    return e.getClass().getSimpleName();
  }

  private static List<String> actions(Model.Entity entity, long id) {
    return tx(() -> data.events(model, entity, id)).stream()
        .map(line -> (String) line.get("action"))
        .toList();
  }

  @Test
  void aSubmittedRecordWaitsForItsHandlerWithADeadline() {
    long id = submit("NORTH");
    Map<String, Object> record = tx(() -> data.get(model, request, id));
    assertEquals("submitted", record.get("status"));
    assertNotNull(record.get("handle_by"));

    officer("north", "NORTH");
    assertEquals(1L, tx(() -> data.countWaiting(model, request)));
    assertEquals(1, tx(() -> data.waiting(model, request, 10)).size());
  }

  @Test
  void anOfficerOfAnotherUnitNeitherSeesNorDecidesTheRecord() {
    long id = submit("NORTH");

    officer("south", "SOUTH");
    assertEquals(0L, tx(() -> data.countWaiting(model, request)));
    assertThrows(ResourceNotFoundException.class, () -> tx(() -> data.get(model, request, id)));
    assertThrows(ResourceNotFoundException.class, () -> act(request, id, "approve", null));
  }

  @Test
  void anotherStudentCannotReachTheRecord() {
    long id = submit("NORTH");

    student("other");
    assertThrows(ResourceNotFoundException.class, () -> act(request, id, "withdraw", null));
  }

  @Test
  void theOwnerCannotApproveAndAnObserverCannotDecide() {
    long id = submit("NORTH");
    assertEquals("HANDLER_ONLY", refusal(request, id, "approve", null));

    signIn("boss", Set.of("SUPERVISOR"), Set.of());
    assertThrows(ActionNotAllowedException.class, () -> act(request, id, "approve", null));
    assertEquals("submitted", tx(() -> data.get(model, request, id)).get("status"));
  }

  @Test
  void theCreatorCannotDecideTheirOwnRecord() {
    signIn("both", Set.of("STUDENT", "OFFICER"), Set.of("NORTH"));
    long id = submit("NORTH");

    assertEquals("SELF_DECISION", refusal(request, id, "approve", null));
  }

  @Test
  void anApprovalClosesTheRecordAndNothingFollows() {
    long id = submit("NORTH");
    officer("north", "NORTH");

    Map<String, Object> approved = act(request, id, "approve", null);

    assertEquals("approved", approved.get("status"));
    assertEquals("north", approved.get("decided_by"));
    assertNull(approved.get("handle_by"));
    assertEquals("WRONG_STATE", refusal(request, id, "reject", "muộn"));
    assertEquals(List.of("created", "approve"), actions(request, id));
  }

  @Test
  void aReturnNeedsANoteAndComesBackOnResubmit() {
    long id = submit("NORTH");
    officer("north", "NORTH");
    assertThrows(ModelValidationException.class, () -> act(request, id, "return", " "));
    assertEquals("returned", act(request, id, "return", "Thiếu minh chứng").get("status"));
    assertEquals("OWNER_ONLY", refusal(request, id, "resubmit", null));

    student("stu");
    assertEquals(1L, tx(() -> data.countWaiting(model, request)));
    assertEquals("submitted", act(request, id, "resubmit", null).get("status"));
  }

  @Test
  void theOwnerWithdrawsAnOpenRecord() {
    long id = submit("NORTH");

    assertEquals("withdrawn", act(request, id, "withdraw", null).get("status"));
    assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().count());
  }

  @Test
  void anUnknownActionIsNotFound() {
    long id = submit("NORTH");

    assertThrows(ResourceNotFoundException.class, () -> act(request, id, "publish", null));
  }

  @Test
  void anActionOnAnOlderVersionIsRefused() {
    long id = submit("NORTH");
    officer("north", "NORTH");

    assertThrows(
        RuntimeException.class, () -> tx(() -> data.act(model, request, id, "approve", null, 99L)));
    assertEquals("submitted", tx(() -> data.get(model, request, id)).get("status"));
  }

  @Test
  void anAssignedRecordIsDecidedByItsAssigneeOnly() {
    long id = submit("NORTH");
    jdbc.update("UPDATE " + table(request) + " SET assignee = 'first' WHERE id = ?", id);

    officer("second", "NORTH");
    assertEquals("ASSIGNED_TO_OTHER", refusal(request, id, "approve", null));
    assertEquals(0L, tx(() -> data.countWaiting(model, request)));

    officer("first", "NORTH");
    assertEquals("approved", act(request, id, "approve", null).get("status"));
  }

  @Test
  void aWorkItemGoesFromSubmittedToClosed() {
    long id = openTicket();
    officer("fixer", "NORTH");
    assertEquals("in_progress", act(ticket, id, "start", null).get("status"));
    assertEquals("resolved", act(ticket, id, "resolve", null).get("status"));

    student("stu");
    assertEquals("closed", act(ticket, id, "confirm", null).get("status"));
  }

  @Test
  void aLateRecordIsReportedOnce() {
    long id = submit("NORTH");
    jdbc.update(
        "UPDATE " + table(request) + " SET handle_by = now() - interval '1 hour' WHERE id = ?", id);

    overdue.run();
    overdue.run();

    assertEquals(List.of("created", "overdue"), actions(request, id));
  }

  @Test
  void aRecordNearItsDeadlineIsAnnounced() {
    long id = submit("NORTH");
    jdbc.update(
        "UPDATE " + table(request) + " SET handle_by = now() + interval '1 hour' WHERE id = ?", id);

    overdue.run();

    assertEquals(List.of("created", "due_soon"), actions(request, id));
  }

  @Test
  void aResolvedWorkItemLeftAloneIsClosedByTheSystem() {
    long id = openTicket();
    long recent = openTicket();
    officer("fixer", "NORTH");
    for (long each : new long[] {id, recent}) {
      act(ticket, each, "start", null);
      act(ticket, each, "resolve", null);
    }
    jdbc.update(
        "UPDATE " + table(ticket) + " SET updated_at = now() - interval '8 days' WHERE id = ?", id);
    jdbc.update(
        "UPDATE engine.record_event SET at = now() - interval '8 days' WHERE record_id = ?", id);

    autoClose.run();

    student("stu");
    assertEquals("closed", tx(() -> data.get(model, ticket, id)).get("status"));
    assertEquals("resolved", tx(() -> data.get(model, ticket, recent)).get("status"));
  }
}
