package HaUI.ImplSolution.DX_Farm.dto;

// @formatter:off
public record LoginResponse(
  // JWT dùng cho header Authorization: Bearer <token>
  String token,

  // Tên đăng nhập
  String username,

  // Họ tên hiển thị
  String fullName,

  // Vai trò (FARMER/ENGINEER/ADMIN)
  String role
){}
