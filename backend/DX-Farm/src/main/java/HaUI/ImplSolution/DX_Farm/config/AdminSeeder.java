package HaUI.ImplSolution.DX_Farm.config;

import HaUI.ImplSolution.DX_Farm.model.Role;
import HaUI.ImplSolution.DX_Farm.model.User;
import HaUI.ImplSolution.DX_Farm.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Seed đúng 1 tài khoản ADMIN khi chưa có user nào - dùng để đăng nhập lần đầu rồi tự
// tạo tiếp user ENGINEER/FARMER qua UserController (không có đăng ký công khai).
@Component
public class AdminSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String defaultUsername;
  private final String defaultPassword;

  public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
      @Value("${admin.default.username}") String defaultUsername,
      @Value("${admin.default.password}") String defaultPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.defaultUsername = defaultUsername;
    this.defaultPassword = defaultPassword;
  }

  @Override
  public void run(String... args) {
    if (userRepository.count() > 0) {
      return;
    }
    User admin = User.builder().username(defaultUsername)
        .passwordHash(passwordEncoder.encode(defaultPassword)).fullName("Quản trị viên")
        .role(Role.ADMIN).active(true).build();
    userRepository.save(admin);
  }
}
