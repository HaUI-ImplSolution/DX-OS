// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.host.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.config.TimeConfig;
import HaUI.ImplSolution.dxos.core.notification.NotificationInboxService;
import HaUI.ImplSolution.dxos.core.notification.NotificationTypes;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowDefinitions;
import HaUI.ImplSolution.dxos.core.workflow.WorkflowService;
import HaUI.ImplSolution.dxos.lab.LabNotificationConfig;
import HaUI.ImplSolution.dxos.lab.LabWorkflowConfig;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanRejectRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanResponse;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanReturnRequest;
import HaUI.ImplSolution.dxos.lab.model.Device;
import HaUI.ImplSolution.dxos.lab.model.DeviceStatus;
import HaUI.ImplSolution.dxos.lab.model.ReturnCondition;
import HaUI.ImplSolution.dxos.lab.repository.DeviceRepository;
import HaUI.ImplSolution.dxos.lab.service.DeviceLoanDecisionHandler;
import HaUI.ImplSolution.dxos.lab.service.DeviceLoanService;
import HaUI.ImplSolution.dxos.lab.service.DeviceMaintenanceService;
import HaUI.ImplSolution.dxos.lab.service.LabNotifier;
import HaUI.ImplSolution.dxos.lab.service.LabUser;
import java.time.Clock;
import java.time.LocalDate;
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

// Device loans: who approves whom, the limits on period and open loans, and what a return does to
// the device, against the migrated schema.
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DeviceLoanService.class, DeviceMaintenanceService.class, DeviceLoanDecisionHandler.class,
    LabUser.class, LabNotifier.class, NotificationInboxService.class, NotificationTypes.class,
    LabNotificationConfig.class, WorkflowService.class, WorkflowDefinitions.class,
    LabWorkflowConfig.class, TimeConfig.class, PersistenceTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabLoanIntegrationTest {

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
  DeviceLoanService loans;
  @Autowired
  DeviceRepository devices;
  @Autowired
  NotificationInboxService inbox;
  @Autowired
  Clock clock;
  @MockitoBean
  UserContext userContext;

  LocalDate today;
  String student;
  String otherStudent;
  String lecturer;
  String technician;

  @BeforeEach
  void setup() {
    today = LocalDate.now(clock);
    student = "student-" + UUID.randomUUID();
    otherStudent = "student-" + UUID.randomUUID();
    lecturer = "lecturer-" + UUID.randomUUID();
    technician = "tech-" + UUID.randomUUID();
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

  private Device device() {
    return devices.save(Device.builder().assetCode("D-" + UUID.randomUUID()).deviceType("Laptop")
        .model("Test").build());
  }

  private LoanResponse request(Device device, int days) {
    return loans.create(new LoanCreateRequest(device.getId(), "Thực hành", today.plusDays(days)),
        UUID.randomUUID());
  }

  private DeviceStatus statusOf(Device device) {
    return devices.findById(device.getId()).orElseThrow().getStatus();
  }

  @Test
  void lecturerApprovesAStudentLoanAndTheDeviceIsTaken() {
    Device device = device();
    actAs(student, "STUDENT");
    LoanResponse loan = request(device, 3);
    assertThat(loan.status()).isEqualTo("PENDING");
    assertThat(statusOf(device)).isEqualTo(DeviceStatus.AVAILABLE);

    actAs(lecturer, "LECTURER");
    assertThat(loans.approve(loan.id()).status()).isEqualTo("APPROVED");
    assertThat(statusOf(device)).isEqualTo(DeviceStatus.ON_LOAN);

    actAs(otherStudent, "STUDENT");
    assertThatThrownBy(() -> request(device, 3)).isInstanceOfSatisfying(
        PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo("DEVICE_NOT_AVAILABLE"));

    actAs(student, "STUDENT");
    assertThat(inbox.listMine(false, 0, 10).items()).extracting(item -> item.type())
        .containsExactly("LOAN_APPROVED");
  }

  @Test
  void approvalFollowsTheRoleOfTheBorrower() {
    actAs(student, "STUDENT");
    LoanResponse studentLoan = request(device(), 3);

    assertThatThrownBy(() -> loans.approve(studentLoan.id()))
        .isInstanceOf(ActionNotAllowedException.class);
    actAs(otherStudent, "STUDENT");
    assertThatThrownBy(() -> loans.approve(studentLoan.id()))
        .isInstanceOf(ResourceNotFoundException.class);
    actAs(technician, "TECHNICIAN");
    assertThatThrownBy(() -> loans.approve(studentLoan.id()))
        .isInstanceOf(ActionNotAllowedException.class);

    actAs(lecturer, "LECTURER");
    LoanResponse lecturerLoan = request(device(), 3);
    assertThatThrownBy(() -> loans.approve(lecturerLoan.id()))
        .isInstanceOf(ActionNotAllowedException.class);
    actAs(technician, "TECHNICIAN");
    assertThat(loans.approve(lecturerLoan.id()).status()).isEqualTo("APPROVED");
  }

  @Test
  void periodAndOpenLoanLimitsAreEnforced() {
    actAs(student, "STUDENT");
    assertThatThrownBy(() -> request(device(), DeviceLoanService.MAX_LOAN_DAYS + 1))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("LOAN_PERIOD_INVALID"));

    request(device(), 3);
    request(device(), 3);
    assertThatThrownBy(() -> request(device(), 3)).isInstanceOfSatisfying(
        PokaYokeViolationException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo("TOO_MANY_OPEN_LOANS"));
  }

  @Test
  void rejectionNeedsAReasonAndLeavesTheDeviceFree() {
    Device device = device();
    actAs(student, "STUDENT");
    LoanResponse loan = request(device, 3);

    actAs(lecturer, "LECTURER");
    assertThatThrownBy(() -> loans.reject(loan.id(), new LoanRejectRequest(" ")))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("REJECTION_REASON_REQUIRED"));
    LoanResponse rejected = loans.reject(loan.id(), new LoanRejectRequest("Đã có lịch dùng"));

    assertThat(rejected.status()).isEqualTo("REJECTED");
    assertThat(rejected.rejectionReason()).isEqualTo("Đã có lịch dùng");
    assertThat(statusOf(device)).isEqualTo(DeviceStatus.AVAILABLE);
    actAs(student, "STUDENT");
    assertThat(inbox.listMine(false, 0, 10).items()).extracting(item -> item.type())
        .containsExactly("LOAN_REJECTED");
  }

  @Test
  void damagedReturnNeedsANoteAndSendsTheDeviceToRepair() {
    Device device = device();
    actAs(student, "STUDENT");
    LoanResponse loan = request(device, 3);
    actAs(lecturer, "LECTURER");
    loans.approve(loan.id());

    actAs(technician, "TECHNICIAN");
    assertThatThrownBy(
        () -> loans.returnDevice(loan.id(), new LoanReturnRequest(ReturnCondition.DAMAGED, null)))
        .isInstanceOfSatisfying(PokaYokeViolationException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("RETURN_NOTE_REQUIRED"));
    LoanResponse returned = loans.returnDevice(loan.id(),
        new LoanReturnRequest(ReturnCondition.DAMAGED, "Vỡ màn hình"));

    assertThat(returned.status()).isEqualTo("RETURNED");
    assertThat(statusOf(device)).isEqualTo(DeviceStatus.UNDER_REPAIR);
  }

  @Test
  void goodReturnMakesTheDeviceAvailableAgain() {
    Device device = device();
    actAs(student, "STUDENT");
    LoanResponse loan = request(device, 3);
    actAs(lecturer, "LECTURER");
    loans.approve(loan.id());

    actAs(technician, "TECHNICIAN");
    loans.returnDevice(loan.id(), new LoanReturnRequest(ReturnCondition.GOOD, null));

    assertThat(statusOf(device)).isEqualTo(DeviceStatus.AVAILABLE);
  }

  @Test
  void sameKeyReturnsTheSameLoan() {
    Device device = device();
    actAs(student, "STUDENT");
    UUID key = UUID.randomUUID();
    LoanCreateRequest request =
        new LoanCreateRequest(device.getId(), "Thực hành", today.plusDays(2));

    LoanResponse first = loans.create(request, key);
    LoanResponse second = loans.create(request, key);

    assertThat(second.id()).isEqualTo(first.id());
  }
}
