// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.engine.event.OutboxHandler;
import java.util.Map;
import org.springframework.stereotype.Component;

// Deletes the object of an attachment row that was dropped. An object that is already gone counts
// as deleted, so a retry is harmless.
@Component
public class FileDeleteHandler implements OutboxHandler {

  static final String TOPIC = "file.delete";

  private final ObjectStorage storage;

  public FileDeleteHandler(ObjectStorage storage) {
    this.storage = storage;
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public void deliver(Map<String, Object> payload) {
    storage.deleteObject((String) payload.get("key"));
  }
}
