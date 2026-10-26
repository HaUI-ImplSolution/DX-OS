// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditOutcome;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// Writes audit records. The actor comes from the signed-in token, the client address and request
// id from the logging context of the request.
@Service
public class AuditService implements AuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private static final String ROLE_PREFIX = "ROLE_";
  private static final Set<String> KEYCLOAK_ROLES = Set.of("offline_access", "uma_authorization");
  private static final String ADMIN_AUTHORITY = ROLE_PREFIX + "ADMIN";
  private static final Set<String> CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final AuditLogRepository repository;
  private final AuditDenialLimiter denialLimiter;
  private final TransactionTemplate ownTransaction;

  public AuditService(AuditLogRepository repository, AuditDenialLimiter denialLimiter,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.denialLimiter = denialLimiter;
    this.ownTransaction = new TransactionTemplate(transactionManager);
    this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void record(AuditEvent event) {
    try {
      AuditLogEntry entry = toEntry(event);
      ownTransaction.executeWithoutResult(status -> repository.save(entry));
    } catch (RuntimeException e) {
      log.warn("Could not write the audit record of {}: {}", event.action(),
          e.getClass().getSimpleName());
    }
  }

  // A refused request: 403 or a rejected token.
  public void recordDenial(String action) {
    recordLimited(action, AuditOutcome.DENIED, Map.of());
  }

  // A request that ended in a server error or an unavailable dependency (5xx).
  public void recordServerError(int status, Throwable cause) {
    recordLimited("SERVER_ERROR", AuditOutcome.FAILED, failureFacts(status, cause));
  }

  // A change an administrator made that was refused for a business reason (409, 422...).
  public void recordAdminFailure(int status, Throwable cause) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean admin = authentication != null && authentication.getAuthorities().stream()
        .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    if (admin && CHANGING_METHODS.contains(MDC.get(RequestContext.METHOD))) {
      recordLimited("ADMIN_ACTION_FAILED", AuditOutcome.FAILED, failureFacts(status, cause));
    }
  }

  // At most a set number of records per client address and action each minute; the rest are
  // counted and reported with the next record.
  private void recordLimited(String action, AuditOutcome outcome, Map<String, Object> facts) {
    String key = MDC.get(RequestContext.CLIENT_IP) + "|" + action;
    int carried = denialLimiter.admit(key, System.currentTimeMillis());
    if (carried < 0) {
      return;
    }
    Map<String, Object> metadata = new LinkedHashMap<>(facts);
    if (carried > 0) {
      metadata.put("suppressed", carried);
    }
    record(new AuditEvent(action, "HTTP", MDC.get(RequestContext.PATH), outcome,
        metadata.isEmpty() ? null : metadata));
  }

  // The exception message is left out because it can hold request data.
  private static Map<String, Object> failureFacts(int status, Throwable cause) {
    return Map.of("status", status, "exception", cause.getClass().getSimpleName());
  }

  private static AuditLogEntry toEntry(AuditEvent event) {
    AuditLogEntry.AuditLogEntryBuilder entry = AuditLogEntry.builder().occurredAt(Instant.now())
        .action(cut(event.action(), 64)).targetType(cut(event.targetType(), 64))
        .targetId(cut(event.targetId(), 255)).outcome(event.outcome())
        .ip(MDC.get(RequestContext.CLIENT_IP)).requestId(MDC.get(RequestContext.REQUEST_ID))
        .httpMethod(MDC.get(RequestContext.METHOD)).path(MDC.get(RequestContext.PATH))
        .metadata(event.metadata() == null || event.metadata().isEmpty() ? null : event.metadata());
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      String name = jwt.getClaimAsString("preferred_username");
      entry.actorUserId(jwt.getSubject())
          .actorName(cut(name != null ? name : jwt.getClaimAsString("name"), 255))
          .clientId(cut(jwt.getClaimAsString("azp"), 255))
          .actorRoles(cut(roles(authentication), 255));
    }
    return entry.build();
  }

  // The application roles of the actor; Keycloak's built-in roles say nothing about the actor.
  private static String roles(Authentication authentication) {
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .filter(role -> !role.startsWith("default-roles-") && !KEYCLOAK_ROLES.contains(role))
        .sorted().collect(Collectors.joining(","));
  }

  private static String cut(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
