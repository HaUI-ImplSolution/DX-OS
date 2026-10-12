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

// A message on a ticket; internal ones are visible to staff only.
@Entity
@Table(name = "ticket_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketComment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @Column(nullable = false)
  private String authorId;

  @Column(nullable = false)
  private String authorName;

  // The author was acting as a technician or administrator
  @Column(nullable = false)
  private boolean staff;

  @Column(nullable = false, name = "internal")
  private boolean internal;

  @Column(nullable = false, length = 4000)
  private String body;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
