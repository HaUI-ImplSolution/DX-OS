package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

// @formatter:off
public record LoginRequest(
  // Tên đăng nhập
  @NotBlank
  String username,

  // Mật khẩu
  @NotBlank
  String password
){}
