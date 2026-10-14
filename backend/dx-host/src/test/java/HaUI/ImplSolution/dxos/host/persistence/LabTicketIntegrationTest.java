// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.storage.FileStore;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.config.TimeConfig;
import HaUI.ImplSolution.dxos.core.notification.NotificationInboxService;
import HaUI.ImplSolution.dxos.core.notification.NotificationTypes;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowDefinitions;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowService;
import HaUI.ImplSolution.dxos.lab.LabNotificationConfig;
import HaUI.ImplSolution.dxos.lab.LabWorkflowConfig;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.ConfirmRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.ReopenRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.ResolveRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketCommentRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketResponse;
import HaUI.ImplSolution.dxos.lab.service.LabNotifier;
import HaUI.ImplSolution.dxos.lab.service.LabUser;
import HaUI.ImplSolution.dxos.lab.service.SlaPolicyService;
import HaUI.ImplSolution.dxos.lab.service.TicketCodeGenerator;
import HaUI.ImplSolution.dxos.lab.service.TicketDecisionHandler;
import HaUI.ImplSolution.dxos.lab.service.TicketService;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Ticket lifecycle and who may take each step, against the migrated schema.
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({TicketService.class, TicketDecisionHandler.class, SlaPolicyService.class,
    TicketCodeGenerator.class, LabUser.class, LabNotifier.class, NotificationInboxService.class,
    NotificationTypes.class, LabNotificationConfig.class, WorkflowService.class,
    WorkflowDefinitions.class, LabWorkflowConfig.class, TimeConfig.class,
    PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabTicketIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    MigratedDatabase.migrate(POSTGRES);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  TicketService tickets;
  @MockitoBean
  UserContext userContext;
  @MockitoBean
  FileStore fileStore;
  @MockitoBean
  ObjectStorage objectStorage;

  String student;
  String otherStudent;
  String technician;
  String otherTechnician;

  @BeforeEach
  void setup() {
    student = "student-" + UUID.randomUUID();
    otherStudent = "student-" + UUID.randomUUID();
    technician = "tech-" + UUID.randomUUID();
    otherTechnician = "tech-" + UUID.randomUUID();
  }

  private void actAs(String userId, String role) {
    reset(userContext);
    when(userContext.currentUserId()).thenReturn(userId);
    when(userContext.currentFullName()).thenReturn("User " + userId);
    when(userContext.currentWorkspace()).thenReturn(Optional.empty());
    when(userContext.roles()).thenReturn(Set.of(role));
    when(userContext.hasRole(role)).thenReturn(true);
    when(userContext.isStaff()).thenReturn("TECHNICIAN".equals(role) || "ADMIN".equals(role));
  }

  private TicketResponse newTicket() {
    actAs(student, "STUDENT");
    return tickets.create(
        new TicketCreateRequest("Máy chiếu không lên hình",
            "Máy chiếu bật đèn nhưng không có tín hiệu", "PROJECTOR", null, null, null),
        null, UUID.randomUUID()).ticket();
  }

  private TicketResponse inProgress() {
    TicketResponse ticket = newTicket();
    actAs(technician, "TECHNICIAN");
    tickets.accept(ticket.id());
    tickets.start(ticket.id());
    return ticket;
  }

  private TicketResponse resolved() {
    TicketResponse ticket = inProgress();
    tickets.resolve(ticket.id(), new ResolveRequest("Đã thay cáp", "Cáp đứt"));
    return ticket;
  }

  @Test
  void ticketGetsDeadlinesFromThePolicyOfItsPriority() {
    TicketResponse ticket = newTicket();

    assertThat(ticket.status()).isEqualTo("NEW");
    assertThat(ticket.code()).startsWith("TK-");
    assertThat(Duration.between(ticket.responseDueAt(), ticket.resolutionDueAt())).isPositive();
  }

  @Test
  void onlyTheAssignedStaffMemberStartsATicket() {
    TicketResponse ticket = newTicket();

    assertThatThrownBy(() -> tickets.accept(ticket.id()))
        .isInstanceOf(ActionNotAllowedException.class);
    actAs(technician, "TECHNICIAN");
    assertThat(tickets.accept(ticket.id()).status()).isEqualTo("ASSIGNED");

    actAs(otherTechnician, "TECHNICIAN");
    assertThatThrownBy(() -> tickets.start(ticket.id()))
        .isInstanceOf(ActionNotAllowedException.class);
    actAs(technician, "TECHNICIAN");
    assertThat(tickets.start(ticket.id()).status()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void resolvingNeedsANoteAndARootCause() {
    TicketResponse ticket = inProgress();

    assertThatThrownBy(() -> tickets.resolve(ticket.id(), new ResolveRequest(null, "Cáp đứt")))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("RESOLUTION_NOTE_REQUIRED"));
    assertThatThrownBy(() -> tickets.resolve(ticket.id(), new ResolveRequest("Đã thay cáp", " ")))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("ROOT_CAUSE_REQUIRED"));
    assertThat(tickets.resolve(ticket.id(), new ResolveRequest("Đã thay cáp", "Cáp đứt")).status())
        .isEqualTo("RESOLVED");
  }

  @Test
  void onlyTheRequesterConfirmsAndNeedsARating() {
    TicketResponse ticket = resolved();

    actAs(otherStudent, "STUDENT");
    assertThatThrownBy(() -> tickets.confirm(ticket.id(), new ConfirmRequest(4, null)))
        .isInstanceOf(ResourceNotFoundException.class);
    actAs(student, "STUDENT");
    assertThatThrownBy(() -> tickets.confirm(ticket.id(), new ConfirmRequest(null, null)))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("RATING_REQUIRED"));
    TicketResponse closed = tickets.confirm(ticket.id(), new ConfirmRequest(5, "Tốt"));

    assertThat(closed.status()).isEqualTo("CLOSED");
    assertThat(closed.rating()).isEqualTo(5);
    assertThatThrownBy(
        () -> tickets.addComment(ticket.id(), new TicketCommentRequest("Cảm ơn", null)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void reopeningIsLimitedToThreeTimes() {
    TicketResponse ticket = resolved();

    for (int i = 0; i < TicketService.MAX_REOPENS; i++) {
      actAs(student, "STUDENT");
      assertThat(tickets.reopen(ticket.id(), new ReopenRequest("Vẫn lỗi lần " + i)).status())
          .isEqualTo("IN_PROGRESS");
      actAs(technician, "TECHNICIAN");
      tickets.resolve(ticket.id(), new ResolveRequest("Đã xử lý lại", "Chưa dứt điểm"));
    }

    actAs(student, "STUDENT");
    assertThatThrownBy(() -> tickets.reopen(ticket.id(), new ReopenRequest("Lại lỗi")))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("TICKET_REOPEN_LIMIT"));
  }

  @Test
  void internalNotesAreStaffOnlyAndHiddenFromTheRequester() {
    TicketResponse ticket = inProgress();
    tickets.addComment(ticket.id(), new TicketCommentRequest("Đang kiểm tra cáp", false));
    tickets.addComment(ticket.id(), new TicketCommentRequest("Nghi hỏng cổng HDMI", true));

    actAs(student, "STUDENT");
    assertThatThrownBy(
        () -> tickets.addComment(ticket.id(), new TicketCommentRequest("Thử ghi chú nội bộ", true)))
        .isInstanceOf(ActionNotAllowedException.class);
    var bodies = tickets.detail(ticket.id()).comments().stream().map(c -> c.body()).toList();
    assertThat(bodies).containsExactly("Đang kiểm tra cáp");

    actAs(technician, "TECHNICIAN");
    assertThat(tickets.detail(ticket.id()).comments()).hasSize(2);
  }

  @Test
  void anotherStudentDoesNotSeeTheTicket() {
    TicketResponse ticket = newTicket();

    actAs(otherStudent, "STUDENT");
    assertThatThrownBy(() -> tickets.detail(ticket.id()))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
