// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.documents;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Document;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.Model.Unit;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Prints a record through a template of its entity. The record is read with the same call as the
// record screen, so whoever cannot read the record cannot print it, and a template only ever sees
// the values of that record.
@Service
public class DocumentService {

  public record Printed(String file, byte[] content) {}

  private static final Map<String, String> COLUMN_LABELS =
      Map.of(
          "id", "Mã",
          "status", "Trạng thái",
          "created_at", "Ngày tạo",
          "updated_at", "Cập nhật lần cuối",
          "decided_at", "Ngày quyết định");

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private final DataService data;
  private final AuditRecorder audit;

  public DocumentService(DataService data, AuditRecorder audit) {
    this.data = data;
    this.audit = audit;
  }

  // The templates of the entity with the name of the download and whether this record can be
  // printed now.
  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(Model model, Entity entity, long id) {
    Map<String, Object> record = data.get(model, entity, id);
    return entity.documents().stream()
        .map(
            document -> {
              Map<String, Object> shown = new LinkedHashMap<>();
              shown.put("name", document.name());
              shown.put("label", document.label());
              shown.put("file", fileName(document, valueOf(model, entity, record)));
              shown.put("available", available(document, record));
              return shown;
            })
        .toList();
  }

  // Not read-only: the audit record is written after the record is read.
  public Printed print(Model model, Entity entity, long id, String name) {
    Document document =
        entity.documents().stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Document " + name + " not found"));
    Map<String, Object> record = data.get(model, entity, id);
    if (!available(document, record)) {
      throw new InvalidStateException(
          "The document " + name + " cannot be printed in the state " + record.get("status"),
          "DOCUMENT_NOT_AVAILABLE");
    }
    var value = valueOf(model, entity, record);
    byte[] content = DocumentRenderer.render(document, value, key -> labelOf(entity, key));
    audit.record(
        AuditEvent.success(
            "DOCUMENT_PRINTED",
            "RECORD",
            model.name() + "/" + entity.name() + "/" + id,
            Map.of("document", name)));
    return new Printed(fileName(document, value), content);
  }

  private static boolean available(Document document, Map<String, Object> record) {
    return document.when().isEmpty() || document.when().contains(record.get("status"));
  }

  private static String labelOf(Entity entity, String key) {
    return entity.field(key).map(Field::label).orElse(COLUMN_LABELS.getOrDefault(key, key));
  }

  // The name of the download: letters without accents, digits and . - _ only, ending in .pdf.
  private static String fileName(
      Document document, java.util.function.Function<String, String> value) {
    String base = DocumentRenderer.fill(document.file(), value);
    String plain =
        Normalizer.normalize(base, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .replaceAll("[^A-Za-z0-9._-]", "_");
    if (plain.length() > Document.MAX_FILE) {
      plain = plain.substring(0, Document.MAX_FILE);
    }
    return (plain.isBlank() ? document.name() : plain) + ".pdf";
  }

  // The text a placeholder or table row shows for a field or column of the record.
  private static java.util.function.Function<String, String> valueOf(
      Model model, Entity entity, Map<String, Object> record) {
    return key -> {
      Object raw = record.get(key);
      if (raw == null) {
        return "";
      }
      if ("status".equals(key) && entity.process() != null) {
        return entity.process().definition().states().stream()
            .filter(state -> state.name().equals(raw))
            .map(ProcessFamily.State::label)
            .findFirst()
            .orElse(String.valueOf(raw));
      }
      Field field = entity.fields().get(key);
      return field == null ? show(raw) : show(model, field, raw);
    };
  }

  private static String show(Model model, Field field, Object raw) {
    if (field.type() == FieldType.UNIT) {
      return model.units().stream()
          .filter(unit -> unit.name().equals(raw))
          .map(Unit::label)
          .findFirst()
          .orElse(String.valueOf(raw));
    }
    return show(raw);
  }

  private static String show(Object raw) {
    if (raw instanceof Instant instant) {
      return DATE_TIME.format(instant.atZone(ZoneId.systemDefault()));
    }
    if (raw instanceof Boolean flag) {
      return flag ? "Có" : "Không";
    }
    if (raw instanceof BigDecimal number) {
      return number.stripTrailingZeros().toPlainString();
    }
    if (raw instanceof List<?> items) {
      return items.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
    String text = String.valueOf(raw);
    if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return DATE.format(LocalDate.parse(text));
    }
    return text;
  }
}
