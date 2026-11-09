// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.table;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient.Person;
import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.files.AttachmentCleaner;
import HaUI.ImplSolution.dxos.engine.process.BookingGuard;
import HaUI.ImplSolution.dxos.engine.process.ProcessRules;
import HaUI.ImplSolution.dxos.engine.process.ProcessService;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.engine.registry.ModelClient;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Actor;
import HaUI.ImplSolution.dxos.model.Scope;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads and writes the records of any entity of any applied model. Which tables, columns, rules
// and roles apply all come from the model; the SQL text is built from its validated names and
// every value goes through a parameter.
@Service
public class DataService {

  // The scopes through which the roles of the caller read and write an entity.
  public record Access(Set<Scope> reads, Set<Scope> writes) {

    public boolean read() {
      return !reads.isEmpty();
    }

    public boolean write() {
      return !writes.isEmpty();
    }
  }

  private static final int MAX_PAGE_SIZE = 200;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int STATS_TIMEOUT_MS = 10_000;
  private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "created_by", "created_at",
      "updated_at", "version", "assignee", "decided_by", "decided_at", "handle_by", "overdue");
  private static final Map<String, String> OPERATORS =
      Map.of("eq", "=", "ne", "<>", "lt", "<", "lte", "<=", "gt", ">", "gte", ">=");

  private final NamedParameterJdbcTemplate jdbc;
  private final UserContext user;
  private final ProcessService process;
  private final ProcessRules rules;
  private final BookingGuard bookings;
  private final EventLog events;
  private final KeycloakAdminClient keycloak;
  private final AttachmentCleaner attachments;

  public DataService(NamedParameterJdbcTemplate jdbc, UserContext user, ProcessService process,
      ProcessRules rules, BookingGuard bookings, EventLog events, KeycloakAdminClient keycloak,
      AttachmentCleaner attachments) {
    this.jdbc = jdbc;
    this.user = user;
    this.process = process;
    this.rules = rules;
    this.bookings = bookings;
    this.events = events;
    this.keycloak = keycloak;
    this.attachments = attachments;
  }

  public Access access(Model model, Entity entity) {
    Set<String> roles = user.clientRoles(ModelClient.of(model.name()));
    Set<Scope> reads = EnumSet.noneOf(Scope.class);
    Set<Scope> writes = EnumSet.noneOf(Scope.class);
    for (Map.Entry<String, Scope> grant : entity.access().entrySet()) {
      if (roles.contains(grant.getKey())) {
        reads.add(grant.getValue());
        if (grant.getValue().canWrite()) {
          writes.add(grant.getValue());
        }
      }
    }
    return new Access(reads, writes);
  }

  public boolean canHandle(Model model, Entity entity) {
    return entity.process() != null && !Collections
        .disjoint(user.clientRoles(ModelClient.of(model.name())), entity.process().allRoles());
  }

  // The handler role assigns records, and so does a role that coordinates by writing to all rows
  // or to those of its units.
  public boolean canAssign(Model model, Entity entity) {
    Set<Scope> writes = access(model, entity).writes();
    return entity.process() != null
        && (canHandle(model, entity) || writes.contains(Scope.ALL) || writes.contains(Scope.UNIT));
  }

  @Transactional(readOnly = true)
  public PageResponse<Map<String, Object>> list(Model model, Entity entity,
      Map<String, String> params) {
    Access access = access(model, entity);
    requireRead(access);
    MapSqlParameterSource values = new MapSqlParameterSource();
    List<String> where = new ArrayList<>();
    where.add(scope(model, entity, access.reads(), values));
    filters(entity, params, where, values);

    String order = "id ASC";
    String sort = params.get("sort");
    if (sort != null && !sort.isBlank()) {
      columnType(entity, sort);
      String direction = "desc".equalsIgnoreCase(params.get("order")) ? "DESC" : "ASC";
      order = ident(sort) + " " + direction + ", id ASC";
    }
    int size = clamp(intParam(params, "size", DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE);
    int page = Math.max(intParam(params, "page", 0), 0);
    values.addValue("limit", size).addValue("offset", (long) page * size);

    String from = " FROM " + table(model, entity) + " WHERE " + String.join(" AND ", where);
    Long total = jdbc.queryForObject("SELECT count(*)" + from, values, Long.class);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT " + columns(entity) + from + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
        values).stream().map(DataService::present).toList();
    long count = total == null ? 0 : total;
    return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
  }

  // Counts over the rows the caller can see, with the same filters as list: in all, per status,
  // past
  // their deadline at computedAt (handle_by before it), and per unit when the entity has a unit
  // field.
  @Transactional(readOnly = true)
  public Map<String, Object> stats(Model model, Entity entity, Map<String, String> params) {
    Access access = access(model, entity);
    requireRead(access);
    // Applies to this transaction only: the counts below stop after the limit.
    jdbc.getJdbcOperations().execute("SET LOCAL statement_timeout = " + STATS_TIMEOUT_MS);
    MapSqlParameterSource values = new MapSqlParameterSource();
    List<String> where = new ArrayList<>();
    where.add(scope(model, entity, access.reads(), values));
    filters(entity, params, where, values);
    java.time.OffsetDateTime at = java.time.OffsetDateTime.now();
    values.addValue("at", at);
    String from = " FROM " + table(model, entity) + " WHERE " + String.join(" AND ", where);
    boolean process = entity.process() != null;
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("total", jdbc.queryForObject("SELECT count(*)" + from, values, Long.class));
    if (process) {
      body.put("byStatus", jdbc.queryForList(
          "SELECT status, count(*) AS count" + from + " GROUP BY status ORDER BY status", values));
      body.put("overdue",
          jdbc.queryForObject(
              "SELECT count(*)" + from + " AND handle_by IS NOT NULL AND handle_by < :at", values,
              Long.class));
      body.putAll(timings(from, values));
    }
    entity.unitField()
        .ifPresent(
            field -> body
                .put("byUnit",
                    jdbc.queryForList("SELECT " + ident(field.name())
                        + " AS unit, count(*) AS count" + from + " GROUP BY 1 ORDER BY 1",
                        values)));
    body.put("computedAt", at.toString());
    return body;
  }

  // Whole seconds from creation to the final state over the records that reached one, and the
  // mean and the largest age at computedAt over those that did not; null when there is no such
  // record.
  private Map<String, Object> timings(String from, MapSqlParameterSource values) {
    Map<String, Object> row =
        jdbc.queryForMap("SELECT avg(EXTRACT(EPOCH FROM decided_at - created_at))"
            + " FILTER (WHERE decided_at IS NOT NULL) AS decision,"
            + " avg(EXTRACT(EPOCH FROM CAST(:at AS timestamptz) - created_at))"
            + " FILTER (WHERE decided_at IS NULL) AS open_avg,"
            + " max(EXTRACT(EPOCH FROM CAST(:at AS timestamptz) - created_at))"
            + " FILTER (WHERE decided_at IS NULL) AS open_max" + from, values);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("avgDecisionSeconds", seconds(row.get("decision")));
    out.put("avgOpenAgeSeconds", seconds(row.get("open_avg")));
    out.put("oldestOpenAgeSeconds", seconds(row.get("open_max")));
    return out;
  }

  private static Long seconds(Object value) {
    return value == null ? null : Math.round(((Number) value).doubleValue());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(Model model, Entity entity, long id) {
    Access access = access(model, entity);
    requireRead(access);
    MapSqlParameterSource values = new MapSqlParameterSource("id", id);
    String scope = scope(model, entity, access.reads(), values);
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + columns(entity) + " FROM "
        + table(model, entity) + " WHERE id = :id AND " + scope, values);
    if (rows.isEmpty()) {
      throw notFound(entity, id);
    }
    return present(rows.get(0));
  }

  @Transactional
  public Map<String, Object> create(Model model, Entity entity, Map<String, Object> body) {
    Access access = access(model, entity);
    requireRead(access);
    requireWrite(access);
    Map<String, Object> values = coerce(entity, body, true);
    requireReachableParent(model, entity, access, values);
    requireCreatableUnit(entity, access, values);
    bookings.check(model, entity, values, null);
    MapSqlParameterSource params = new MapSqlParameterSource("created_by", user.currentUserId());
    List<String> names = new ArrayList<>(List.of("created_by"));
    List<String> markers = new ArrayList<>(List.of(":created_by"));
    values.forEach((name, value) -> {
      names.add(ident(name));
      markers.add(":" + name);
      params.addValue(name, value);
    });
    if (entity.process() != null && entity.process().deadline() != null) {
      names.add("handle_by");
      markers.add("now() + make_interval(secs => :handle_secs)");
      params.addValue("handle_secs", entity.process().deadline().toSeconds());
    }
    Map<String, Object> row;
    try {
      row = jdbc.queryForMap("INSERT INTO " + table(model, entity) + " (" + String.join(", ", names)
          + ") VALUES (" + String.join(", ", markers) + ") RETURNING " + columns(entity), params);
    } catch (DataIntegrityViolationException e) {
      throw translate(entity, e);
    }
    long id = ((Number) row.get("id")).longValue();
    if (entity.process() != null) {
      process.start(model, entity, id, user.currentUserId());
      row = jdbc.queryForMap(
          "SELECT " + columns(entity) + " FROM " + table(model, entity) + " WHERE id = :id",
          new MapSqlParameterSource("id", id));
    }
    events.record(model, entity, id, "created", null, (String) row.get("status"), null, null);
    return present(row);
  }

  @Transactional
  public Map<String, Object> update(Model model, Entity entity, long id, Map<String, Object> body) {
    Access access = access(model, entity);
    requireRead(access);
    requireWrite(access);
    Long baseVersion = baseVersion(body);
    Map<String, Object> values = coerce(entity, withoutVersion(body), false);
    if (values.isEmpty()) {
      throw new ModelValidationException("body", "no field to update");
    }
    requireReachableParent(model, entity, access, values);
    requireOpen(model, entity, access, id);
    Changed changed = changes(model, entity, access, id, values);
    Map<String, Object> changes = changed.values();
    if (changes.isEmpty()) {
      return get(model, entity, id);
    }
    requireMovableUnit(model, entity, access, id, changes);
    bookings.check(model, entity, changes, id);
    MapSqlParameterSource params = new MapSqlParameterSource("id", id);
    List<String> sets = new ArrayList<>();
    changes.forEach((name, value) -> {
      sets.add(ident(name) + " = :" + name);
      params.addValue(name, value);
    });
    String scope = scope(model, entity, access.writes(), params);
    String sameVersion = baseVersion == null ? "" : " AND version = :baseVersion";
    params.addValue("baseVersion", baseVersion);
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList("UPDATE " + table(model, entity) + " SET " + String.join(", ", sets)
          + ", updated_at = now(), version = version + 1 WHERE id = :id " + "AND " + scope
          + sameVersion + " RETURNING " + columns(entity), params);
    } catch (DataIntegrityViolationException e) {
      throw translate(entity, e);
    }
    if (rows.isEmpty()) {
      boolean there =
          baseVersion != null && !jdbc
              .queryForList(
                  "SELECT 1 FROM " + table(model, entity) + " WHERE id = :id AND " + scope, params)
              .isEmpty();
      if (there) {
        throw new ObjectOptimisticLockingFailureException(entity.name(), id);
      }
      throw notFound(entity, id);
    }
    String status = (String) rows.get(0).get("status");
    events.record(model, entity, id, "updated", status, status, null,
        events.changesOf(changes, changed.before()));
    return present(rows.get(0));
  }

  // The version the caller read the record at; an update with one only applies to that version.
  private static Long baseVersion(Map<String, Object> body) {
    Object value = body.get("version");
    if (value == null) {
      return null;
    }
    try {
      return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      throw new ModelValidationException("version", "must be a whole number");
    }
  }

  private static Map<String, Object> withoutVersion(Map<String, Object> body) {
    Map<String, Object> fields = new LinkedHashMap<>(body);
    fields.remove("version");
    return fields;
  }

  // Passes when the record is in a row the caller may write and, with a process, not final.
  @Transactional(readOnly = true)
  public void requireChangeable(Model model, Entity entity, long id) {
    Access access = access(model, entity);
    requireRead(access);
    requireWrite(access);
    MapSqlParameterSource values = new MapSqlParameterSource("id", id);
    String scope = scope(model, entity, access.writes(), values);
    String status = entity.process() == null ? "NULL" : "status";
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + status + " AS status FROM "
        + table(model, entity) + " WHERE id = :id AND " + scope, values);
    if (rows.isEmpty()) {
      throw notFound(entity, id);
    }
    if (entity.process() != null
        && entity.process().definition().isTerminal((String) rows.get(0).get("status"))) {
      throw new InvalidStateException("The record is closed", "RECORD_CLOSED");
    }
  }

  @Transactional
  public void delete(Model model, Entity entity, long id) {
    Access access = access(model, entity);
    requireRead(access);
    requireWrite(access);
    if (entity.process() != null) {
      throw new InvalidStateException(
          "A record with a process is kept; its owner withdraws it instead", "KEPT_BY_PROCESS");
    }
    String status = requireOpen(model, entity, access, id);
    MapSqlParameterSource params = new MapSqlParameterSource("id", id);
    String scope = scope(model, entity, access.writes(), params);
    int deleted;
    try {
      deleted = jdbc.update("DELETE FROM " + table(model, entity) + " WHERE id = :id AND " + scope,
          params);
    } catch (DataIntegrityViolationException e) {
      throw new InvalidStateException("Other records still refer to this record",
          "STILL_REFERENCED");
    }
    if (deleted == 0) {
      throw notFound(entity, id);
    }
    attachments.purge(model, entity, id);
    events.record(model, entity, id, "deleted", status, null, null, null);
  }

  // Takes an action of the process of a record. The row stays locked until the transaction ends,
  // so two actions on one record run one after the other.
  @Transactional
  public Map<String, Object> act(Model model, Entity entity, long id, String actionName,
      String note) {
    Action action = processOf(entity).definition().action(actionName)
        .orElseThrow(() -> new ResourceNotFoundException("Action " + actionName + " not found"));
    List<String> judged = new ArrayList<>(action.require());
    if (action.when() != null && !judged.contains(action.when().field())) {
      judged.add(action.when().field());
    }
    Map<String, Object> record = lock(model, entity, access(model, entity), id, judged);
    String me = user.currentUserId();
    rules.checkAction(entity, action, record, me, user.clientRoles(ModelClient.of(model.name())),
        note);
    String status = (String) record.get("status");
    if (ProcessFamily.WITHDRAW.equals(actionName)) {
      process.withdraw(model, entity, id, me);
    } else {
      process.act(model, entity, id, action, me);
    }
    Map<String, Object> changed = get(model, entity, id);
    events.record(model, entity, id, actionName, status, (String) changed.get("status"), note,
        null);
    return changed;
  }

  // Gives a record to a user who can handle it, or back to the handler role with null.
  @Transactional
  public Map<String, Object> assign(Model model, Entity entity, long id, String assignee) {
    processOf(entity);
    Access access = access(model, entity);
    Map<String, Object> current = get(model, entity, id);
    requireAssigner(model, entity, (String) current.get("status"));
    // The caller taking the record is checked like anyone else: handler role, unit, creator rule.
    String holder = null;
    if (assignee != null) {
      holder = handlers(model, entity, current).stream().filter(p -> p.id().equals(assignee))
          .findFirst().map(Person::name)
          .orElseThrow(() -> new ModelValidationException("assignee", "cannot handle this record"));
    }
    Map<String, Object> record = lock(model, entity, access, id);
    String status = (String) record.get("status");
    if (entity.process().definition().isTerminal(status)) {
      throw new InvalidStateException("The record is closed", "RECORD_CLOSED");
    }
    jdbc.update(
        "UPDATE " + table(model, entity)
            + " SET assignee = :assignee, updated_at = now(), version = version + 1 WHERE id = :id",
        new MapSqlParameterSource("assignee", assignee).addValue("id", id));
    process.assign(model, entity, id, status, assignee);
    events.record(model, entity, id, "assigned", status, status, null, holder);
    // Read without the scope: giving a record away can take it out of the caller's reach.
    return present(jdbc.queryForMap(
        "SELECT " + columns(entity) + " FROM " + table(model, entity) + " WHERE id = :id",
        new MapSqlParameterSource("id", id)));
  }

  // Who a record can be given to: the users with the handler role, of its unit when that role
  // reaches records by unit, except the creator unless the process allows it.
  @Transactional(readOnly = true)
  public List<Person> handlers(Model model, Entity entity, long id) {
    processOf(entity);
    Map<String, Object> record = get(model, entity, id);
    requireAssigner(model, entity, (String) record.get("status"));
    return handlers(model, entity, record);
  }

  // The roles that handle a record in its state, or every handler role when no action leaves it.
  private static Set<String> rolesHandling(Entity entity, String status) {
    Set<String> roles = entity.process().rolesIn(status);
    return roles.isEmpty() ? Set.copyOf(entity.process().handlers()) : roles;
  }

  private List<Person> handlers(Model model, Entity entity, Map<String, Object> record) {
    Model.Process definition = entity.process();
    Set<String> roles = rolesHandling(entity, (String) record.get("status"));
    Map<String, Person> people = new LinkedHashMap<>();
    for (String role : roles) {
      List<Person> holders =
          keycloak.enabledUsersWithClientRole(ModelClient.of(model.name()), role);
      if (entity.access().get(role) == Scope.UNIT) {
        Object unit = record.get(entity.unitField().orElseThrow().name());
        Set<String> members = unit == null ? Set.of()
            : keycloak.enabledGroupMembers(unit.toString()).stream().map(Person::id)
                .collect(Collectors.toSet());
        holders = holders.stream().filter(person -> members.contains(person.id())).toList();
      }
      holders.forEach(person -> people.putIfAbsent(person.id(), person));
    }
    return people.values().stream().filter(
        person -> definition.allowSelfDecision() || !person.id().equals(record.get("created_by")))
        .toList();
  }

  // The history of a record the caller can read.
  @Transactional(readOnly = true)
  public List<Map<String, Object>> events(Model model, Entity entity, long id) {
    get(model, entity, id);
    return events.of(model, entity, id).stream().map(DataService::present).toList();
  }

  // The records of an entity waiting for the caller: those its handler role can act on and nobody
  // else holds, and the caller's own records waiting for them. Overdue first, then oldest first.
  @Transactional(readOnly = true)
  public List<Map<String, Object>> waiting(Model model, Entity entity, int limit) {
    MapSqlParameterSource values = new MapSqlParameterSource("limit", limit);
    String where = waitingCondition(model, entity, values);
    if (where == null) {
      return List.of();
    }
    return jdbc.queryForList("SELECT " + columns(entity) + " FROM " + table(model, entity)
        + " WHERE " + where
        + " ORDER BY (handle_by IS NOT NULL AND handle_by < now()) DESC, created_at, id LIMIT :limit",
        values).stream().map(DataService::present).toList();
  }

  // How many records of an entity wait for the caller.
  @Transactional(readOnly = true)
  public long countWaiting(Model model, Entity entity) {
    MapSqlParameterSource values = new MapSqlParameterSource();
    String where = waitingCondition(model, entity, values);
    if (where == null) {
      return 0;
    }
    Long count = jdbc.queryForObject(
        "SELECT count(*) FROM " + table(model, entity) + " WHERE " + where, values, Long.class);
    return count == null ? 0 : count;
  }

  // The condition of the records waiting for the caller; null when nothing can wait.
  private String waitingCondition(Model model, Entity entity, MapSqlParameterSource values) {
    Access access = access(model, entity);
    if (entity.process() == null || !access.read()) {
      return null;
    }
    values.addValue("me", user.currentUserId());
    String reach = scope(model, entity, access.reads(), values);
    List<String> any = new ArrayList<>();
    Set<String> owner = fromStates(entity, Actor.OWNER);
    if (!owner.isEmpty()) {
      values.addValue("ownerStates", owner);
      any.add("(status IN (:ownerStates) AND created_by = :me AND " + reach + ")");
    }
    Set<String> handler = handlerStates(model, entity);
    if (!handler.isEmpty()) {
      values.addValue("handlerStates", handler);
      String notMine = entity.process().allowSelfDecision() ? "" : " AND created_by <> :me";
      any.add("(status IN (:handlerStates) AND (assignee IS NULL OR assignee = :me)" + notMine
          + " AND " + reach + ")");
    }
    return any.isEmpty() ? null : "(" + String.join(" OR ", any) + ")";
  }

  // The states in which a record waits for one of the roles the caller holds.
  private Set<String> handlerStates(Model model, Entity entity) {
    Set<String> mine = user.clientRoles(ModelClient.of(model.name()));
    return entity.process().definition().actions().stream()
        .filter(action -> action.by() == Actor.HANDLER
            && !Collections.disjoint(mine, entity.process().rolesFor(action)))
        .flatMap(action -> action.from().stream()).collect(Collectors.toSet());
  }

  private static Set<String> fromStates(Entity entity, Actor by) {
    // Withdrawing is always open to the owner; it is not work waiting for them.
    return entity.process().definition().actions().stream().filter(action -> action.by() == by)
        .filter(action -> !ProcessFamily.WITHDRAW.equals(action.name()))
        .flatMap(action -> action.from().stream()).collect(Collectors.toSet());
  }

  private static Model.Process processOf(Entity entity) {
    if (entity.process() == null) {
      throw new ResourceNotFoundException(entity.name() + " has no process");
    }
    return entity.process();
  }

  // Whoever writes to all rows or to those of its units may assign, and so may a role that handles
  // the record in its current state.
  private void requireAssigner(Model model, Entity entity, String status) {
    Set<Scope> writes = access(model, entity).writes();
    boolean coordinator = writes.contains(Scope.ALL) || writes.contains(Scope.UNIT);
    boolean handler = !Collections.disjoint(user.clientRoles(ModelClient.of(model.name())),
        rolesHandling(entity, status));
    if (!coordinator && !handler) {
      throw new ActionNotAllowedException("Your roles cannot assign this record", "NOT_ASSIGNER");
    }
  }

  // Locks a row the caller can read until the transaction ends.
  private Map<String, Object> lock(Model model, Entity entity, Access access, long id) {
    return lock(model, entity, access, id, List.of());
  }

  // Locks the row and reads what the rules judge from, with the fields an action requires.
  private Map<String, Object> lock(Model model, Entity entity, Access access, long id,
      List<String> required) {
    requireRead(access);
    MapSqlParameterSource values = new MapSqlParameterSource("id", id);
    String scope = scope(model, entity, access.reads(), values);
    String extra = required.stream().map(name -> ", " + ident(name)).collect(Collectors.joining());
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, created_by, assignee" + extra
        + " FROM " + table(model, entity) + " WHERE id = :id AND " + scope + " FOR UPDATE", values);
    if (rows.isEmpty()) {
      throw notFound(entity, id);
    }
    return rows.get(0);
  }

  // A user who reaches records through a parent record cannot attach one to a parent out of reach.
  private void requireReachableParent(Model model, Entity entity, Access access,
      Map<String, Object> values) {
    String via = entity.ownerVia();
    if (via == null || values.get(via) == null || reachesAll(access.writes())) {
      return;
    }
    Entity parent = model.entities().get(entity.fields().get(via).to());
    MapSqlParameterSource params = new MapSqlParameterSource("id", values.get(via));
    String condition = reach(model, parent, access.writes(), params);
    Integer found = jdbc.queryForObject(
        "SELECT count(*) FROM " + table(model, parent) + " WHERE id = :id AND " + condition, params,
        Integer.class);
    if (found == null || found == 0) {
      throw new ActionNotAllowedException("The " + via + " is out of your reach",
          "PARENT_OUT_OF_REACH");
    }
  }

  // A role that only reaches its units creates records in those units; one that reaches records
  // by assignment creates none.
  private void requireCreatableUnit(Entity entity, Access access, Map<String, Object> values) {
    Set<Scope> writes = access.writes();
    if (reachesAll(writes) || writes.contains(Scope.OWN) || entity.ownerVia() != null) {
      return;
    }
    if (!writes.contains(Scope.UNIT)) {
      throw new ActionNotAllowedException("Your roles cannot create this record", "CANNOT_CREATE");
    }
    requireOwnUnit(entity, values);
  }

  // Moving a record to another unit needs a role reaching all rows or being its creator.
  private void requireMovableUnit(Model model, Entity entity, Access access, long id,
      Map<String, Object> values) {
    if (entity.unitField().isEmpty() || !values.containsKey(entity.unitField().get().name())
        || reachesAll(access.writes())) {
      return;
    }
    List<String> creators =
        jdbc.queryForList("SELECT created_by FROM " + table(model, entity) + " WHERE id = :id",
            new MapSqlParameterSource("id", id), String.class);
    if (creators.isEmpty() || !user.currentUserId().equals(creators.get(0))) {
      requireOwnUnit(entity, values);
    }
  }

  private void requireOwnUnit(Entity entity, Map<String, Object> values) {
    Object unit = entity.unitField().map(field -> values.get(field.name())).orElse(null);
    if (unit == null || !user.groups().contains(unit)) {
      throw new ActionNotAllowedException("The unit is out of your reach", "UNIT_OUT_OF_REACH");
    }
  }

  // The values that changed and what the row held for them.
  private record Changed(Map<String, Object> values, Map<String, Object> before) {}

  // The given values that differ from what the row holds, compared by the database, with the
  // values the row held. The row stays locked until the transaction ends, so what is read here is
  // what the update replaces.
  private Changed changes(Model model, Entity entity, Access access, long id,
      Map<String, Object> values) {
    MapSqlParameterSource params = new MapSqlParameterSource("id", id);
    List<String> columns = new ArrayList<>();
    List<String> names = new ArrayList<>(values.keySet());
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      params.addValue(name, values.get(name));
      columns.add(ident(name) + " IS DISTINCT FROM CAST(:" + name + " AS "
          + DdlGenerator.sqlType(entity.fields().get(name).type()) + ") AS d" + i);
      columns.add(ident(name) + " AS o" + i);
    }
    String scope = scope(model, entity, access.writes(), params);
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + String.join(", ", columns)
        + " FROM " + table(model, entity) + " WHERE id = :id AND " + scope + " FOR UPDATE", params);
    if (rows.isEmpty()) {
      throw notFound(entity, id);
    }
    Map<String, Object> changed = new LinkedHashMap<>();
    Map<String, Object> before = new LinkedHashMap<>();
    for (int i = 0; i < names.size(); i++) {
      if (Boolean.TRUE.equals(rows.get(0).get("d" + i))) {
        changed.put(names.get(i), values.get(names.get(i)));
        before.put(names.get(i), rows.get(0).get("o" + i));
      }
    }
    return new Changed(changed, before);
  }

  // A record in a terminal state is frozen. The row stays locked until the transaction ends, so an
  // action cannot slip in between this check and the write.
  private String requireOpen(Model model, Entity entity, Access access, long id) {
    if (entity.process() == null) {
      return null;
    }
    MapSqlParameterSource values = new MapSqlParameterSource("id", id);
    String scope = scope(model, entity, access.writes(), values);
    List<String> statuses = jdbc.queryForList("SELECT status FROM " + table(model, entity)
        + " WHERE id = :id AND " + scope + " FOR UPDATE", values, String.class);
    if (statuses.isEmpty()) {
      throw notFound(entity, id);
    }
    if (entity.process().definition().isTerminal(statuses.get(0))) {
      throw new InvalidStateException("The record is closed", "RECORD_CLOSED");
    }
    return statuses.get(0);
  }

  private void requireRead(Access access) {
    if (!access.read()) {
      throw new ActionNotAllowedException("Your roles cannot reach this entity",
          "NO_ROLE_FOR_ENTITY");
    }
  }

  private void requireWrite(Access access) {
    if (!access.write()) {
      throw new ActionNotAllowedException("Your roles can only read this entity", "READ_ONLY");
    }
  }

  private static boolean reachesAll(Set<Scope> scopes) {
    return scopes.stream().anyMatch(Scope::reachesAll);
  }

  // The rows a request may touch: those any of the scopes reaches, on the entity itself or, for an
  // entity that belongs to another record, on that record.
  private String scope(Model model, Entity entity, Set<Scope> scopes,
      MapSqlParameterSource values) {
    if (reachesAll(scopes)) {
      return "TRUE";
    }
    return reach(model, entity, scopes, values);
  }

  // Follows owner_via up to the entity whose own columns decide who reaches a row.
  private String reach(Model model, Entity entity, Set<Scope> scopes,
      MapSqlParameterSource values) {
    if (entity.ownerVia() == null) {
      return rowCondition(entity, scopes, values);
    }
    Entity parent = model.entities().get(entity.fields().get(entity.ownerVia()).to());
    return ident(entity.ownerVia()) + " IN (SELECT id FROM " + table(model, parent) + " WHERE "
        + reach(model, parent, scopes, values) + ")";
  }

  private String rowCondition(Entity entity, Set<Scope> scopes, MapSqlParameterSource values) {
    List<String> any = new ArrayList<>();
    if (scopes.contains(Scope.OWN)) {
      values.addValue("sub", user.currentUserId());
      any.add("created_by = :sub");
    }
    if (scopes.contains(Scope.ASSIGNED) && entity.process() != null) {
      values.addValue("sub", user.currentUserId());
      any.add("assignee = :sub");
    }
    Set<String> units = user.groups();
    if (scopes.contains(Scope.UNIT) && entity.unitField().isPresent() && !units.isEmpty()) {
      values.addValue("units", units);
      any.add(ident(entity.unitField().get().name()) + " IN (:units)");
    }
    return any.isEmpty() ? "FALSE" : "(" + String.join(" OR ", any) + ")";
  }

  private void filters(Entity entity, Map<String, String> params, List<String> where,
      MapSqlParameterSource values) {
    int index = 0;
    for (Map.Entry<String, String> param : params.entrySet()) {
      if (!param.getKey().startsWith("filter.")) {
        continue;
      }
      String spec = param.getKey().substring("filter.".length());
      String operator = "eq";
      int dot = spec.lastIndexOf('.');
      String field = spec;
      if (dot > 0) {
        operator = spec.substring(dot + 1);
        field = spec.substring(0, dot);
      }
      FieldType type = columnType(entity, field);
      String name = "f" + index++;
      String column = ident(field);
      if ("in".equals(operator)) {
        List<Object> items = new ArrayList<>();
        for (String raw : param.getValue().split(",")) {
          items.add(parse(field, type, raw.trim()));
        }
        if (items.isEmpty()) {
          throw new ModelValidationException(param.getKey(), "in needs at least one value");
        }
        values.addValue(name, items);
        where.add(column + " IN (:" + name + ")");
      } else if ("like".equals(operator)) {
        if (type != FieldType.TEXT && type != FieldType.CHOICE && type != FieldType.USER
            && type != FieldType.UNIT) {
          throw new ModelValidationException(param.getKey(), "like applies to text fields");
        }
        values.addValue(name, "%"
            + param.getValue().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        where.add(column + " ILIKE :" + name);
      } else if (OPERATORS.containsKey(operator)) {
        values.addValue(name, parse(field, type, param.getValue()));
        where.add(column + " " + OPERATORS.get(operator) + " :" + name);
      } else {
        throw new ModelValidationException(param.getKey(), "unknown operator " + operator);
      }
    }
  }

  // The type of a column a request may filter or sort by; any other name is refused.
  private FieldType columnType(Entity entity, String column) {
    if ("id".equals(column) || "version".equals(column)) {
      return FieldType.REFERENCE;
    }
    boolean decision = entity.process() != null;
    if ("created_by".equals(column)
        || (decision && Set.of("status", "assignee", "decided_by").contains(column))) {
      return FieldType.TEXT;
    }
    if ("created_at".equals(column) || "updated_at".equals(column)
        || (decision && ("decided_at".equals(column) || "handle_by".equals(column)))) {
      return FieldType.DATETIME;
    }
    Field field = entity.fields().get(column);
    if (field == null) {
      throw new ModelValidationException(column, "unknown field");
    }
    return field.type();
  }

  private String columns(Entity entity) {
    List<String> columns =
        new ArrayList<>(List.of("id", "created_by", "created_at", "updated_at", "version").stream()
            .map(name -> ident(name)).toList());
    if (entity.process() != null) {
      columns.addAll(List.of("status", "assignee", "decided_by", "decided_at", "handle_by").stream()
          .map(name -> ident(name)).toList());
      columns.add("(handle_by IS NOT NULL AND handle_by < now()) AS " + ident("overdue"));
    }
    entity.fields().keySet().forEach(name -> columns.add(ident(name)));
    return String.join(", ", columns);
  }

  // Checks and converts the values of a request body to the types of the columns.
  private Map<String, Object> coerce(Entity entity, Map<String, Object> body, boolean creating) {
    Map<String, String> errors = new LinkedHashMap<>();
    Map<String, Object> values = new LinkedHashMap<>();
    for (Map.Entry<String, Object> item : body.entrySet()) {
      String name = item.getKey();
      if ("status".equals(name) && entity.process() != null) {
        throw new ActionNotAllowedException("The status is changed by the process only",
            "STATUS_BY_PROCESS_ONLY");
      }
      if (SYSTEM_COLUMNS.contains(name) || "status".equals(name)) {
        errors.put(name, "cannot be set");
        continue;
      }
      Field field = entity.fields().get(name);
      if (field == null) {
        errors.put(name, "unknown field");
        continue;
      }
      Object value = item.getValue();
      if (value == null || isEmpty(field, value)) {
        if (field.required()) {
          errors.put(name, "is required");
        } else {
          values.put(name, null);
        }
        continue;
      }
      try {
        values.put(name, convert(field, value));
      } catch (IllegalArgumentException e) {
        errors.put(name, e.getMessage());
      }
    }
    if (creating) {
      for (Field field : entity.fields().values()) {
        if (field.required() && field.defaultValue() == null && !values.containsKey(field.name())
            && !errors.containsKey(field.name())) {
          errors.put(field.name(), "is required");
        }
      }
    }
    if (!errors.isEmpty()) {
      throw new ModelValidationException(errors);
    }
    return values;
  }

  // No value: an empty string, which for a required text field includes one of only spaces.
  private static boolean isEmpty(Field field, Object value) {
    if (!(value instanceof String text)) {
      return false;
    }
    return field.type() == FieldType.TEXT ? field.required() && text.isBlank() : text.isEmpty();
  }

  private static Object convert(Field field, Object value) {
    return switch (field.type()) {
      case TEXT, USER -> {
        if (!(value instanceof String text)) {
          throw new IllegalArgumentException("must be text");
        }
        yield text;
      }
      case CHOICE, UNIT -> {
        if (!(value instanceof String text) || !field.options().contains(text)) {
          throw new IllegalArgumentException("must be one of " + field.options());
        }
        yield text;
      }
      case NUMBER -> {
        try {
          yield new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("must be a number");
        }
      }
      case DATETIME -> parseDate(String.valueOf(value));
      case REFERENCE -> parseId(String.valueOf(value));
      case FILE -> throw new IllegalArgumentException("is set by uploading a file");
    };
  }

  private static Object parse(String field, FieldType type, String raw) {
    try {
      return switch (type) {
        case NUMBER -> new BigDecimal(raw);
        case DATETIME -> parseDate(raw);
        case REFERENCE -> parseId(raw);
        default -> raw;
      };
    } catch (IllegalArgumentException e) {
      throw new ModelValidationException("filter." + field, e.getMessage());
    }
  }

  private static OffsetDateTime parseDate(String raw) {
    try {
      return OffsetDateTime.parse(raw);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("must be an ISO-8601 date and time with an offset");
    }
  }

  private static Long parseId(String raw) {
    try {
      return Long.valueOf(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("must be the id of a record");
    }
  }

  // A row as the API shows it: timestamps as instants.
  private static Map<String, Object> present(Map<String, Object> row) {
    Map<String, Object> shown = new LinkedHashMap<>();
    row.forEach(
        (key, value) -> shown.put(key, value instanceof Timestamp timestamp ? timestamp.toInstant()
            : value instanceof OffsetDateTime time ? time.toInstant() : value));
    return shown;
  }

  private RuntimeException translate(Entity entity, DataIntegrityViolationException e) {
    if (e.getMostSpecificCause() instanceof PSQLException psql
        && psql.getServerErrorMessage() != null) {
      String constraint = psql.getServerErrorMessage().getConstraint();
      String state = psql.getSQLState();
      if (constraint != null) {
        String prefix = switch (state == null ? "" : state) {
          case "23505" -> "uq_";
          case "23503" -> "fk_";
          case "23514" -> "ck_";
          default -> null;
        };
        if (prefix != null && constraint.startsWith(prefix + entity.name() + "_")) {
          String field = constraint.substring((prefix + entity.name() + "_").length());
          String message = switch (prefix) {
            case "uq_" -> "already exists";
            case "fk_" -> "refers to a record that does not exist";
            default -> "has a value that is not allowed";
          };
          return new ModelValidationException(field, message);
        }
      }
    }
    return new ModelValidationException("record", "breaks a rule of the model");
  }

  private static ResourceNotFoundException notFound(Entity entity, long id) {
    return new ResourceNotFoundException(entity.name() + " " + id + " not found");
  }

  private static int intParam(Map<String, String> params, String name, int fallback) {
    try {
      return params.containsKey(name) ? Integer.parseInt(params.get(name)) : fallback;
    } catch (NumberFormatException e) {
      throw new ModelValidationException(name, "must be a number");
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
