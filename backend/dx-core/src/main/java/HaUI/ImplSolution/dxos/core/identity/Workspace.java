// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

// The unit an app attaches its data to, such as a team or a class.
@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Sequential code built from the type prefix (e.g. H0042)
  @Column(nullable = false, unique = true)
  private String code;

  // A workspace type registered by the running app
  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String name;

  private String phone;

  private String address;

  // Free-form properties of the app
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, String> attributes;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;
}
