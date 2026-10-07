package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import HaUI.ImplSolution.DX_Farm.model.NotificationType;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Tells the farmer once a day that a season reached its expected harvest date and is still not
// harvested.
@Service
public class HarvestReminderService {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final CropSeasonRepository cropSeasonRepository;
  private final NotificationService notificationService;
  private final Clock clock;

  public HarvestReminderService(CropSeasonRepository cropSeasonRepository,
      NotificationService notificationService, Clock clock) {
    this.cropSeasonRepository = cropSeasonRepository;
    this.notificationService = notificationService;
    this.clock = clock;
  }

  // Returns how many reminders were created; a season already reminded today is skipped.
  @Transactional
  public int sendDueReminders() {
    LocalDate today = LocalDate.now(clock);
    int created = 0;
    for (CropSeason season : cropSeasonRepository
        .findByStatusAndApprovalStatusAndDeletedAtIsNullAndExpectedHarvestDateLessThanEqual(
            CropSeasonStatus.ACTIVE, ApprovalStatus.APPROVED, today)) {
      if (season.getParcel().getDeletedAt() != null) {
        continue;
      }
      String label = season.getCode() != null ? season.getCode() : "#" + season.getId();
      boolean sent = notificationService.notifyHouseholdOnce(
          "HARVEST_REMINDER:%d:%s".formatted(season.getId(), today),
          season.getParcel().getHousehold(), NotificationType.HARVEST_REMINDER, "Đến hạn thu hoạch",
          "Vụ %s dự kiến thu hoạch ngày %s. Hãy khai báo thu hoạch hoặc chốt lại ngày mới."
              .formatted(label, DATE_FORMAT.format(season.getExpectedHarvestDate())),
          "CROP_SEASON", String.valueOf(season.getId()));
      if (sent) {
        created++;
      }
    }
    return created;
  }
}
