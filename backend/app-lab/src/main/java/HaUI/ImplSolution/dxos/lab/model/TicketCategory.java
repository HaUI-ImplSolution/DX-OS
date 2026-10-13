// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

// A kind of incident users can report; supplies the default priority and classifier keywords.
@Entity
@Table(name = "ticket_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TicketPriority defaultPriority;

  // Comma-separated hints the classifier matches in addition to its rule file
  @Column(length = 1000)
  private String keywords;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  // Optimistic lock: concurrent writers get a 409 instead of silently overwriting each other
  @Version
  @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
  private Long version;
}
