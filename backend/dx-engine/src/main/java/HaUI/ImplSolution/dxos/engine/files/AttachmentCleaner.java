// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.engine.event.Outbox;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Keeps the object store in step with the attachment rows. An object whose row is dropped is
// deleted through the outbox after a set delay, so it is retried until the store answers and a
// backup that dumps the rows before it copies the store still finds every object it names. An
// object stored by a change that rolled back has no row and is deleted at once.
@Component
public class AttachmentCleaner {

  private static final Logger log = LoggerFactory.getLogger(AttachmentCleaner.class);

  private final JdbcTemplate jdbc;
  private final ObjectStorage storage;
  private final Outbox outbox;
  private final Duration delay;

  public AttachmentCleaner(
      JdbcTemplate jdbc,
      ObjectStorage storage,
      Outbox outbox,
      @Value("${engine.files.delete-delay:24h}") Duration delay) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.outbox = outbox;
    this.delay = delay.isNegative() ? Duration.ZERO : delay;
  }

  // Drops every file of a record: the rows now, the objects once the delay has passed.
  public void purge(Model model, Entity entity, long id) {
    List<String> keys =
        jdbc.queryForList(
            "DELETE FROM engine.attachment WHERE model = ? AND entity = ? AND record_id = ? "
                + "RETURNING object_key",
            String.class,
            model.name(),
            entity.name(),
            id);
    keys.forEach(key -> deleteLater(model.name(), entity.name(), id, key));
  }

  // Drops every file of an entity whose table is going away, in the same way.
  public int purgeEntity(String model, String entity) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "DELETE FROM engine.attachment WHERE model = ? AND entity = ? "
                + "RETURNING object_key, record_id",
            model,
            entity);
    rows.forEach(
        row ->
            deleteLater(
                model,
                entity,
                ((Number) row.get("record_id")).longValue(),
                (String) row.get("object_key")));
    return rows.size();
  }

  // Deletes the object of a dropped row after the delay; nothing is deleted when the change rolls
  // back, because the message is written in its transaction.
  public void deleteLater(String model, String entity, long recordId, String key) {
    outbox.publishLater(
        FileDeleteHandler.TOPIC,
        FileDeleteHandler.TOPIC + ":" + key,
        model,
        entity,
        recordId,
        Map.of("key", key),
        delay);
  }

  public void deleteIfRolledBack(String key) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              try {
                storage.deleteObject(key);
              } catch (RuntimeException e) {
                log.warn("Could not delete object {}: {}", key, e.getMessage());
              }
            }
          }
        });
  }
}
