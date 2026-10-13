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

// One repair, inspection or replacement of a device; open until completedAt is set.
@Entity
@Table(name = "device_maintenance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceMaintenance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private Device device;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MaintenanceKind kind;

  @Column(nullable = false, length = 2000)
  private String note;

  // Ticket that led to the work, when there is one
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ticket_id")
  private Ticket ticket;

  @Column(nullable = false)
  private String performedById;

  @Column(nullable = false)
  private String performedByName;

  @Column(nullable = false)
  private Instant startedAt;

  private Instant completedAt;

  @Column(length = 2000)
  private String completionNote;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
