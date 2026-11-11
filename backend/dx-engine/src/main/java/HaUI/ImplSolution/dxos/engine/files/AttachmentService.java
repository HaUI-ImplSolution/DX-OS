// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.storage.FileStore;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.data.EventLog;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// The files attached to the records of a model. Who may see or change a file is who may see or
// change its record; a file can be taken away only by whoever added it.
@Service
public class AttachmentService {

  private static final int NAME_LENGTH = 200;

  private final JdbcTemplate jdbc;
  private final DataService data;
  private final FileStore files;
  private final ObjectStorage storage;
  private final EventLog events;
  private final UserContext user;
  private final AttachmentCleaner cleaner;
  private final int maxPerRecord;

  public AttachmentService(JdbcTemplate jdbc, DataService data, FileStore files,
      ObjectStorage storage, EventLog events, UserContext user, AttachmentCleaner cleaner,
      @Value("${engine.files.max-per-record:20}") int maxPerRecord) {
    this.jdbc = jdbc;
    this.data = data;
    this.files = files;
    this.storage = storage;
    this.events = events;
    this.user = user;
    this.cleaner = cleaner;
    this.maxPerRecord = maxPerRecord;
  }

  @Transactional
  public Map<String, Object> add(Model model, Entity entity, long id, String field,
      MultipartFile upload) {
    requireFileField(entity, field);
    data.requireChangeable(model, entity, id);
    Integer held = jdbc.queryForObject("SELECT count(*) FROM engine.attachment WHERE model = ? "
        + "AND entity = ? AND record_id = ?", Integer.class, model.name(), entity.name(), id);
    if (held != null && held >= maxPerRecord) {
      throw new ModelValidationException("file",
          "a record holds at most " + maxPerRecord + " files");
    }
    String name = cleanName(upload.getOriginalFilename());
    String key;
    try {
      key = files.store("attachment", upload.getBytes(), name, user.currentUserId());
    } catch (IOException e) {
      throw new ModelValidationException("file", "cannot be read");
    } catch (IllegalArgumentException e) {
      throw new ModelValidationException("file", e.getMessage());
    }
    cleaner.deleteIfRolledBack(key);
    String type =
        upload.getContentType() == null ? "application/octet-stream" : upload.getContentType();
    Long attachment = jdbc.queryForObject(
        "INSERT INTO engine.attachment (model, entity, record_id, field, object_key, filename, "
            + "content_type, size, uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
        Long.class, model.name(), entity.name(), id, field, key, name, type, upload.getSize(),
        user.currentUserId());
    jdbc.update("UPDATE " + Sql.table(model, entity) + " SET " + Sql.ident(field)
        + " = ?, updated_at = now(), version = version + 1 WHERE id = ?", name, id);
    record(model, entity, id, "file_added", name);
    return describe(attachment, field, name, upload.getSize(), type, user.currentUserId(), null);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(Model model, Entity entity, long id) {
    data.get(model, entity, id);
    return jdbc.queryForList("SELECT id, field, filename AS name, size, content_type, uploaded_by,"
        + " uploaded_at FROM engine.attachment WHERE model = ? AND entity = ? AND record_id = ? "
        + "ORDER BY id", model.name(), entity.name(), id);
  }

  // A link that works for a few minutes.
  @Transactional(readOnly = true)
  public Map<String, Object> link(Model model, Entity entity, long id, long fileId) {
    data.get(model, entity, id);
    Map<String, Object> row = find(model, entity, id, fileId);
    return Map.of("name", row.get("filename"), "url",
        storage.presignedDownloadUrl((String) row.get("object_key"), (String) row.get("filename")));
  }

  @Transactional
  public void remove(Model model, Entity entity, long id, long fileId) {
    data.requireChangeable(model, entity, id);
    Map<String, Object> row = find(model, entity, id, fileId);
    if (!user.currentUserId().equals(row.get("uploaded_by"))) {
      throw new ActionNotAllowedException("Only the user who added a file can remove it",
          "FILE_ADDER_ONLY");
    }
    jdbc.update("DELETE FROM engine.attachment WHERE id = ?", fileId);
    cleaner.deleteLater(model.name(), entity.name(), id, (String) row.get("object_key"));
    String latest = jdbc.query(
        "SELECT filename FROM engine.attachment WHERE model = ? AND entity = ? AND record_id = ? "
            + "AND field = ? ORDER BY id DESC LIMIT 1",
        (rs, i) -> rs.getString(1), model.name(), entity.name(), id, row.get("field")).stream()
        .findFirst().orElse(null);
    jdbc.update(
        "UPDATE " + Sql.table(model, entity) + " SET " + Sql.ident((String) row.get("field"))
            + " = ?, updated_at = now(), version = version + 1 WHERE id = ?",
        latest, id);
    record(model, entity, id, "file_removed", (String) row.get("filename"));
  }

  private Map<String, Object> find(Model model, Entity entity, long id, long fileId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT field, object_key, filename, uploaded_by FROM engine.attachment WHERE id = ? "
            + "AND model = ? AND entity = ? AND record_id = ?",
        fileId, model.name(), entity.name(), id);
    if (rows.isEmpty()) {
      throw new ResourceNotFoundException("File " + fileId + " not found");
    }
    return rows.get(0);
  }

  private void record(Model model, Entity entity, long id, String action, String name) {
    Object status = data.get(model, entity, id).get("status");
    events.record(model, entity, id, action, (String) status, (String) status, null, name);
  }

  private static void requireFileField(Entity entity, String field) {
    if (entity.field(field).map(f -> f.type() != FieldType.FILE).orElse(true)) {
      throw new ModelValidationException("field",
          field + " is not a file field of " + entity.name());
    }
  }

  private static String cleanName(String original) {
    String name = original == null ? "" : original.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).strip();
    return name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) : name;
  }

  private static Map<String, Object> describe(Long id, String field, String name, long size,
      String type, String by, Object at) {
    java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
    map.put("id", id);
    map.put("field", field);
    map.put("name", name);
    map.put("size", size);
    map.put("content_type", type);
    map.put("uploaded_by", by);
    map.put("uploaded_at", at);
    return map;
  }
}
