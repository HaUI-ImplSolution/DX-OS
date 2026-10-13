// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.event;

import java.math.BigDecimal;

// Published inside the transaction that stores a parcel request; plain values only.
public record ParcelCreatedEvent(String parcelCode,String householdCode,String farmerName,BigDecimal areaHectares,BigDecimal latitude,BigDecimal longitude,boolean resubmitted){}
