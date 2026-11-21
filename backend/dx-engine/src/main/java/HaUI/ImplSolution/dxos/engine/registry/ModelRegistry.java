// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.registry;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelException;
import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import HaUI.ImplSolution.dxos.model.ModelParser;
import HaUI.ImplSolution.dxos.model.ModelParser.ParsedStored;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// The models in force: the latest applied version of each, kept in memory and refreshed after
// every apply. A stored model that no longer passes the checks is left out, logged and listed with
// its problems, so the others keep running. A check that a later release made stricter can be
// tolerated when reading a stored model: it runs without the part that breaks the check, and the
// warning stays listed until a corrected file is applied.
//
// A request that uses a model holds its read lock until the request ends, and an apply takes the
// write lock to hold the model, so an apply starts only after the requests already using the model
// are done and every later request sees the model held.
@Component
@DependsOn("engineFlyway")
public class ModelRegistry {

  private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);
  private static final long HOLD_WAIT_SECONDS = 30;

  // Models whose read lock the current request holds; null outside a request.
  private static final ThreadLocal<Set<String>> REQUEST_LOCKS = new ThreadLocal<>();

  private final JdbcTemplate jdbc;
  private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
  private volatile Map<String, Model> models = Map.of();
  private volatile Set<String> maintenance = Set.of();
  private volatile Map<String, List<String>> leftOut = Map.of();
  private volatile Map<String, List<String>> warnings = Map.of();

  public ModelRegistry(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public synchronized void reload() {
    maintenance =
        Set.copyOf(
            jdbc.queryForList(
                "SELECT model FROM (SELECT DISTINCT ON (model) model, status "
                    + "FROM engine.apply_run ORDER BY model, id DESC) latest "
                    + "WHERE status IN ('running', 'failed')",
                String.class));
    List<Map<String, Object>> stored =
        jdbc.queryForList(
            "SELECT DISTINCT ON (model) model, content FROM engine.model_version "
                + "ORDER BY model, version DESC");
    Map<String, Model> loaded = new LinkedHashMap<>();
    Map<String, List<String>> rejected = new LinkedHashMap<>();
    Map<String, List<String>> tolerated = new LinkedHashMap<>();
    for (Map<String, Object> row : stored) {
      String name = (String) row.get("model");
      try {
        ParsedStored parsed = ModelParser.parseStored((String) row.get("content"));
        loaded.put(parsed.model().name(), parsed.model());
        if (!parsed.tolerated().isEmpty()) {
          List<String> reasons = parsed.tolerated().stream().map(Problem::toString).toList();
          tolerated.put(name, reasons);
          log.warn(
              "Model {} runs without the part that fails the checks of this release: {}."
                  + " Apply a corrected file to fix it.",
              name,
              reasons);
        }
      } catch (ModelException e) {
        List<String> reasons = e.problems().stream().map(Problem::toString).toList();
        rejected.put(name, reasons);
        log.error(
            "The stored model {} is left out because it fails the checks: {}. Its data and stored"
                + " versions are kept; it runs again once the stored version passes the checks.",
            name,
            reasons);
      }
    }
    models = Map.copyOf(loaded);
    leftOut = Map.copyOf(rejected);
    warnings = Map.copyOf(tolerated);
  }

  // The stored models that fail the checks, by name, each with its problems.
  public Map<String, List<String>> leftOut() {
    return leftOut;
  }

  // The problems a stored model runs with, tolerated; empty for a model with none.
  public List<String> warnings(String name) {
    return warnings.getOrDefault(name, List.of());
  }

  // Waits for the requests that use the model to end, then holds it.
  public void beginApply(String model) {
    Set<String> held = REQUEST_LOCKS.get();
    if (held != null && held.remove(model)) {
      lock(model).readLock().unlock();
    }
    ReentrantReadWriteLock.WriteLock write = lock(model).writeLock();
    boolean locked;
    try {
      locked = write.tryLock(HOLD_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      locked = false;
    }
    if (!locked) {
      throw new InvalidStateException("Model " + model + " is busy; try again");
    }
    try {
      hold(model);
    } finally {
      write.unlock();
    }
  }

  private synchronized void hold(String model) {
    LinkedHashSet<String> next = new LinkedHashSet<>(maintenance);
    next.add(model);
    maintenance = Set.copyOf(next);
  }

  public synchronized void endApply(String model) {
    LinkedHashSet<String> next = new LinkedHashSet<>(maintenance);
    next.remove(model);
    maintenance = Set.copyOf(next);
  }

  // A model is held while an apply of it runs and after one stopped before it finished.
  public boolean isHeld(String name) {
    return maintenance.contains(name);
  }

  public Optional<Model> find(String name) {
    return Optional.ofNullable(models.get(name));
  }

  public Model require(String name) {
    List<String> problems = leftOut.get(name);
    if (problems != null) {
      throw new InvalidStateException(
          "Model "
              + name
              + " is left out because its stored version fails the checks: "
              + problems);
    }
    if (!use(name)) {
      throw new InvalidStateException(
          "Model " + name + " is being applied or needs reconciliation");
    }
    return find(name)
        .orElseThrow(() -> new ResourceNotFoundException("Model " + name + " is not applied"));
  }

  // The request holds the model read lock until its transaction has ended.
  public long version(String name) {
    require(name);
    Long version =
        jdbc.queryForObject(
            "SELECT max(version) FROM engine.model_version WHERE model = ?", Long.class, name);
    if (version == null) {
      throw new ResourceNotFoundException("Model " + name + " is not applied");
    }
    return version;
  }

  public void checkVersion(String name, Long expected) {
    if (expected != null && version(name) != expected.longValue()) {
      throw new InvalidStateException("The model changed; read it again", "MODEL_VERSION_CHANGED");
    }
  }

  // The models that are held, so that a screen can say why a model is not there.
  public List<Model> held() {
    return models.values().stream()
        .filter(model -> isHeld(model.name()))
        .sorted(Comparator.comparing(Model::name))
        .toList();
  }

  public Collection<Model> all() {
    List<Model> usable = new ArrayList<>();
    for (Model model :
        models.values().stream().sorted(Comparator.comparing(Model::name)).toList()) {
      if (use(model.name())) {
        usable.add(model);
      }
    }
    return usable;
  }

  // Inside a request the read lock is taken before the hold is checked, so no apply can hold the
  // model between the check and the end of the request.
  private boolean use(String name) {
    Set<String> held = REQUEST_LOCKS.get();
    boolean locked = false;
    if (held != null && !held.contains(name)) {
      lock(name).readLock().lock();
      held.add(name);
      locked = true;
    }
    if (maintenance.contains(name)) {
      if (locked) {
        held.remove(name);
        lock(name).readLock().unlock();
      }
      return false;
    }
    return true;
  }

  private ReentrantReadWriteLock lock(String model) {
    return locks.computeIfAbsent(model, key -> new ReentrantReadWriteLock());
  }

  void startRequest() {
    REQUEST_LOCKS.set(new HashSet<>());
  }

  void endRequest() {
    Set<String> held = REQUEST_LOCKS.get();
    REQUEST_LOCKS.remove();
    if (held != null) {
      held.forEach(model -> lock(model).readLock().unlock());
    }
  }
}
