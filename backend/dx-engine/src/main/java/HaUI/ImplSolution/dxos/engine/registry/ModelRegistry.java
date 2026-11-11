// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.registry;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelException;
import HaUI.ImplSolution.dxos.model.ModelParser;
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
// every apply. A stored model that no longer passes the checks is left out and logged, so the
// others keep running.
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
    List<String> contents =
        jdbc.queryForList(
            "SELECT DISTINCT ON (model) content FROM engine.model_version ORDER BY model, version DESC",
            String.class);
    Map<String, Model> loaded = new LinkedHashMap<>();
    for (String content : contents) {
      try {
        Model model = ModelParser.parse(content);
        loaded.put(model.name(), model);
      } catch (ModelException e) {
        log.error("A stored model is left out because it fails the checks: {}", e.problems());
      }
    }
    models = Map.copyOf(loaded);
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
    if (!use(name)) {
      throw new InvalidStateException(
          "Model " + name + " is being applied or needs reconciliation");
    }
    return find(name)
        .orElseThrow(() -> new ResourceNotFoundException("Model " + name + " is not applied"));
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
