package HaUI.ImplSolution.DX_Farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Tên đăng nhập, dùng làm định danh nghiệp vụ
  @Column(nullable = false, unique = true)
  private String username;

  // Mật khẩu đã băm (BCrypt), không bao giờ trả ra API
  @Column(nullable = false)
  private String passwordHash;

  // Họ tên hiển thị (dùng làm approvedBy/rejectedBy/reportedBy thay cho nhập tay)
  @Column(nullable = false)
  private String fullName;

  // Vai trò, quyết định quyền gọi API
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  // Tài khoản còn hiệu lực đăng nhập hay không
  @Column(nullable = false)
  private boolean active;

  // Thời điểm tạo bản ghi
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // Thời điểm cập nhật gần nhất
  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  // ─── UserDetails ─── getUsername() đã khớp sẵn nhờ Lombok @Getter trên field `username`.

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return active;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }
}
