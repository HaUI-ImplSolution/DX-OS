// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.core.identity;

import java.time.Instant;

// @formatter:off
public record HouseholdResponse(
  Long id,

  // Mã hộ (vd: H0042)
  String code,

  String fullName,

  String phone,

  String address,

  // true khi hộ đã gắn tài khoản Keycloak
  boolean accountLinked,

  Instant createdAt
){}
