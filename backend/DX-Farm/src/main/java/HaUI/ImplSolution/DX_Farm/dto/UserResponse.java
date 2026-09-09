package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;

// @formatter:off
public record UserResponse(
  // Khoá chính
  Long id,

  // Tên đăng nhập
  String username,

  // Họ tên hiển thị
  String fullName,

  // Vai trò (FARMER/ENGINEER/ADMIN)
  String role,

  // Tài khoản còn hiệu lực hay không
  boolean active,

  // Thời điểm tạo tài khoản
  Instant createdAt
){}
