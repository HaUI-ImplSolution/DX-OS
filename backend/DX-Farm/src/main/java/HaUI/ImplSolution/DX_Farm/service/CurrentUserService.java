package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdRepository;
import java.util.Optional;
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
  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final Set<String> STAFF_ROLES = Set.of("ROLE_ENGINEER", ROLE_ADMIN);

  private final HouseholdRepository householdRepository;

  public CurrentUserService(HouseholdRepository householdRepository) {
    this.householdRepository = householdRepository;
  }

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

  // Stable account id (token "sub"), unlike the display name which Keycloak users can change.
  public String currentUserId() {
    return getCurrentJwt().getSubject();
  }

  // The household linked to the signed-in account, if an administrator has linked one.
  public Optional<Household> currentHousehold() {
    return householdRepository.findByKeycloakUserId(currentUserId());
  }

  // A FARMER without ENGINEER/ADMIN is limited to their own household's parcels and approved
  // diagnoses.
  public boolean isFarmer() {
    Set<String> roles = currentRoles();
    return roles.contains(ROLE_FARMER) && STAFF_ROLES.stream().noneMatch(roles::contains);
  }

  public boolean isAdmin() {
    return currentRoles().contains(ROLE_ADMIN);
  }

  // A farmer only accesses parcels of their own household; other roles access all.
  public boolean canAccess(Parcel parcel) {
    if (!isFarmer()) {
      return true;
    }
    return currentHousehold()
        .map(household -> household.getId().equals(parcel.getHousehold().getId())).orElse(false);
  }

  private Set<String> currentRoles() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }
}
