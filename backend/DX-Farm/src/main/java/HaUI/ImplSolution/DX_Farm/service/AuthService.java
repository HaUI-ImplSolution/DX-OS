package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.LoginRequest;
import HaUI.ImplSolution.DX_Farm.dto.LoginResponse;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.User;
import HaUI.ImplSolution.DX_Farm.repository.UserRepository;
import HaUI.ImplSolution.DX_Farm.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final JwtService jwtService;

  public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
      JwtService jwtService) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.jwtService = jwtService;
  }

  public LoginResponse login(LoginRequest request) {
    // Ném BadCredentialsException/DisabledException nếu sai mật khẩu hoặc tài khoản bị khoá
    // -> GlobalExceptionHandler map thành 401. Principal trả về đã chính là UserDetails do
    // CustomUserDetailsService tạo, tái dùng luôn để không phải load lại lần nữa.
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();

    User user = userRepository.findByUsername(request.username()).orElseThrow(
        () -> new ResourceNotFoundException("Unknown username: " + request.username()));
    String token = jwtService.generateToken(userDetails);
    return new LoginResponse(token, user.getUsername(), user.getFullName(), user.getRole().name());
  }
}
