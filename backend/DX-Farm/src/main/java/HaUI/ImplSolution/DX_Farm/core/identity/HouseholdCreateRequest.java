package HaUI.ImplSolution.DX_Farm.core.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// @formatter:off
public record HouseholdCreateRequest(
  // Họ tên chủ hộ, cũng là tên hiển thị của tài khoản; cần ít nhất hai từ (Keycloak bắt buộc
  // cả firstName lẫn lastName)
  @NotBlank
  @Pattern(regexp = "\\S+(\\s+\\S+)+")
  @Size(max = 255)
  String fullName,

  @Size(max = 255)
  String phone,

  @Size(max = 255)
  String address,

  // Tên đăng nhập Keycloak: chữ thường, số, dấu chấm, gạch dưới, gạch ngang
  @NotBlank
  @Pattern(regexp = "[a-z0-9._-]{3,50}")
  String username,

  @Email
  @Size(max = 255)
  String email
){}
