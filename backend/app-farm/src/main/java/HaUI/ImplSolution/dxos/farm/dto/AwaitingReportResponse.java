// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import java.time.Instant;

// A report that has no diagnosis yet, as the engineers' to-do list shows it.
public record AwaitingReportResponse(Long reportId,Long cropSeasonId,String parcelCode,String farmerName,String riceVariety,String reportedBy,Instant reportedAt){}
