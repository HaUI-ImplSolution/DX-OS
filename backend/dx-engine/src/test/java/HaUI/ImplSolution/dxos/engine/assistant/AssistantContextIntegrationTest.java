// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.data.EventLog;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Scope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class AssistantContextIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  private AssistantContext context;
  private JdbcTemplate jdbc;
  private NamedParameterJdbcTemplate named;
  private UserContext user;
  private ModelRegistry registry;
  private final AssistantContract.Query query = new AssistantContract.Query("find", "example",
      "request", "printer", Map.of(), null, null, false);

  @BeforeEach
  void prepare() {
    DriverManagerDataSource source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).schemas("engine").defaultSchema("engine")
        .locations("classpath:db/migration/engine").load().migrate();
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS m_example");
    jdbc.execute("CREATE TABLE IF NOT EXISTS m_example.request (id bigserial PRIMARY KEY, "
        + "title text, created_by text, created_at timestamptz DEFAULT now(), "
        + "updated_at timestamptz DEFAULT now(), version int DEFAULT 1)");
    jdbc.execute("TRUNCATE m_example.request RESTART IDENTITY");
    jdbc.execute("TRUNCATE engine.record_event RESTART IDENTITY");
    jdbc.execute("TRUNCATE engine.model_version RESTART IDENTITY");
    jdbc.update("INSERT INTO engine.model_version(model, version, hash, content, applied_by) "
        + "VALUES ('example', 1, 'fixture', 'fixture', 'fixture')");
    jdbc.update("INSERT INTO m_example.request(title, created_by) VALUES ('printer', 'owner'), "
        + "('private', 'other')");
    user = mock(UserContext.class);
    when(user.roles()).thenReturn(Set.of("REQUESTER"));
    when(user.currentUserId()).thenReturn("owner");
    when(user.groups()).thenReturn(Set.of());
    EventLog events = new EventLog(jdbc, user);
    named = spy(new NamedParameterJdbcTemplate(source));
    DataService data = new DataService(named, user, null, null, null, events, null, null);
    Model.Field title = new Model.Field("title", FieldType.TEXT, "Tiêu đề", true, false, List.of(),
        null, null, null);
    Model.Entity entity = new Model.Entity("request", "Yêu cầu", Map.of("title", title),
        Map.of("REQUESTER", Scope.OWN), null, null);
    Model model = new Model(0, "example", List.of(), List.of(), Map.of("request", entity));
    registry = mock(ModelRegistry.class);
    when(registry.require("example")).thenReturn(model);
    context = new AssistantContext(registry, data, jdbc, new DataSourceTransactionManager(source));
  }

  @Test
  void snapshotsAndHistoryRespectExistingOwnerPermission() {
    var snapshot = context.capture(query, Map.of(), List.of(1L));
    assertEquals(1, snapshot.total());
    assertEquals(1L, snapshot.rows().getFirst().get("id"));
    assertThrows(ResourceNotFoundException.class,
        () -> context.capture(query, Map.of(), List.of(2L)));
  }

  @Test
  void appendedNoteAndEditedCandidateInvalidateSnapshots() {
    var original = context.capture(query, Map.of(), List.of(1L));
    jdbc.update("INSERT INTO engine.record_event(model, entity, record_id, action, actor, note) "
        + "VALUES ('example', 'request', 1, 'updated', 'owner', 'new note')");
    var noted = context.capture(query, Map.of(), List.of(1L));
    assertTrue(original.sameRecords(noted));
    assertFalse(original.sameSources(noted));
    jdbc.update("UPDATE m_example.request SET title='changed' WHERE id=1");
    assertFalse(noted.sameRecords(context.capture(query, Map.of(), List.of(1L))));
  }

  @Test
  void boundedHistoryReportsMoreAndTracksEventsOutsideWindow() {
    for (int n = 0; n < 101; n++) {
      jdbc.update("INSERT INTO engine.record_event(model, entity, record_id, action, actor) "
          + "VALUES ('example', 'request', 1, 'updated', 'owner')");
    }
    var snapshot = context.capture(query, Map.of(), List.of(1L));
    assertEquals("HISTORY_LIMIT", snapshot.limitReason());
    assertEquals(100, snapshot.histories().get(1L).items().size());
    var windowed = new AssistantContract.Query("summarize", "example", "request", "", Map.of(), 1L,
        new AssistantContract.HistoryWindow(null, Instant.parse("2000-01-01T00:00:00Z")), false);
    var outside = context.capture(windowed, Map.of(), List.of(1L));
    assertTrue(outside.histories().get(1L).items().isEmpty());
    assertEquals(101, outside.histories().get(1L).latestId());
  }

  @Test
  void countAndRowsUseOneSnapshotDuringConcurrentInsert() {
    AtomicBoolean once = new AtomicBoolean();
    doAnswer(invocation -> {
      Object count = invocation.callRealMethod();
      if (once.compareAndSet(false, true)) {
        CompletableFuture
            .runAsync(() -> jdbc
                .update("INSERT INTO m_example.request(title, created_by) VALUES ('new', 'owner')"))
            .join();
      }
      return count;
    }).when(named).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
    var first = context.capture(query, Map.of(), List.of());
    assertEquals(1, first.total());
    assertEquals(1, first.rows().size());
    var second = context.capture(query, Map.of(), List.of());
    assertEquals(2, second.total());
    assertFalse(first.sameRecords(second));
  }

  @Test
  void unitAssignedAndMultipleRolesKeepTheExistingRowScopes() {
    jdbc.execute("ALTER TABLE m_example.request ADD COLUMN IF NOT EXISTS department text, "
        + "ADD COLUMN IF NOT EXISTS assignee text, ADD COLUMN IF NOT EXISTS status text, "
        + "ADD COLUMN IF NOT EXISTS decided_by text, ADD COLUMN IF NOT EXISTS decided_at timestamptz, "
        + "ADD COLUMN IF NOT EXISTS handle_by timestamptz, ADD COLUMN IF NOT EXISTS overdue boolean");
    jdbc.update("UPDATE m_example.request SET department=CASE WHEN id=1 THEN 'A' ELSE 'B' END, "
        + "assignee=CASE WHEN id=2 THEN 'owner' ELSE NULL END, status='submitted'");
    Model.Field unit = new Model.Field("department", FieldType.UNIT, "Đơn vị", false, false,
        List.of(), null, null, null);
    Model.Entity entity = new Model.Entity("request", "Yêu cầu", Map.of("department", unit),
        Map.of("AUTHOR", Scope.OWN, "HANDLER", Scope.UNIT, "ASSIGNEE", Scope.ASSIGNED),
        new Model.Process("approval", "HANDLER", false, null, Map.of(), null), null);
    when(registry.require("example"))
        .thenReturn(new Model(0, "example", List.of(), List.of(), Map.of("request", entity)));
    when(user.roles()).thenReturn(Set.of("HANDLER"));
    when(user.groups()).thenReturn(Set.of("A"));
    assertEquals(1L, context.capture(query, Map.of(), List.of()).rows().getFirst().get("id"));
    when(user.groups()).thenReturn(Set.of("B"));
    assertEquals(2L, context.capture(query, Map.of(), List.of()).rows().getFirst().get("id"));
    when(user.roles()).thenReturn(Set.of("ASSIGNEE"));
    assertEquals(2L, context.capture(query, Map.of(), List.of()).rows().getFirst().get("id"));
    when(user.roles()).thenReturn(Set.of("AUTHOR", "ASSIGNEE"));
    assertEquals(2, context.capture(query, Map.of(), List.of()).total());
  }

  @Test
  void childRecordsFollowOwnerViaAcrossTwoLevels() {
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS m_example.root (id bigint PRIMARY KEY, created_by text)");
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS m_example.folder (id bigint PRIMARY KEY, root bigint)");
    jdbc.execute("TRUNCATE m_example.root, m_example.folder");
    jdbc.execute("ALTER TABLE m_example.request ADD COLUMN IF NOT EXISTS folder bigint");
    jdbc.execute("INSERT INTO m_example.root VALUES (1,'owner'), (2,'other')");
    jdbc.execute("INSERT INTO m_example.folder VALUES (1,1), (2,2)");
    jdbc.execute("UPDATE m_example.request SET folder=id, created_by='unrelated'");
    Model.Field rootLink = new Model.Field("root", FieldType.REFERENCE, "Root", true, false,
        List.of(), "root", null, null);
    Model.Field folderLink = new Model.Field("folder", FieldType.REFERENCE, "Folder", true, false,
        List.of(), "folder", null, null);
    Map<String, Scope> access = Map.of("REQUESTER", Scope.OWN);
    Model.Entity root = new Model.Entity("root", "Root", Map.of(), access, null, null);
    Model.Entity folder =
        new Model.Entity("folder", "Folder", Map.of("root", rootLink), access, null, "root");
    Model.Entity child =
        new Model.Entity("request", "Child", Map.of("folder", folderLink), access, null, "folder");
    when(registry.require("example")).thenReturn(new Model(0, "example", List.of(), List.of(),
        Map.of("root", root, "folder", folder, "request", child)));
    assertEquals(1, context.capture(query, Map.of(), List.of()).total());
    assertEquals(1L, context.capture(query, Map.of(), List.of()).rows().getFirst().get("id"));
    when(user.currentUserId()).thenReturn("other");
    assertEquals(2L, context.capture(query, Map.of(), List.of()).rows().getFirst().get("id"));
  }

  @Test
  void deletionModelVersionAndLongTargetRequireFreshOrNarrowerContext() {
    var original = context.capture(query, Map.of(), List.of());
    jdbc.update("UPDATE engine.model_version SET version=2 WHERE model='example'");
    assertFalse(original.sameRecords(context.capture(query, Map.of(), List.of())));
    jdbc.update("UPDATE m_example.request SET title=? WHERE id=1", "x".repeat(4001));
    var similar =
        new AssistantContract.Query("similar", "example", "request", "", Map.of(), 1L, null, false);
    assertEquals("FIELD_LIMIT",
        context.capture(similar, Map.of("title.eq", "missing"), List.of()).limitReason());
    jdbc.update("DELETE FROM m_example.request WHERE id=1");
    assertFalse(original.sameRecords(context.capture(query, Map.of(), List.of())));
  }

  @Test
  void completeCountsAndFiltersDoNotTreatTheFirstPageAsTheWholeSet() {
    jdbc.update("INSERT INTO m_example.request(title, created_by) "
        + "SELECT 'bulk', 'owner' FROM generate_series(1, 200)");
    var full = context.capture(query, Map.of(), List.of());
    assertEquals(201, full.total());
    assertEquals(200, full.rows().size());
    assertEquals("CANDIDATE_LIMIT", full.limitReason());
    var filtered = context.capture(query, Map.of("title.eq", "printer"), List.of());
    assertEquals(1, filtered.total());
    assertNull(filtered.limitReason());
    assertEquals(0, context.capture(query, Map.of("title.eq", "missing"), List.of()).total());
  }
}
