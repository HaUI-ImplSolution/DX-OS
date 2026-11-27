// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.data.IdempotencyService;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

// What leaves the core: queued messages in order and retried, the answer of a check service before
// an action, the targets read from the webhook file, and a repeated request answered once.
@Testcontainers
class DeliveryIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final ObjectMapper JSON = new ObjectMapper();
  private static JdbcTemplate jdbc;
  private static DataSourceTransactionManager transactions;

  @BeforeAll
  static void migrate() {
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
    transactions = new DataSourceTransactionManager(source);
  }

  @BeforeEach
  void empty() {
    jdbc.execute("TRUNCATE engine.outbox, engine.idempotency RESTART IDENTITY");
  }

  private static OutboxDispatcher dispatcher(OutboxHandler handler, int maxAttempts) {
    JobLock lock = mock(JobLock.class);
    when(lock.runIfFree(anyString(), any()))
        .thenAnswer(
            call -> {
              call.getArgument(1, Runnable.class).run();
              return true;
            });
    return new OutboxDispatcher(jdbc, transactions, lock, JSON, List.of(handler), maxAttempts);
  }

  private static OutboxHandler handler(List<Object> delivered, RuntimeException failure) {
    OutboxHandler handler = mock(OutboxHandler.class);
    when(handler.topic()).thenReturn("record.event");
    org.mockito.Mockito.doAnswer(
            call -> {
              if (failure != null) {
                throw failure;
              }
              delivered.add(call.<Map<String, Object>>getArgument(0).get("n"));
              return null;
            })
        .when(handler)
        .deliver(any());
    return handler;
  }

  private static void queue(String key, long record, int n) {
    new Outbox(jdbc, JSON).publish("record.event", key, "shop", "order", record, Map.of("n", n));
  }

  private static long count(String where) {
    return jdbc.queryForObject("SELECT count(*) FROM engine.outbox WHERE " + where, Long.class);
  }

  @Test
  void messagesAreDeliveredInOrderAndOnce() {
    queue("a", 1, 1);
    queue("b", 1, 2);
    queue("a", 1, 3);
    List<Object> delivered = new ArrayList<>();
    OutboxDispatcher dispatcher = dispatcher(handler(delivered, null), 3);

    dispatcher.run();
    dispatcher.run();

    assertEquals(List.of(1, 2), delivered);
    assertEquals(0, count("delivered_at IS NULL"));
  }

  @Test
  void aFailedMessageHoldsBackTheNextOfItsRecordAndIsGivenUpInTheEnd() {
    queue("a", 1, 1);
    queue("b", 1, 2);
    OutboxDispatcher dispatcher =
        dispatcher(handler(new ArrayList<>(), new IllegalStateException("refused")), 2);

    dispatcher.run();
    assertEquals(1, count("attempts = 1 AND last_error = 'refused'"));
    assertEquals(1, count("attempts = 0"));

    jdbc.update("UPDATE engine.outbox SET next_attempt_at = now()");
    dispatcher.run();
    assertEquals(1, count("attempts = 2 AND next_attempt_at = 'infinity'"));
  }

  @Test
  void anUnreachableTargetStopsTheRound() {
    queue("a", 1, 1);
    queue("b", 2, 2);
    RuntimeException down = mock(TargetUnreachableException.class);

    dispatcher(handler(new ArrayList<>(), down), 5).run();

    assertEquals(1, count("attempts = 1"));
    assertEquals(1, count("attempts = 0"));
  }

  private static ProcessFamily.Action action() {
    ProcessFamily.Action action = mock(ProcessFamily.Action.class);
    return action;
  }

  private static WebhookTargets targets(Path file) {
    return new WebhookTargets(file.toString(), "", "", "json");
  }

  @Test
  void onlyValidTargetsOfTheFileAreUsed(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("webhooks.yaml");
    Files.writeString(
        file,
        """
        targets:
          good: {url: "https://example.test/hook", secret: s, on_error: allow}
          chat: {url: "http://example.test/chat", format: chat, bypass: true}
          no_url: {format: json}
          ftp: {url: "ftp://example.test"}
          bad_mode: {url: "https://example.test", on_error: maybe}
          Bad-Name: {url: "https://example.test"}
        """);
    WebhookTargets targets = targets(file);

    assertTrue(targets.checkTarget("good").orElseThrow().allowOnError());
    assertTrue(targets.checkTarget("chat").orElseThrow().bypass());
    for (String name : List.of("no_url", "ftp", "bad_mode", "Bad-Name", "absent")) {
      assertTrue(targets.checkTarget(name).isEmpty(), name);
    }
    Files.delete(file);
    assertTrue(targets.checkTarget("good").isEmpty());
  }

  @Test
  void theDefaultTargetNeedsAUsableAddressAndFormat() {
    assertTrue(
        new WebhookTargets("", "https://example.test", "s", "json")
            .targetFor(Optional.empty())
            .isPresent());
    assertFalse(new WebhookTargets("", "not a url", "s", "json").sendsFor(Optional.empty()));
    assertFalse(
        new WebhookTargets("", "https://example.test", "s", "xml").sendsFor(Optional.empty()));
  }

  private static ActionCheck.Verdict ask(WebhookTargets.Target target, String answer) {
    WebhookTargets targets = mock(WebhookTargets.class);
    when(targets.checkTarget(any())).thenReturn(Optional.ofNullable(target));
    WebhookSender sender = mock(WebhookSender.class);
    if (answer == null) {
      when(sender.ask(any(), anyString(), any(), any()))
          .thenThrow(new IllegalStateException("down"));
    } else {
      when(sender.ask(any(), anyString(), any(), any())).thenReturn(answer);
    }
    return new ActionCheck(targets, sender, JSON).ask(action(), "e1", Map.of());
  }

  private static WebhookTargets.Target target(boolean allowOnError, boolean bypass) {
    return new WebhookTargets.Target("https://example.test", "s", "json", allowOnError, bypass);
  }

  @Test
  void aCheckAllowsOrDeniesWhatTheServiceSays() {
    assertFalse(ask(target(false, false), "{\"allow\": true}").stops());

    ActionCheck.Verdict denied =
        ask(target(false, false), "{\"allow\": false, \"code\": \"STOCK\", \"message\": \"hết\"}");

    assertTrue(denied.stops());
    assertEquals("STOCK", denied.code());
    assertEquals("hết", denied.message());
  }

  @Test
  void aCheckThatCannotBeAskedStopsTheActionUnlessTheTargetAllows() {
    assertTrue(ask(target(false, false), null).stops());
    assertTrue(ask(target(false, false), "{\"allow\": \"yes\"}").stops());
    assertTrue(ask(null, "{\"allow\": true}").stops());
    assertEquals(ActionCheck.SKIPPED, ask(target(true, false), null).history());
    assertEquals(ActionCheck.SKIPPED, ask(target(false, true), null).history());
  }

  @Test
  void aRepeatedRequestIsAnsweredFromTheFirstRun() {
    UserContext user = mock(UserContext.class);
    when(user.currentUserId()).thenReturn("u1");
    IdempotencyService once = new IdempotencyService(jdbc, user, JSON);
    AtomicInteger runs = new AtomicInteger();

    for (int i = 0; i < 2; i++) {
      IdempotencyService.Outcome outcome =
          once.once(
              "key-1",
              "create",
              "shop/order",
              Map.of("a", 1, "b", List.of(2)),
              () -> Map.of("run", runs.incrementAndGet()));
      assertEquals(i == 1, outcome.replayed());
      assertEquals(1, outcome.answer().get("run"));
    }

    assertThrows(
        InvalidStateException.class,
        () -> once.once("key-1", "create", "shop/order", Map.of("a", 2), () -> Map.of()));
    assertThrows(
        ModelValidationException.class,
        () -> once.once("bad key", "create", "x", Map.of(), () -> Map.of()));
    assertFalse(once.once(null, "create", "x", Map.of(), () -> Map.of()).replayed());
  }
}
