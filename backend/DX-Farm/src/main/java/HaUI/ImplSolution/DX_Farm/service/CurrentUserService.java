package HaUI.ImplSolution.DX_Farm.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

// Actor cho reportedBy/decidedBy... lấy từ claim của token Keycloak đã xác thực.
@Service
public class CurrentUserService {

  public Jwt getCurrentJwt() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt jwt) {
      return jwt;
    }
    throw new IllegalStateException("No authenticated Jwt principal found: " + principal);
  }

  public String currentFullName() {
    Jwt jwt = getCurrentJwt();
    String name = jwt.getClaimAsString("name");
    return name != null ? name : jwt.getClaimAsString("preferred_username");
  }
}
