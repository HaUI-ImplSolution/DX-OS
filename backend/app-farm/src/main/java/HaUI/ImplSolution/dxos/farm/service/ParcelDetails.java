// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.model.Parcel;
import java.util.LinkedHashMap;
import java.util.Map;

// The values of a parcel that approval requests show.
final class ParcelDetails {

  static Map<String, Object> of(Parcel parcel) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("areaHectares", parcel.getAreaHectares());
    values.put("location", parcel.getLocation());
    values.put("latitude", parcel.getLatitude());
    values.put("longitude", parcel.getLongitude());
    values.put("gpsAccuracyMeters", parcel.getGpsAccuracyMeters());
    return values;
  }

  private ParcelDetails() {}
}
