package HaUI.ImplSolution.DX_Farm.security;

import HaUI.ImplSolution.DX_Farm.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public CustomUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // User implements UserDetails trực tiếp
    return userRepository.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("Unknown username: " + username));
  }
}
