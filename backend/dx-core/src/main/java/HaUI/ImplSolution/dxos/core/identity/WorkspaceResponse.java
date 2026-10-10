// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import java.time.Instant;
import java.util.Map;

// @formatter:off
public record WorkspaceResponse(
  Long id,

  // Sequential code built from the type prefix (e.g. H0042)
  String code,

  // Workspace type registered by the running app
  String type,

  String name,

  String phone,

  String address,

  // true once an owner account is linked
  boolean accountLinked,

  Map<String, String> attributes,

  Instant createdAt
){}
