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

// A file stored in object storage and linked to a ticket.
@Entity
@Table(name = "ticket_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @Column(nullable = false)
  private String objectKey;

  @Column(nullable = false)
  private String filename;

  @Column(nullable = false, length = 100)
  private String contentType;

  @Column(nullable = false)
  private Long sizeBytes;

  @Column(nullable = false)
  private String uploadedBy;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
