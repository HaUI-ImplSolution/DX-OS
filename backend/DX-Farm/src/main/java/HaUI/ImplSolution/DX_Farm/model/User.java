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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

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
}
