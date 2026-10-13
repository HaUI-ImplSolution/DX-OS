// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import java.time.LocalDate;

// @formatter:off
public record IsolationStatusResponse(
  // Vụ mùa được hỏi
  Long cropSeasonId,

  // Mã thửa ruộng sở hữu vụ mùa
  String parcelCode,

  // Còn đang trong thời gian cách ly (PHI) hay không
  boolean isolated,

  // Ngày hết cách ly của lần phun gần nhất (null nếu chưa từng phun thuốc)
  LocalDate harvestLockedUntil,

  // Số ngày còn lại phải chờ (null nếu không còn cách ly)
  Long daysRemaining,

  // Mã thuốc của lần phun gần nhất dẫn tới cách ly (null nếu chưa từng phun thuốc)
  String pesticideCode,

  // Câu trả lời tiếng Việt, diễn đạt lại facts ở trên - không qua LLM
  String message
){}
