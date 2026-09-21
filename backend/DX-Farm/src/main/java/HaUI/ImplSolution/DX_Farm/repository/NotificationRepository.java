package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.Notification;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Page<Notification> findByRecipientUserIdOrderByCreatedAtDescIdDesc(String recipientUserId,
      Pageable pageable);

  Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
      String recipientUserId, Pageable pageable);

  long countByRecipientUserIdAndReadAtIsNull(String recipientUserId);

  Optional<Notification> findByIdAndRecipientUserId(Long id, String recipientUserId);

  boolean existsByDedupKey(String dedupKey);

  @Modifying
  @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId"
      + " and n.readAt is null")
  int markAllRead(@Param("userId") String userId, @Param("now") Instant now);
}
