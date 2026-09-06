package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.User;
import HaUI.ImplSolution.DX_Farm.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Actor thực hiện hành động (reportedBy/decidedBy...) lấy từ JWT đã xác thực - không tin
// field tên tự do gửi lên từ client, đúng tinh thần poka-yoke chặn ở backend.
@Service
@Transactional(readOnly = true)
public class CurrentUserService {

  private final UserRepository userRepository;

  public CurrentUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User getCurrentUser() {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    return userRepository.findByUsername(username)
        .orElseThrow(() -> new ResourceNotFoundException("Unknown username: " + username));
  }

  public String currentFullName() {
    return getCurrentUser().getFullName();
  }
}
