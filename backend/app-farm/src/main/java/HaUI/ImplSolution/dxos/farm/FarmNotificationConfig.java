// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm;

import HaUI.ImplSolution.dxos.core.api.notification.NotificationChannel;
import HaUI.ImplSolution.dxos.core.api.notification.NotificationTypeDefinition;
import HaUI.ImplSolution.dxos.farm.service.MattermostChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The notification types the Farm app sends and the chat channels it posts to.
@Configuration
public class FarmNotificationConfig {

  @Bean
  public NotificationTypeDefinition parcelApprovedType() {
    return new NotificationTypeDefinition("PARCEL_APPROVED", "Thửa ruộng được duyệt", "check");
  }

  @Bean
  public NotificationTypeDefinition parcelRejectedType() {
    return new NotificationTypeDefinition("PARCEL_REJECTED", "Thửa ruộng bị từ chối", "x");
  }

  @Bean
  public NotificationTypeDefinition parcelEditApprovedType() {
    return new NotificationTypeDefinition("PARCEL_EDIT_APPROVED", "Yêu cầu sửa thửa được duyệt",
        "check");
  }

  @Bean
  public NotificationTypeDefinition parcelEditRejectedType() {
    return new NotificationTypeDefinition("PARCEL_EDIT_REJECTED", "Yêu cầu sửa thửa bị từ chối",
        "x");
  }

  @Bean
  public NotificationTypeDefinition cropSeasonApprovedType() {
    return new NotificationTypeDefinition("CROP_SEASON_APPROVED", "Vụ mùa được duyệt", "check");
  }

  @Bean
  public NotificationTypeDefinition cropSeasonRejectedType() {
    return new NotificationTypeDefinition("CROP_SEASON_REJECTED", "Vụ mùa bị từ chối", "x");
  }

  @Bean
  public NotificationTypeDefinition diagnosisApprovedType() {
    return new NotificationTypeDefinition("DIAGNOSIS_APPROVED", "Chẩn đoán được duyệt", "check");
  }

  @Bean
  public NotificationTypeDefinition diagnosisRejectedType() {
    return new NotificationTypeDefinition("DIAGNOSIS_REJECTED", "Chẩn đoán bị từ chối", "x");
  }

  @Bean
  public NotificationTypeDefinition treatmentConfirmedType() {
    return new NotificationTypeDefinition("TREATMENT_CONFIRMED", "Lần phun thuốc được xác nhận",
        "check");
  }

  @Bean
  public NotificationTypeDefinition treatmentRejectedType() {
    return new NotificationTypeDefinition("TREATMENT_REJECTED", "Lần phun thuốc bị từ chối", "x");
  }

  @Bean
  public NotificationTypeDefinition harvestReminderType() {
    return new NotificationTypeDefinition("HARVEST_REMINDER", "Nhắc thu hoạch", "bell");
  }

  @Bean
  public NotificationTypeDefinition harvestRecordedType() {
    return new NotificationTypeDefinition("HARVEST_RECORDED", "Đã ghi nhận thu hoạch", "check");
  }

  @Bean
  public NotificationTypeDefinition retakeRequestedType() {
    return new NotificationTypeDefinition("RETAKE_REQUESTED", "Cần chụp lại ảnh", "camera");
  }

  @Bean
  public NotificationChannel adminChannel() {
    return new NotificationChannel(MattermostChannel.ADMIN.key(), "Quản trị viên");
  }

  @Bean
  public NotificationChannel engineerChannel() {
    return new NotificationChannel(MattermostChannel.ENGINEER.key(), "Kỹ sư");
  }
}
