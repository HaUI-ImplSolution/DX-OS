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

// A tracked piece of equipment; status follows its loans and maintenance.
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Unique asset tag
  @Column(nullable = false, unique = true)
  private String assetCode;

  @Column(nullable = false)
  private String deviceType;

  private String model;

  private String serialNumber;

  // Room the device normally sits in
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private DeviceStatus status = DeviceStatus.AVAILABLE;

  private LocalDate purchasedOn;

  private LocalDate warrantyUntil;

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
