package HaUI.ImplSolution.DX_Farm.dto;

import HaUI.ImplSolution.DX_Farm.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// @formatter:off
public record UserCreateRequest(
  // Tên đăng nhập, duy nhất
  @NotBlank
  String username,

  // Mật khẩu ban đầu
  @NotBlank
  @Size(min = 6)
  String password,

  // Họ tên hiển thị
  @NotBlank
  String fullName,

  // Vai trò: FARMER/ENGINEER/ADMIN
  @NotNull
  Role role
){}
