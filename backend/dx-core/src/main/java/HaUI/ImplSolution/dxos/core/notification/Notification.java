// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Token "sub" of the account that sees the message
  @Column(nullable = false)
  private String recipientUserId;

  // A type the running app registered
  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 1000)
  private String body;

  // Entity the message is about, so a client can open it
  private String relatedEntityType;

  private String relatedEntityRef;

  // Set on reminders so one cannot be created twice for the same day
  @Column(unique = true)
  private String dedupKey;

  // Null while unread
  private Instant readAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
