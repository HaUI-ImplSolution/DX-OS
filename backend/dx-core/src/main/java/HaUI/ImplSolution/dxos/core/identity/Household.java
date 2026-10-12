// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "households")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Household {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Mã hộ tuần tự (vd: H0042)
  @Column(nullable = false, unique = true)
  private String code;

  // Họ tên chủ hộ
  @Column(nullable = false)
  private String fullName;

  private String phone;

  private String address;

  // Claim "sub" của tài khoản Keycloak; null khi hộ chưa gắn tài khoản
  @Column(unique = true)
  private String keycloakUserId;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;
}
