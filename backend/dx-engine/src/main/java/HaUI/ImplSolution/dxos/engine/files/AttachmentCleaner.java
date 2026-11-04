// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Keeps the object store in step with the attachment rows: an object is deleted once the change
// that dropped its row has committed, and one stored by a change that rolled back is deleted too.
@Component
public class AttachmentCleaner {

  private static final Logger log = LoggerFactory.getLogger(AttachmentCleaner.class);

  private final JdbcTemplate jdbc;
  private final ObjectStorage storage;

  public AttachmentCleaner(JdbcTemplate jdbc, ObjectStorage storage) {
    this.jdbc = jdbc;
    this.storage = storage;
  }

  // Drops every file of a record, rows now and objects after the commit.
  public void purge(Model model, Entity entity, long id) {
    List<String> keys = jdbc.queryForList(
        "DELETE FROM engine.attachment WHERE model = ? AND entity = ? AND record_id = ? "
            + "RETURNING object_key",
        String.class, model.name(), entity.name(), id);
    keys.forEach(this::deleteAfterCommit);
  }

  // Drops every file of an entity whose table is going away: the rows, then the objects. The caller
  // is not in a transaction, so the rows are gone for good when the objects are deleted.
  public int purgeEntity(String model, String entity) {
    List<String> keys = jdbc.queryForList(
        "DELETE FROM engine.attachment WHERE model = ? AND entity = ? RETURNING object_key",
        String.class, model, entity);
    keys.forEach(this::delete);
    return keys.size();
  }

  public void deleteAfterCommit(String key) {
    whenDone(key, true);
  }

  public void deleteIfRolledBack(String key) {
    whenDone(key, false);
  }

  private void whenDone(String key, boolean committed) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if ((status == STATUS_COMMITTED) == committed) {
          delete(key);
        }
      }
    });
  }

  private void delete(String key) {
    try {
      storage.deleteObject(key);
    } catch (RuntimeException e) {
      log.warn("Could not delete object {}: {}", key, e.getMessage());
    }
  }
}
