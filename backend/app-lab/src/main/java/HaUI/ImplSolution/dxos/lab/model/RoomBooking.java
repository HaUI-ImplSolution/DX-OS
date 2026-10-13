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

// A room reserved for a time range. Its status mirrors the ROOM_BOOKING workflow case.
@Entity
@Table(name = "room_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBooking {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @Column(nullable = false)
  private String requesterId;

  @Column(nullable = false)
  private String requesterName;

  @Column(nullable = false)
  private String requesterRole;

  private Long workspaceId;

  // Course or event the room is booked for
  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private Integer attendees;

  @Column(nullable = false)
  private Instant startsAt;

  @Column(nullable = false)
  private Instant endsAt;

  // True while the booking holds its slot; the database refuses two active bookings that overlap
  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  // Status of the ROOM_BOOKING case, see BookingStatus
  @Column(nullable = false)
  private String status;

  @Column(length = 1000)
  private String rejectionReason;

  private Long workflowCaseId;

  @Column(unique = true)
  private UUID clientRequestId;

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
