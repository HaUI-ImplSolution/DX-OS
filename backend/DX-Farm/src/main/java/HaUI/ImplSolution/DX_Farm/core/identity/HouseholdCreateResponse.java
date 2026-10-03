package HaUI.ImplSolution.DX_Farm.core.identity;

// @formatter:off
public record HouseholdCreateResponse(
  HouseholdResponse household,

  // Tên đăng nhập của tài khoản vừa tạo
  String username,

  // Mật khẩu tạm, chỉ trả về một lần; nông dân phải đổi ở lần đăng nhập đầu
  String temporaryPassword
){}
