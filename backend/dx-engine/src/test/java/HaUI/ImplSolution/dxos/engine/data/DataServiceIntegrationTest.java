// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.time.BusinessTime;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.engine.event.ActionCheck;
import HaUI.ImplSolution.dxos.engine.event.RecordEventPublisher;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.files.AttachmentCleaner;
import HaUI.ImplSolution.dxos.engine.process.BookingGuard;
import HaUI.ImplSolution.dxos.engine.process.Deadlines;
import HaUI.ImplSolution.dxos.engine.process.ProcessRules;
import HaUI.ImplSolution.dxos.engine.process.ProcessService;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

// The data service on a real PostgreSQL: the tables come from the DDL of the system itself, the
// history is the real chain, and the signed-in user is the only thing that is set in the test.
@Testcontainers
class DataServiceIntegrationTest {

  private static final String YAML =
      """
      format: 1
      name: shop
      roles:
        - name: STAFF
          staff: true
        - name: BUYER
        - name: AUDITOR
      entities:
        order:
          label: Đơn hàng
          fields:
            title: {type: text, label: Tiêu đề, required: true}
            amount: {type: number, label: Số tiền, min: 0, max: 1000}
            kind: {type: choice, label: Loại, options: [Lẻ, Sỉ], default: Lẻ}
            sku: {type: text, label: Mã hàng, unique: true}
            code: {type: code, format: "DH-{year}-{seq:3}"}
          access: {STAFF: all, BUYER: own, AUDITOR: read}
      """;

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static JdbcTemplate jdbc;
  private static Model model;
  private static Model.Entity order;
  private static HistoryChain chain;
  private static DataService data;
  private static final TestUser USER = new TestUser();

  // The person who is signed in and the roles they hold in the model.
  private static final class TestUser implements UserContext {
    String id = "u-staff";
    Set<String> roles = Set.of("STAFF");

    @Override
    public String currentUserId() {
      return id;
    }

    @Override
    public String currentFullName() {
      return "Test " + id;
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
      return Set.of();
    }

    @Override
    public boolean isStaff() {
      return roles.contains("STAFF");
    }
  }

  private static void signIn(String id, String... roles) {
    USER.id = id;
    USER.roles = Set.of(roles);
  }

  @BeforeAll
  static void start() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas("engine")
        .locations("classpath:db/migration/engine")
        .table("flyway_schema_history_engine")
        .load()
        .migrate();
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    model = ModelParser.parse(YAML);
    order = model.entities().get("order");
    jdbc.execute(
        "DO $$ BEGIN CREATE ROLE report_ro; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
    jdbc.execute(DdlGenerator.createSchema(model));
    jdbc.execute("SET search_path TO \"" + model.schema() + "\"");
    DriverManagerDataSource inSchema =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + model.schema(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    new JdbcTemplate(inSchema).execute(DdlGenerator.desiredState(model, Map.of()));
    DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);
    chain = new HistoryChain(jdbc, transactions);
    EventLog events =
        new EventLog(jdbc, USER, mock(RecordEventPublisher.class), chain, new ObjectMapper());
    Clock clock = Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), BusinessTime.ZONE);
    data =
        new DataService(
            new NamedParameterJdbcTemplate(source),
            USER,
            mock(ProcessService.class),
            mock(ProcessRules.class),
            mock(BookingGuard.class),
            events,
            mock(KeycloakAdminClient.class),
            mock(AttachmentCleaner.class),
            mock(ActionCheck.class),
            new RecordCodes(new NamedParameterJdbcTemplate(source), clock),
            new Deadlines(clock),
            new WebLinks("", "core"),
            transactions);
  }

  @BeforeEach
  void emptyTables() {
    jdbc.execute(
        "TRUNCATE \""
            + model.schema()
            + "\".\"order\" RESTART IDENTITY; TRUNCATE engine.record_event"
            + " RESTART IDENTITY; TRUNCATE engine.record_counter");
    signIn("u-staff", "STAFF");
  }

  private static Map<String, Object> body(Object... pairs) {
    Map<String, Object> body = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      body.put((String) pairs[i], pairs[i + 1]);
    }
    return body;
  }

  private static Map<String, Object> create(Object... pairs) {
    return data.create(model, order, body(pairs));
  }

  private static Map<String, String> params(String... pairs) {
    Map<String, String> params = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      params.put(pairs[i], pairs[i + 1]);
    }
    return params;
  }

  private static Map<String, String> errors(Runnable action) {
    return assertThrows(ModelValidationException.class, action::run).fieldErrors();
  }

  private static List<Object> titles(PageResponse<Map<String, Object>> page) {
    return page.items().stream().map(row -> row.get("title")).toList();
  }

  // --- creating -----------------------------------------------------------------------------

  @Test
  void aCreatedRecordKeepsWhoMadeItItsDefaultsItsCodeAndVersionZero() {
    Map<String, Object> record = create("title", "Đơn đầu", "amount", 250);

    assertTrue(((Number) record.get("id")).longValue() > 0);
    assertEquals("u-staff", record.get("created_by"));
    assertEquals(0L, ((Number) record.get("version")).longValue());
    assertEquals("Lẻ", record.get("kind"));
    assertEquals(
        "DH-"
            + LocalDate.now(Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), BusinessTime.ZONE))
                .getYear()
            + "-001",
        record.get("code"));
    assertEquals(
        0,
        new java.math.BigDecimal("250")
            .compareTo(new java.math.BigDecimal(record.get("amount").toString())));
    assertNull(record.get("sku"));
  }

  @Test
  void eachNewRecordTakesTheNextCode() {
    create("title", "A");
    Map<String, Object> second = create("title", "B");

    assertTrue(second.get("code").toString().endsWith("-002"), second.get("code").toString());
  }

  @Test
  void aRecordWithoutARequiredFieldIsRefusedWithTheFieldNamed() {
    assertEquals("is required", errors(() -> create("amount", 5)).get("title"));
  }

  @Test
  void aFieldTheModelDoesNotHaveIsRefused() {
    assertEquals("unknown field", errors(() -> create("title", "A", "ghost", 1)).get("ghost"));
  }

  @Test
  void theColumnsTheSystemOwnsCannotBeSetByTheCaller() {
    Map<String, String> errors =
        errors(() -> create("title", "A", "created_by", "someone-else", "id", 99));

    assertEquals("cannot be set", errors.get("created_by"));
    assertEquals("cannot be set", errors.get("id"));
  }

  @Test
  void aCodeIsAssignedBySystemAndCannotBeWritten() {
    assertEquals(
        "is assigned by the system", errors(() -> create("title", "A", "code", "X")).get("code"));
  }

  @Test
  void aValueOfTheWrongTypeOrOutsideTheBoundsIsRefused() {
    assertEquals(
        "must be a number", errors(() -> create("title", "A", "amount", "many")).get("amount"));
    assertTrue(errors(() -> create("title", "A", "amount", -1)).containsKey("amount"));
    assertTrue(errors(() -> create("title", "A", "amount", 1001)).containsKey("amount"));
    assertTrue(errors(() -> create("title", "A", "kind", "Khác")).containsKey("kind"));
  }

  @Test
  void aValueUsedTwiceInAUniqueFieldIsRefused() {
    create("title", "A", "sku", "X-1");

    assertEquals("already exists", errors(() -> create("title", "B", "sku", "X-1")).get("sku"));
  }

  @Test
  void aRoleThatCanOnlyReadCannotCreateAndOneWithNoAccessCannotEvenRead() {
    signIn("u-auditor", "AUDITOR");
    ActionNotAllowedException readOnly =
        assertThrows(ActionNotAllowedException.class, () -> create("title", "A"));
    assertEquals("READ_ONLY", readOnly.getReason());

    signIn("u-nobody", "GHOST");
    assertEquals(
        "NO_ROLE_FOR_ENTITY",
        assertThrows(ActionNotAllowedException.class, () -> data.list(model, order, Map.of()))
            .getReason());
  }

  // --- reading ------------------------------------------------------------------------------

  @Test
  void aRoleWithOwnAccessSeesOnlyTheRecordsItCreated() {
    signIn("u-ann", "BUYER");
    Map<String, Object> mine = create("title", "Của Ann");
    signIn("u-staff", "STAFF");
    create("title", "Của nhân viên");

    signIn("u-ann", "BUYER");
    assertEquals(List.of("Của Ann"), titles(data.list(model, order, Map.of())));
    assertEquals(
        "Của Ann", data.get(model, order, ((Number) mine.get("id")).longValue()).get("title"));

    signIn("u-bob", "BUYER");
    assertEquals(List.of(), titles(data.list(model, order, Map.of())));
    assertThrows(
        ResourceNotFoundException.class,
        () -> data.get(model, order, ((Number) mine.get("id")).longValue()));
  }

  @Test
  void aRoleThatReadsEverythingSeesEveryRecord() {
    create("title", "A");
    create("title", "B");

    signIn("u-auditor", "AUDITOR");

    assertEquals(2, data.list(model, order, Map.of()).totalElements());
  }

  @Test
  void aListIsPagedAndCountsAllTheRecords() {
    for (int i = 1; i <= 5; i++) {
      create("title", "T" + i);
    }

    PageResponse<Map<String, Object>> second =
        data.list(model, order, params("size", "2", "page", "1"));

    assertEquals(List.of("T3", "T4"), titles(second));
    assertEquals(5, second.totalElements());
    assertEquals(3, second.totalPages());
    assertEquals(2, second.size());
    assertEquals(1, second.page());
  }

  @Test
  void aPageSizeIsKeptBetweenOneAndTwoHundred() {
    create("title", "A");

    assertEquals(200, data.list(model, order, params("size", "9999")).size());
    assertEquals(1, data.list(model, order, params("size", "0")).size());
    assertEquals(
        "must be a number", errors(() -> data.list(model, order, params("size", "x"))).get("size"));
  }

  @Test
  void aListIsSortedByAFieldInEitherDirection() {
    create("title", "B");
    create("title", "A");
    create("title", "C");

    assertEquals(List.of("A", "B", "C"), titles(data.list(model, order, params("sort", "title"))));
    assertEquals(
        List.of("C", "B", "A"),
        titles(data.list(model, order, params("sort", "title", "order", "desc"))));
    assertEquals(
        "unknown field",
        errors(() -> data.list(model, order, params("sort", "ghost"))).get("ghost"));
  }

  @Test
  void aListIsFilteredByEqualityOrderingOrSetOrText() {
    create("title", "Nhỏ", "amount", 10, "kind", "Lẻ");
    create("title", "Lớn", "amount", 500, "kind", "Sỉ");
    create("title", "Vừa", "amount", 100, "kind", "Sỉ");

    assertEquals(
        List.of("Lớn", "Vừa"),
        titles(data.list(model, order, params("filter.kind", "Sỉ", "sort", "title"))).stream()
            .sorted(java.util.Comparator.comparing(Object::toString))
            .toList());
    assertEquals(
        List.of("Lớn"), titles(data.list(model, order, params("filter.amount.gt", "100"))));
    assertEquals(2, data.list(model, order, params("filter.amount.lte", "100")).totalElements());
    assertEquals(2, data.list(model, order, params("filter.title.in", "Nhỏ,Lớn")).totalElements());
    assertEquals(1, data.list(model, order, params("filter.title.like", "ừa")).totalElements());
    assertTrue(
        errors(() -> data.list(model, order, params("filter.title.near", "x")))
            .containsKey("filter.title.near"));
    assertTrue(
        errors(() -> data.list(model, order, params("filter.amount.like", "x")))
            .containsKey("filter.amount.like"));
    assertTrue(
        errors(() -> data.list(model, order, params("filter.title.contains", "x")))
            .containsKey("filter.title.contains"));
  }

  @Test
  void aSearchMeetsWordsWithoutMarksAndInAnyCase() {
    create("title", "Sốt xuất huyết", "sku", "S-1");
    create("title", "Hoãn thi", "sku", "S-2");

    assertEquals(
        List.of("Sốt xuất huyết"), titles(data.list(model, order, params("q", "SOT xuat"))));
    assertEquals(List.of("Hoãn thi"), titles(data.list(model, order, params("q", "hoan s-2"))));
    assertEquals(0, data.list(model, order, params("q", "không có")).totalElements());
  }

  @Test
  void aSearchIsBoundedInLengthAndWords() {
    create("title", "A");

    assertTrue(
        errors(() -> data.list(model, order, params("q", "x".repeat(201)))).containsKey("q"));
    assertTrue(
        errors(() -> data.list(model, order, params("q", "a b c d e f g h i"))).containsKey("q"));
  }

  @Test
  void aSearchTakesTheWildcardsOfTheTextLiterally() {
    create("title", "100% sale");
    create("title", "100 sale");

    assertEquals(List.of("100% sale"), titles(data.list(model, order, params("q", "100%"))));
  }

  // --- changing -----------------------------------------------------------------------------

  @Test
  void anUpdateChangesTheFieldsRaisesTheVersionAndKeepsTheRest() {
    long id = ((Number) create("title", "Cũ", "amount", 5).get("id")).longValue();

    Map<String, Object> updated = data.update(model, order, id, body("title", "Mới"));

    assertEquals("Mới", updated.get("title"));
    assertEquals(1L, ((Number) updated.get("version")).longValue());
    assertEquals(
        0,
        new java.math.BigDecimal("5")
            .compareTo(new java.math.BigDecimal(updated.get("amount").toString())));
  }

  @Test
  void anUpdateThatChangesNothingKeepsTheVersion() {
    long id = ((Number) create("title", "A").get("id")).longValue();

    Map<String, Object> same = data.update(model, order, id, body("title", "A"));

    assertEquals(0L, ((Number) same.get("version")).longValue());
  }

  @Test
  void anUpdateOfAnOldVersionIsRefusedAndNeedsAtLeastOneField() {
    long id = ((Number) create("title", "A").get("id")).longValue();
    data.update(model, order, id, body("title", "B"));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> data.update(model, order, id, body("title", "C", "version", 0)));
    assertEquals("B", data.get(model, order, id).get("title"));
    assertEquals("C", data.update(model, order, id, body("title", "C", "version", 1)).get("title"));
    assertEquals(
        "no field to update",
        errors(() -> data.update(model, order, id, body("version", 2))).get("body"));
    assertEquals(
        "must be a whole number",
        errors(() -> data.update(model, order, id, body("title", "D", "version", "x")))
            .get("version"));
  }

  @Test
  void aRoleOfOwnAccessChangesItsOwnRecordsButNotThoseOfOthers() {
    signIn("u-ann", "BUYER");
    long mine = ((Number) create("title", "Của Ann").get("id")).longValue();
    signIn("u-staff", "STAFF");
    long others = ((Number) create("title", "Của nhân viên").get("id")).longValue();

    signIn("u-ann", "BUYER");
    assertEquals("Đã sửa", data.update(model, order, mine, body("title", "Đã sửa")).get("title"));
    assertThrows(
        ResourceNotFoundException.class,
        () -> data.update(model, order, others, body("title", "X")));
  }

  @Test
  void aValueUsedTwiceIsRefusedByAnUpdateToo() {
    create("title", "A", "sku", "X-1");
    long other = ((Number) create("title", "B", "sku", "X-2").get("id")).longValue();

    assertEquals(
        "already exists",
        errors(() -> data.update(model, order, other, body("sku", "X-1"))).get("sku"));
  }

  // --- deleting -----------------------------------------------------------------------------

  @Test
  void aDeletedRecordIsGoneAndAMissingOneIsNotFound() {
    long id = ((Number) create("title", "A").get("id")).longValue();

    data.delete(model, order, id);

    assertThrows(ResourceNotFoundException.class, () -> data.get(model, order, id));
    assertThrows(ResourceNotFoundException.class, () -> data.delete(model, order, id));
  }

  @Test
  void aRoleThatCanOnlyReadCannotDelete() {
    long id = ((Number) create("title", "A").get("id")).longValue();

    signIn("u-auditor", "AUDITOR");

    assertThrows(ActionNotAllowedException.class, () -> data.delete(model, order, id));
    assertNotNull(data.get(model, order, id));
  }

  // --- history ------------------------------------------------------------------------------

  @Test
  void everyChangeLeavesALineOfHistoryAndTheChainStaysIntact() {
    long id = ((Number) create("title", "A").get("id")).longValue();
    data.update(model, order, id, body("title", "B"));
    data.delete(model, order, id);

    List<Object> actions =
        jdbc
            .query("SELECT action FROM engine.record_event ORDER BY id", (rs, i) -> rs.getString(1))
            .stream()
            .map(a -> (Object) a)
            .toList();

    assertEquals(List.of("created", "updated", "deleted"), actions);
    assertNull(chain.firstBreak());
    assertFalse(chain.head() == null);
  }

  @Test
  void theHistoryOfARecordIsReadBackWithTheActorAndTheChange() {
    long id = ((Number) create("title", "A").get("id")).longValue();
    data.update(model, order, id, body("title", "B"));

    List<Map<String, Object>> events = data.events(model, order, id);

    assertEquals(2, events.size());
    assertEquals("created", events.get(0).get("action"));
    assertEquals("updated", events.get(1).get("action"));
    assertEquals("u-staff", events.get(1).get("actor"));
  }
}
