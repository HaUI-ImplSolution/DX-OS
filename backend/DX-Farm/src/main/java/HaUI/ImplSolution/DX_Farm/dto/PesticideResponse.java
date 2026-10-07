// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

// @formatter:off
public record PesticideResponse(

  // Khoá chính
  Long id,

  // Mã thuốc
  String code,

  // Tên hiển thị
  String name,

  // Đơn vị tính liều
  String unit
){}
