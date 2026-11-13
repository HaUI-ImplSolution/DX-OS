// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// What a report asks for, checked against the fields of the entity: the one field the rows are
// grouped by, the figures to compute and how many groups to return. A name that is not a field of
// the entity, or a figure that does not fit the type of its field, is refused before any query
// runs.
record ReportQuery(Group group, List<Metric> metrics, int limit) {

  static final int DEFAULT_LIMIT = 50;
  static final int MAX_LIMIT = 200;
  static final int MAX_METRICS = 6;

  private static final Set<String> FUNCTIONS = Set.of("sum", "avg", "min", "max");
  private static final Pattern METRIC =
      Pattern.compile("(count|sum|avg|min|max)(?:\\(([a-z_][a-z0-9_]*)\\))?");
  // The columns every record has that a report may group by.
  private static final String STATUS = "status";
  private static final String CREATED_AT = "created_at";

  // month is true when the rows are grouped by the month of a date.
  record Group(String field, boolean month, FieldType type) {

    String label() {
      return month ? field + ":month" : field;
    }
  }

  // function is count, sum, avg, min or max; field is null for count.
  record Metric(String function, String field) {

    String label() {
      return field == null ? function : function + "(" + field + ")";
    }
  }

  static ReportQuery parse(Entity entity, Map<String, String> params) {
    return new ReportQuery(
        group(entity, params.get("groupBy")),
        metrics(entity, params.get("metrics")),
        limit(params));
  }

  private static Group group(Entity entity, String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    boolean month = raw.endsWith(":month");
    String name = month ? raw.substring(0, raw.length() - ":month".length()) : raw;
    if (STATUS.equals(name) && entity.process() != null) {
      return refuseMonth(month, new Group(name, false, FieldType.CHOICE));
    }
    if (CREATED_AT.equals(name)) {
      if (!month) {
        throw new ModelValidationException(
            "groupBy", "created_at is grouped by month: use created_at:month");
      }
      return new Group(name, true, FieldType.DATETIME);
    }
    Field field =
        entity
            .field(name)
            .orElseThrow(() -> new ModelValidationException("groupBy", "unknown field " + name));
    return switch (field.type()) {
      case CHOICE, UNIT, BOOLEAN -> {
        if (field.multiple()) {
          throw new ModelValidationException(
              "groupBy", name + " holds several values and cannot be grouped");
        }
        yield refuseMonth(month, new Group(name, false, field.type()));
      }
      case DATE, DATETIME -> {
        if (!month) {
          throw new ModelValidationException(
              "groupBy", name + " is grouped by month: use " + name + ":month");
        }
        yield new Group(name, true, field.type());
      }
      default ->
          throw new ModelValidationException(
              "groupBy", name + " cannot be grouped: use a choice, unit, boolean or date field");
    };
  }

  private static Group refuseMonth(boolean month, Group group) {
    if (month) {
      throw new ModelValidationException("groupBy", ":month applies to a date field only");
    }
    return group;
  }

  private static List<Metric> metrics(Entity entity, String raw) {
    List<Metric> metrics = new ArrayList<>();
    String[] items = raw == null || raw.isBlank() ? new String[] {"count"} : raw.split(",");
    if (items.length > MAX_METRICS) {
      throw new ModelValidationException("metrics", "at most " + MAX_METRICS + " figures");
    }
    for (String item : items) {
      Matcher match = METRIC.matcher(item.trim());
      if (!match.matches()) {
        throw new ModelValidationException("metrics", "unknown figure " + item.trim());
      }
      String function = match.group(1);
      String field = match.group(2);
      if (FUNCTIONS.contains(function)) {
        if (field == null) {
          throw new ModelValidationException(
              "metrics", function + " needs a number field: " + function + "(field)");
        }
        Field target =
            entity
                .field(field)
                .orElseThrow(
                    () -> new ModelValidationException("metrics", "unknown field " + field));
        if (target.type() != FieldType.NUMBER) {
          throw new ModelValidationException("metrics", field + " is not a number field");
        }
      } else if (field != null) {
        throw new ModelValidationException("metrics", "count takes no field");
      }
      Metric metric = new Metric(function, field);
      if (metrics.contains(metric)) {
        throw new ModelValidationException(
            "metrics", "figure " + metric.label() + " is asked twice");
      }
      metrics.add(metric);
    }
    return metrics;
  }

  private static int limit(Map<String, String> params) {
    String raw = params.get("limit");
    if (raw == null || raw.isBlank()) {
      return DEFAULT_LIMIT;
    }
    try {
      return Math.max(1, Math.min(MAX_LIMIT, Integer.parseInt(raw.trim())));
    } catch (NumberFormatException e) {
      throw new ModelValidationException("limit", "must be a whole number");
    }
  }
}
