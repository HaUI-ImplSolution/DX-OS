// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

// @formatter:off
public record DiseaseResponse(
  // Khoá chính
  Long id,

  // Mã bệnh
  String code,

  // Tên hiển thị tiếng Việt
  String name,

  // Mô tả chi tiết về bệnh
  String description
){}
