// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import HaUI.ImplSolution.dxos.core.api.audit.AuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One audited event. Rows are only inserted, never updated or deleted.
@Entity
@Immutable
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  // Token "sub" of the actor; null when the request carried no valid token
  private String actorUserId;

  private String actorName;

  // Realm roles of the actor, comma separated
  private String actorRoles;

  // Token "azp": the client the token was issued to
  private String clientId;

  @Column(nullable = false)
  private String action;

  private String targetType;

  private String targetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuditOutcome outcome;

  private String ip;

  private String requestId;

  private String httpMethod;

  private String path;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> metadata;
}
