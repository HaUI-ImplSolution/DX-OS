package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.model.Parcel;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

// Actor cho reportedBy/decidedBy... lấy từ claim của token Keycloak đã xác thực.
@Service
public class CurrentUserService {

  private static final String ROLE_FARMER = "ROLE_FARMER";
  private static final Set<String> STAFF_ROLES = Set.of("ROLE_ENGINEER", "ROLE_ADMIN");

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

  // A FARMER without ENGINEER/ADMIN is limited to their own parcels and approved diagnoses.
  public boolean isFarmer() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    Set<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
    return roles.contains(ROLE_FARMER) && STAFF_ROLES.stream().noneMatch(roles::contains);
  }

  // A farmer only accesses parcels whose farmerName matches their name; other roles access all.
  public boolean canAccess(Parcel parcel) {
    if (!isFarmer()) {
      return true;
    }
    String name = currentFullName();
    return name != null && name.equalsIgnoreCase(parcel.getFarmerName());
  }
}
