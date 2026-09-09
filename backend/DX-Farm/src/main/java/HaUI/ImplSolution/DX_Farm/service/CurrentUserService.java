package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.model.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

// Actor thực hiện hành động (reportedBy/decidedBy...) lấy từ JWT đã xác thực - không tin
// field tên tự do gửi lên từ client, đúng tinh thần poka-yoke chặn ở backend.
// User implements UserDetails nên principal trong SecurityContext đã chính là entity thật
// (do JwtAuthenticationFilter/CustomUserDetailsService load) - không cần query lại DB.
@Service
public class CurrentUserService {

  public User getCurrentUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof User user) {
      return user;
    }
    throw new IllegalStateException("No authenticated User principal found: " + principal);
  }

  public String currentFullName() {
    return getCurrentUser().getFullName();
  }
}
