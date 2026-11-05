// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.scheduling.JobLock;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Copies the head of the history chain (id, hash, time) to the object store at a set interval, one
// JSON line per anchor. The store is outside the database, so rewriting the whole chain in the
// database no longer matches the anchors.
@Component
public class HistoryAnchor {

  static final String KEY = "history/anchors.jsonl";

  private final HistoryChain chain;
  private final ObjectStorage storage;
  private final JobLock lock;
  private final ObjectMapper json;

  public HistoryAnchor(HistoryChain chain, ObjectStorage storage, JobLock lock, ObjectMapper json) {
    this.chain = chain;
    this.storage = storage;
    this.lock = lock;
    this.json = json;
  }

  @Scheduled(fixedDelayString = "${engine.history.anchor-ms:3600000}")
  public void run() {
    lock.runIfFree("engine-history-anchor", this::anchor);
  }

  // Adds an anchor for the head of the chain unless the last anchor already holds it; returns the
  // anchor, or null when there is no history yet.
  public synchronized Map<String, Object> anchor() {
    Map<String, Object> head = chain.head();
    if (head == null || head.get("hash") == null) {
      return null;
    }
    List<Map<String, Object>> anchors = anchors();
    long id = ((Number) head.get("id")).longValue();
    if (!anchors.isEmpty() && ((Number) anchors.getLast().get("id")).longValue() == id) {
      return anchors.getLast();
    }
    Map<String, Object> anchor = new LinkedHashMap<>();
    anchor.put("id", id);
    anchor.put("hash", head.get("hash"));
    anchor.put("at", String.valueOf(head.get("at")));
    StringBuilder content = new StringBuilder();
    storage.getObject(KEY)
        .ifPresent(bytes -> content.append(new String(bytes, StandardCharsets.UTF_8)));
    content.append(json.writeValueAsString(anchor)).append('\n');
    storage.putObject(KEY, content.toString().getBytes(StandardCharsets.UTF_8),
        "application/x-ndjson", Map.of());
    return anchor;
  }

  public List<Map<String, Object>> anchors() {
    List<Map<String, Object>> anchors = new ArrayList<>();
    storage.getObject(KEY).ifPresent(bytes -> {
      for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
        if (!line.isBlank()) {
          anchors.add(json.readValue(line, new TypeReference<Map<String, Object>>() {}));
        }
      }
    });
    return anchors;
  }
}
