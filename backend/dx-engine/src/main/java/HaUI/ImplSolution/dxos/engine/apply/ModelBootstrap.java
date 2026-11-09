// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.engine.apply.ApplyService.Actor;
import HaUI.ImplSolution.dxos.engine.apply.ApplyService.Applied;
import HaUI.ImplSolution.dxos.engine.apply.ApplyService.Plan;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Level;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelException;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Applies the model files named by engine.model-file (names separated by commas, each resolved
// against engine.model-dir when that is set) once the service is up, so that a system can start
// from its files. It waits for Keycloak once first, since an apply that meets no Keycloak stops
// half way. A file is applied only if it was never applied before, the plan is safe, and no apply
// of that model is stopped; every other case is logged and left to the control panel, and the
// service keeps running. The files are taken in order and one that fails does not stop the next.
// A model named by an earlier file of the same run is not applied again by a later one.
//
// Each file ends with exactly one log line "Model bootstrap: <result> <detail>", where the result
// is applied, unchanged, skipped or failed; tools that start the system read those lines. Any
// other line of the run does not put one of those words right after "Model bootstrap: ".
@Component
public class ModelBootstrap {

  private static final Logger log = LoggerFactory.getLogger(ModelBootstrap.class);
  private static final Actor ACTOR = new Actor("bootstrap", "Bootstrap");
  private static final long RETRY_MILLIS = 5_000;

  private final ApplyService apply;
  private final KeycloakAdminClient keycloak;
  private final ModelRegistry registry;
  private final JdbcTemplate jdbc;
  private final List<String> files;
  private final String dir;
  private final long waitSeconds;

  public ModelBootstrap(ApplyService apply, KeycloakAdminClient keycloak, ModelRegistry registry,
      JdbcTemplate jdbc, @Value("${engine.model-file:}") String files,
      @Value("${engine.model-dir:}") String dir,
      @Value("${engine.model-file-wait-seconds:300}") long waitSeconds) {
    this.apply = apply;
    this.keycloak = keycloak;
    this.registry = registry;
    this.jdbc = jdbc;
    this.files = Arrays.stream((files == null ? "" : files).split(",")).map(String::trim)
        .filter(name -> !name.isEmpty()).toList();
    this.dir = dir == null ? "" : dir.trim();
    this.waitSeconds = waitSeconds;
  }

  // A thread of its own, so that the health check does not wait for Keycloak.
  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (files.isEmpty()) {
      return;
    }
    Thread thread = new Thread(this::run, "model-bootstrap");
    thread.setDaemon(true);
    thread.start();
  }

  void run() {
    Map<String, String> contents = new LinkedHashMap<>();
    for (String name : files) {
      String content = read(name);
      if (content != null) {
        contents.put(name, content);
      }
    }
    if (contents.isEmpty() || !waitForKeycloak(contents.keySet())) {
      return;
    }
    Set<String> seen = new HashSet<>();
    contents.forEach((name, content) -> {
      try {
        consider(name, content, seen);
      } catch (RuntimeException e) {
        log.error("Model bootstrap: failed {} {}", name, e.getMessage(), e);
      }
    });
  }

  private String read(String name) {
    Path path = dir.isEmpty() ? Path.of(name) : Path.of(dir).resolve(name);
    try {
      return Files.readString(path);
    } catch (IOException e) {
      log.error("Model bootstrap: failed {} cannot be read: {}", name, e.getMessage());
      return null;
    }
  }

  // False after a failed line for each file that was waiting.
  private boolean waitForKeycloak(Collection<String> waiting) {
    long deadline = System.nanoTime() + waitSeconds * 1_000_000_000L;
    boolean told = false;
    while (true) {
      try {
        keycloak.searchAccounts("", 0, 1);
        return true;
      } catch (RuntimeException e) {
        if (System.nanoTime() > deadline) {
          waiting.forEach(name -> log.error(
              "Model bootstrap: failed {} Keycloak did not answer in {} seconds: {}", name,
              waitSeconds, e.getMessage()));
          return false;
        }
        if (!told) {
          log.info("Model bootstrap: waiting for Keycloak, {}", e.getMessage());
          told = true;
        }
      }
      try {
        Thread.sleep(RETRY_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        waiting.forEach(name -> log
            .error("Model bootstrap: failed {} was interrupted while waiting for Keycloak", name));
        return false;
      }
    }
  }

  void consider(String file, String content, Set<String> seen) {
    Model model;
    try {
      model = ModelParser.parse(content);
    } catch (ModelException e) {
      log.error("Model bootstrap: failed {} fails the checks: {}", file, e.problems());
      return;
    }
    String name = model.name();
    if (!seen.add(name)) {
      log.warn("Model bootstrap: skipped {} an earlier file of this start already names this "
          + "model; {} is not applied", name, file);
      return;
    }
    if (registry.isHeld(name)) {
      log.warn("Model bootstrap: skipped {} has an apply that stopped; continue or give it up in "
          + "the control panel", name);
      return;
    }
    String hash = ApplyService.hash(content);
    List<String> running = jdbc.queryForList(
        "SELECT hash FROM engine.model_version WHERE model = ? ORDER BY version DESC LIMIT 1",
        String.class, name);
    if (!running.isEmpty() && hash.equals(running.get(0))) {
      log.info("Model bootstrap: unchanged {} already runs the file", name);
      return;
    }
    Integer before = jdbc.queryForObject(
        "SELECT count(*) FROM engine.model_version WHERE model = ? AND hash = ?", Integer.class,
        name, hash);
    if (before != null && before > 0) {
      log.warn("Model bootstrap: skipped {} the file was applied before and is not the running "
          + "version, so the running version stays", name);
      return;
    }
    Plan plan = apply.plan(content);
    if ("unchanged".equals(plan.action())) {
      log.info("Model bootstrap: unchanged {} already runs the file", name);
      return;
    }
    if ("rejected".equals(plan.action()) || plan.level() != Level.SAFE) {
      log.warn(
          "Model bootstrap: skipped {} the plan is at level {} with rejections {}, questions "
              + "{} and {} rules broken by existing rows; review and apply it in the control panel",
          name, plan.level(), plan.rejections(), plan.questions(), plan.violations().size());
      return;
    }
    try {
      Applied result = apply.apply(content, ACTOR, plan.fromVersion(), Level.SAFE);
      log.info("Model bootstrap: applied {} version {}", name, result.version());
    } catch (RuntimeException e) {
      log.error("Model bootstrap: failed {} {}; if it stopped half way, continue or give it up in "
          + "the control panel", name, e.getMessage(), e);
    }
  }
}
