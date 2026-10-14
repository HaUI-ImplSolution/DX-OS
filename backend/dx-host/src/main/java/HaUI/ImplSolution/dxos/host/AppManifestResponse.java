// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host;

import java.util.List;
import java.util.Map;

// @formatter:off
public record AppManifestResponse(
  // Value of DXOS_APP, e.g. farm
  String id,

  String name,

  // Realm roles the app defines
  List<String> roles,

  // Navigation entries; roles lists who sees an entry, empty for every signed-in user
  List<MenuEntry> menu,

  // Workspace types the app registers
  List<String> workspaceTypes,

  // Values the app asks its clients to use, e.g. gpsMaxAccuracyMeters for Farm
  Map<String, String> settings
){

  public record MenuEntry(
    String id,

    String label,

    String path,

    List<String> roles
  ){}
}
