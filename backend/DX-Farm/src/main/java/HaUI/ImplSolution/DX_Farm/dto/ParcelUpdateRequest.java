// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Values of a parcel sent again after a rejection, or proposed as a change to an approved parcel.
// @formatter:off
public record ParcelUpdateRequest(
  // Parcel area in hectares
  @NotNull
  @DecimalMin(value="0.01")
  BigDecimal areaHectares,

  // Parcel location or address
  String location,

  // GPS position, required
  @NotNull @DecimalMin(value = "-90") @DecimalMax(value = "90")
  BigDecimal latitude,

  @NotNull @DecimalMin(value = "-180") @DecimalMax(value = "180")
  BigDecimal longitude,

  // Error radius of the GPS fix in meters; required for a FARMER
  @DecimalMin(value = "0")
  BigDecimal gpsAccuracyMeters,

  // The device reports the position as coming from a mock-location app
  Boolean mockedLocation,

  // Why the change is wanted (edit request for an approved parcel)
  @Size(max = 255)
  String reason
){}
