// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab;

import HaUI.ImplSolution.dxos.core.api.notification.NotificationChannel;
import HaUI.ImplSolution.dxos.core.api.notification.NotificationTypeDefinition;
import HaUI.ImplSolution.dxos.lab.service.LabNotificationTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The message types the Lab app sends and the chat channels it posts to.
@Configuration
public class LabNotificationConfig {

  public static final String TECHNICIAN_CHANNEL = "technician";
  public static final String ADMIN_CHANNEL = "admin";

  @Bean
  public NotificationTypeDefinition ticketAssignedType() {
    return new NotificationTypeDefinition(LabNotificationTypes.TICKET_ASSIGNED,
        "Yêu cầu được tiếp nhận", "user-check");
  }

  @Bean
  public NotificationTypeDefinition ticketRepliedType() {
    return new NotificationTypeDefinition(LabNotificationTypes.TICKET_REPLIED, "Có phản hồi mới",
        "message");
  }

  @Bean
  public NotificationTypeDefinition ticketWaitingUserType() {
    return new NotificationTypeDefinition(LabNotificationTypes.TICKET_WAITING_USER,
        "Cần bạn bổ sung thông tin", "clock");
  }

  @Bean
  public NotificationTypeDefinition ticketResolvedType() {
    return new NotificationTypeDefinition(LabNotificationTypes.TICKET_RESOLVED,
        "Yêu cầu đã xử lý xong", "check");
  }

  @Bean
  public NotificationTypeDefinition ticketSlaWarningType() {
    return new NotificationTypeDefinition(LabNotificationTypes.TICKET_SLA_WARNING,
        "Yêu cầu sắp quá hạn", "bell");
  }

  @Bean
  public NotificationTypeDefinition ticketOverdueType() {
    return new NotificationTypeDefinition(LabNotificationTypes.TICKET_OVERDUE,
        "Yêu cầu quá hạn xử lý", "alert");
  }

  @Bean
  public NotificationTypeDefinition loanApprovedType() {
    return new NotificationTypeDefinition(LabNotificationTypes.LOAN_APPROVED,
        "Phiếu mượn được duyệt", "check");
  }

  @Bean
  public NotificationTypeDefinition loanRejectedType() {
    return new NotificationTypeDefinition(LabNotificationTypes.LOAN_REJECTED,
        "Phiếu mượn bị từ chối", "x");
  }

  @Bean
  public NotificationTypeDefinition loanDueSoonType() {
    return new NotificationTypeDefinition(LabNotificationTypes.LOAN_DUE_SOON,
        "Sắp đến hạn trả thiết bị", "bell");
  }

  @Bean
  public NotificationTypeDefinition loanOverdueType() {
    return new NotificationTypeDefinition(LabNotificationTypes.LOAN_OVERDUE, "Quá hạn trả thiết bị",
        "alert");
  }

  @Bean
  public NotificationTypeDefinition bookingApprovedType() {
    return new NotificationTypeDefinition(LabNotificationTypes.BOOKING_APPROVED,
        "Lịch đặt phòng được duyệt", "check");
  }

  @Bean
  public NotificationTypeDefinition bookingRejectedType() {
    return new NotificationTypeDefinition(LabNotificationTypes.BOOKING_REJECTED,
        "Lịch đặt phòng bị từ chối", "x");
  }

  @Bean
  public NotificationChannel technicianChannel() {
    return new NotificationChannel(TECHNICIAN_CHANNEL, "Kỹ thuật viên");
  }

  @Bean
  public NotificationChannel adminChannel() {
    return new NotificationChannel(ADMIN_CHANNEL, "Quản trị viên");
  }
}
