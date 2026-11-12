// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.users;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient.Account;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient.Accounts;
import HaUI.ImplSolution.dxos.engine.event.AdminEventPublisher;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.registry.ModelClient;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Lets an administrator see who holds which role of which model, change roles and units, and keep
// accounts. The roles of the platform (ADMIN) are not reachable from here: only the roles and
// units that running models declare can be given or taken. Every change is written to the audit
// log with the administrator who made it; a password is never written.
@Service
public class UserAdminService {

  public record UserView(String id, String username, String name, String email, boolean enabled) {}

  public record UserDetail(UserView user, Map<String, List<String>> roles, List<String> units) {}

  public record RoleInfo(String name, String description) {}

  public record ModelRoles(String model, List<RoleInfo> roles) {}

  public record UnitInfo(String name, String label) {}

  public record Options(List<ModelRoles> models, List<UnitInfo> units) {}

  public record NewUser(String username, String fullName, String email, String temporaryPassword) {}

  public record CreatedUser(String id, String username, String temporaryPassword) {}

  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
  private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
  private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  private static final String DIGITS = "23456789";
  private static final int PASSWORD_LENGTH = 14;

  private final KeycloakAdminClient keycloak;
  private final ModelRegistry registry;
  private final UserContext user;
  private final AuditRecorder audit;
  private final AdminEventPublisher events;
  private final SecureRandom random = new SecureRandom();

  public UserAdminService(
      KeycloakAdminClient keycloak,
      ModelRegistry registry,
      UserContext user,
      AuditRecorder audit,
      AdminEventPublisher events) {
    this.keycloak = keycloak;
    this.registry = registry;
    this.user = user;
    this.audit = audit;
    this.events = events;
  }

  public PageResponse<UserView> list(String search, Integer page, Integer size) {
    int pageNumber = page == null || page < 0 ? 0 : page;
    int pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    int first = (int) Math.min((long) pageNumber * pageSize, Integer.MAX_VALUE);
    Accounts accounts = keycloak.searchAccounts(search, first, pageSize);
    int pages = (int) ((accounts.total() + pageSize - 1) / pageSize);
    return new PageResponse<>(
        accounts.items().stream().map(UserAdminService::view).toList(),
        pageNumber,
        pageSize,
        accounts.total(),
        pages);
  }

  public UserDetail get(String id) {
    UserView view = view(require(id));
    Map<String, List<String>> roles = new LinkedHashMap<>();
    keycloak
        .clientRolesOf(id)
        .forEach(
            (client, names) ->
                ModelClient.modelOf(client).ifPresent(model -> roles.put(model, names)));
    Set<String> declared =
        declaredUnits().stream().map(Model.Unit::name).collect(Collectors.toSet());
    List<String> units = keycloak.groupsOf(id).stream().filter(declared::contains).toList();
    return new UserDetail(view, roles, units);
  }

  // What can be given: the roles and units of the models that run now.
  public Options options() {
    List<ModelRoles> models =
        registry.all().stream()
            .map(
                model ->
                    new ModelRoles(
                        model.name(),
                        model.roles().stream()
                            .map(role -> new RoleInfo(role.name(), role.description()))
                            .toList()))
            .toList();
    List<UnitInfo> units =
        declaredUnits().stream().map(unit -> new UnitInfo(unit.name(), unit.label())).toList();
    return new Options(models, units);
  }

  public void grantRole(String id, String model, String role) {
    Account account = require(id);
    Model running =
        registry
            .find(model)
            .orElseThrow(() -> new ResourceNotFoundException("Model " + model + " is not applied"));
    if (running.roles().stream().noneMatch(declared -> declared.name().equals(role))) {
      throw new ResourceNotFoundException("Model " + model + " has no role " + role);
    }
    keycloak.grantClientRole(id, ModelClient.of(model), role);
    audited("USER_ROLE_GRANTED", account, Map.of("model", model, "role", role));
  }

  public void revokeRole(String id, String model, String role) {
    Account account = require(id);
    keycloak.revokeClientRole(id, ModelClient.of(model), role);
    audited("USER_ROLE_REVOKED", account, Map.of("model", model, "role", role));
  }

  public void addUnit(String id, String unit) {
    Account account = require(id);
    requireUnit(unit);
    keycloak.addUserToGroup(id, unit);
    audited("USER_UNIT_ADDED", account, Map.of("unit", unit));
  }

  public void removeUnit(String id, String unit) {
    Account account = require(id);
    requireUnit(unit);
    keycloak.removeUserFromGroup(id, unit);
    audited("USER_UNIT_REMOVED", account, Map.of("unit", unit));
  }

  // Creates the account with a temporary password that the person changes at the first sign-in.
  public CreatedUser create(NewUser request) {
    Map<String, String> problems = new LinkedHashMap<>();
    if (request == null || request.username() == null || request.username().isBlank()) {
      problems.put("username", "is required");
    }
    if (request == null || request.fullName() == null || request.fullName().isBlank()) {
      problems.put("fullName", "is required");
    }
    if (!problems.isEmpty()) {
      throw new ModelValidationException(problems);
    }
    String password =
        request.temporaryPassword() == null || request.temporaryPassword().isBlank()
            ? generatePassword()
            : request.temporaryPassword();
    String username = request.username().trim();
    String id = keycloak.createUser(username, request.fullName().trim(), request.email(), password);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("username", username);
    audit.record(AuditEvent.success("USER_CREATED", "USER", id, metadata));
    announce("USER_CREATED", id, metadata);
    return new CreatedUser(id, username, password);
  }

  public void setEnabled(String id, boolean enabled) {
    Account account = require(id);
    if (!enabled && id.equals(user.currentUserId())) {
      throw new InvalidStateException("You cannot lock your own account");
    }
    keycloak.setEnabled(id, enabled);
    audited(enabled ? "USER_UNLOCKED" : "USER_LOCKED", account, Map.of());
  }

  // Sets a new temporary password and returns it, once.
  public String resetPassword(String id) {
    Account account = require(id);
    String password = generatePassword();
    keycloak.resetPassword(id, password);
    audited("USER_PASSWORD_RESET", account, Map.of());
    return password;
  }

  // Writes what the administrator changed about an account; the actor comes from the token.
  private void audited(String action, Account account, Map<String, Object> facts) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("username", account.username());
    metadata.putAll(facts);
    audit.record(AuditEvent.success(action, "USER", account.id(), metadata));
    announce(action, account.id(), metadata);
  }

  // Tells the default webhook target about the change; a failure to queue the message does not
  // undo the change.
  private void announce(String action, String userId, Map<String, Object> metadata) {
    try {
      Map<String, Object> facts = new LinkedHashMap<>();
      facts.put("userId", userId);
      facts.putAll(metadata);
      events.publish(action, null, user.currentUserId(), user.currentFullName(), facts);
    } catch (RuntimeException e) {
      log.warn("The {} message could not be queued: {}", action, e.getMessage());
    }
  }

  private Account require(String id) {
    return keycloak
        .findAccount(id)
        .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
  }

  private void requireUnit(String unit) {
    if (declaredUnits().stream().noneMatch(declared -> declared.name().equals(unit))) {
      throw new ResourceNotFoundException("Unit " + unit + " not found");
    }
  }

  private List<Model.Unit> declaredUnits() {
    Map<String, Model.Unit> units = new LinkedHashMap<>();
    registry
        .all()
        .forEach(model -> model.units().forEach(unit -> units.putIfAbsent(unit.name(), unit)));
    return List.copyOf(units.values());
  }

  private static UserView view(Account account) {
    return new UserView(
        account.id(), account.username(), account.name(), account.email(), account.enabled());
  }

  // A random password with a letter of each case and a digit, without look-alike characters.
  private String generatePassword() {
    List<Character> chars = new ArrayList<>();
    chars.add(pick(LOWER));
    chars.add(pick(UPPER));
    chars.add(pick(DIGITS));
    String all = LOWER + UPPER + DIGITS;
    while (chars.size() < PASSWORD_LENGTH) {
      chars.add(pick(all));
    }
    Collections.shuffle(chars, random);
    return chars.stream().map(String::valueOf).collect(Collectors.joining());
  }

  private char pick(String alphabet) {
    return alphabet.charAt(random.nextInt(alphabet.length()));
  }
}
