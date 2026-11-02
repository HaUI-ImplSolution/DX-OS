// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.table;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.process.ProcessService;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Scope;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads and writes the records of any entity of any applied model. Which tables, columns, rules
// and roles apply all come from the model; the SQL text is built from its validated names and
// every value goes through a parameter.
@Service
public class DataService {

  public record Access(boolean read, boolean readAll, boolean write, boolean writeAll) {}

  private static final int MAX_PAGE_SIZE = 200;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final Set<String> SYSTEM_COLUMNS =
      Set.of("id", "created_by", "created_at", "updated_at", "version");
  private static final Map<String, String> OPERATORS =
      Map.of("eq", "=", "ne", "<>", "lt", "<", "lte", "<=", "gt", ">", "gte", ">=");

  private final NamedParameterJdbcTemplate jdbc;
  private final UserContext user;
  private final ProcessService process;

  public DataService(NamedParameterJdbcTemplate jdbc, UserContext user, ProcessService process) {
    this.jdbc = jdbc;
    this.user = user;
    this.process = process;
  }

  public Access access(Entity entity) {
    Set<String> roles = user.roles();
    boolean read = false;
    boolean readAll = false;
    boolean write = false;
    boolean writeAll = false;
    for (Map.Entry<String, Scope> grant : entity.access().entrySet()) {
      if (!roles.contains(grant.getKey())) {
        continue;
      }
      Scope scope = grant.getValue();
      read = true;
      readAll |= scope.reachesAll();
      if (scope.canWrite()) {
        write = true;
        writeAll |= scope == Scope.ALL;
      }
    }
    return new Access(read, readAll, write, writeAll);
  }

  public boolean canDecide(Entity entity) {
    return entity.process() != null && user.roles().contains(entity.process().approver());
  }

  @Transactional(readOnly = true)
  public PageResponse<Map<String, Object>> list(Model model, Entity entity,
      Map<String, String> params) {
    Access access = access(entity);
    requireRead(access);
    MapSqlParameterSource values = new MapSqlParameterSource();
    List<String> where = new ArrayList<>();
    where.add(scope(model, entity, access.readAll(), values));
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

  @Transactional(readOnly = true)
  public Map<String, Object> get(Model model, Entity entity, long id) {
    Access access = access(entity);
    requireRead(access);
    MapSqlParameterSource values = new MapSqlParameterSource("id", id);
    String scope = scope(model, entity, access.readAll(), values);
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + columns(entity) + " FROM "
        + table(model, entity) + " WHERE id = :id AND " + scope, values);
    if (rows.isEmpty()) {
      throw notFound(entity, id);
    }
    return present(rows.get(0));
  }

  @Transactional
  public Map<String, Object> create(Model model, Entity entity, Map<String, Object> body) {
    Access access = access(entity);
    requireRead(access);
    requireWrite(access);
    Map<String, Object> values = coerce(entity, body, true);
    requireOwnedParent(model, entity, access, values);
    MapSqlParameterSource params = new MapSqlParameterSource("created_by", user.currentUserId());
    List<String> names = new ArrayList<>(List.of("created_by"));
    List<String> markers = new ArrayList<>(List.of(":created_by"));
    values.forEach((name, value) -> {
      names.add(ident(name));
      markers.add(":" + name);
      params.addValue(name, value);
    });
    Map<String, Object> row;
    try {
      row = jdbc.queryForMap("INSERT INTO " + table(model, entity) + " (" + String.join(", ", names)
          + ") VALUES (" + String.join(", ", markers) + ") RETURNING " + columns(entity), params);
    } catch (DataIntegrityViolationException e) {
      throw translate(entity, e);
    }
    long id = ((Number) row.get("id")).longValue();
    if (entity.process() != null) {
      process.start(model, entity, id);
      row = jdbc.queryForMap(
          "SELECT " + columns(entity) + " FROM " + table(model, entity) + " WHERE id = :id",
          new MapSqlParameterSource("id", id));
    }
    return present(row);
  }

  @Transactional
  public Map<String, Object> update(Model model, Entity entity, long id, Map<String, Object> body) {
    Access access = access(entity);
    requireRead(access);
    requireWrite(access);
    Map<String, Object> values = coerce(entity, body, false);
    if (values.isEmpty()) {
      throw new ModelValidationException("body", "no field to update");
    }
    requireOwnedParent(model, entity, access, values);
    MapSqlParameterSource params = new MapSqlParameterSource("id", id);
    List<String> sets = new ArrayList<>();
    values.forEach((name, value) -> {
      sets.add(ident(name) + " = :" + name);
      params.addValue(name, value);
    });
    String scope = scope(model, entity, access.writeAll(), params);
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList("UPDATE " + table(model, entity) + " SET " + String.join(", ", sets)
          + ", updated_at = now(), version = version + 1 WHERE id = :id " + "AND " + scope
          + " RETURNING " + columns(entity), params);
    } catch (DataIntegrityViolationException e) {
      throw translate(entity, e);
    }
    if (rows.isEmpty()) {
      throw notFound(entity, id);
    }
    return present(rows.get(0));
  }

  @Transactional
  public void delete(Model model, Entity entity, long id) {
    Access access = access(entity);
    requireRead(access);
    requireWrite(access);
    MapSqlParameterSource params = new MapSqlParameterSource("id", id);
    String scope = scope(model, entity, access.writeAll(), params);
    if (entity.process() != null) {
      process.cancel(model, entity, id);
    }
    int deleted;
    try {
      deleted = jdbc.update("DELETE FROM " + table(model, entity) + " WHERE id = :id AND " + scope,
          params);
    } catch (DataIntegrityViolationException e) {
      throw new InvalidStateException("Other records still refer to this record");
    }
    if (deleted == 0) {
      throw notFound(entity, id);
    }
  }

  @Transactional
  public Map<String, Object> decide(Model model, Entity entity, long id, String outcome) {
    if (entity.process() == null) {
      throw new ResourceNotFoundException(entity.name() + " has no process");
    }
    if (!canDecide(entity)) {
      throw new ActionNotAllowedException("Only " + entity.process().approver() + " can decide");
    }
    if (!"approve".equals(outcome) && !"reject".equals(outcome)) {
      throw new ModelValidationException("outcome", "must be approve or reject");
    }
    get(model, entity, id);
    process.decide(model, entity, id, outcome);
    return get(model, entity, id);
  }

  // A user who owns records through their parent record cannot attach one to somebody else's.
  private void requireOwnedParent(Model model, Entity entity, Access access,
      Map<String, Object> values) {
    String via = entity.ownerVia();
    if (via == null || access.writeAll() || values.get(via) == null) {
      return;
    }
    Entity parent = model.entities().get(entity.fields().get(via).to());
    Integer owned = jdbc.queryForObject(
        "SELECT count(*) FROM " + table(model, parent) + " WHERE id = :id AND created_by = :sub",
        new MapSqlParameterSource("id", values.get(via)).addValue("sub", user.currentUserId()),
        Integer.class);
    if (owned == null || owned == 0) {
      throw new ActionNotAllowedException("The " + via + " is not yours");
    }
  }

  private void requireRead(Access access) {
    if (!access.read()) {
      throw new ActionNotAllowedException("Your roles cannot reach this entity");
    }
  }

  private void requireWrite(Access access) {
    if (!access.write()) {
      throw new ActionNotAllowedException("Your roles can only read this entity");
    }
  }

  // The rows a request may touch: all of them, or those the user owns, directly or through the
  // record the entity belongs to.
  private String scope(Model model, Entity entity, boolean all, MapSqlParameterSource values) {
    if (all) {
      return "TRUE";
    }
    values.addValue("sub", user.currentUserId());
    if (entity.ownerVia() == null) {
      return "created_by = :sub";
    }
    Entity parent = model.entities().get(entity.fields().get(entity.ownerVia()).to());
    return ident(entity.ownerVia()) + " IN (SELECT id FROM " + table(model, parent)
        + " WHERE created_by = :sub)";
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
        values.addValue(name, items);
        where.add(column + " IN (:" + name + ")");
      } else if ("like".equals(operator)) {
        if (type != FieldType.TEXT && type != FieldType.CHOICE && type != FieldType.USER) {
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
    if ("created_by".equals(column) || ("status".equals(column) && entity.process() != null)) {
      return FieldType.TEXT;
    }
    if ("created_at".equals(column) || "updated_at".equals(column)) {
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
        new ArrayList<>(List.of("id", "created_by", "created_at", "updated_at", "version"));
    if (entity.process() != null) {
      columns.add("status");
    }
    columns.addAll(entity.fields().keySet());
    return columns.stream().map(name -> ident(name))
        .collect(java.util.stream.Collectors.joining(", "));
  }

  // Checks and converts the values of a request body to the types of the columns.
  private Map<String, Object> coerce(Entity entity, Map<String, Object> body, boolean creating) {
    Map<String, String> errors = new LinkedHashMap<>();
    Map<String, Object> values = new LinkedHashMap<>();
    for (Map.Entry<String, Object> item : body.entrySet()) {
      String name = item.getKey();
      if ("status".equals(name) && entity.process() != null) {
        throw new ActionNotAllowedException("The status is changed by the process only");
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
      if (value == null
          || (value instanceof String text && text.isEmpty() && field.type() != FieldType.TEXT)) {
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
        if (field.required() && !values.containsKey(field.name())
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

  private static Object convert(Field field, Object value) {
    return switch (field.type()) {
      case TEXT, USER -> {
        if (!(value instanceof String text)) {
          throw new IllegalArgumentException("must be text");
        }
        yield text;
      }
      case CHOICE -> {
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
