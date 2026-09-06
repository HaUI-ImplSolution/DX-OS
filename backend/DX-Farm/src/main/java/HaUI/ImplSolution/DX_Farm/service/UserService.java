package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.UserCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.UserResponse;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.model.User;
import HaUI.ImplSolution.DX_Farm.repository.UserRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Chỉ ADMIN mới gọi được (chặn ở UserController @PreAuthorize) - không có đăng ký công khai.
@Service
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public List<UserResponse> listAll() {
    return userRepository.findAll().stream().map(UserService::toResponse).toList();
  }

  @Transactional
  public UserResponse create(UserCreateRequest request) {
    if (userRepository.existsByUsername(request.username())) {
      throw new InvalidStateException("Username already taken: " + request.username());
    }
    User user = User.builder().username(request.username())
        .passwordHash(passwordEncoder.encode(request.password())).fullName(request.fullName())
        .role(request.role()).active(true).build();
    return toResponse(userRepository.save(user));
  }

  private static UserResponse toResponse(User user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getFullName(),
        user.getRole().name(), user.isActive(), user.getCreatedAt());
  }
}
