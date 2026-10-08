// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowActor;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowEvent;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowEventRepository;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(WorkflowService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowServiceIntegrationTest {

  private static final String TYPE = "DIAGNOSIS_APPROVAL";
  private static final String ENTITY = "DIAGNOSIS";
  private static final WorkflowActor ENGINEER = new WorkflowActor("eng-1", "Engineer One");
  private static final WorkflowActor OTHER = new WorkflowActor("eng-2", "Engineer Two");

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
  private WorkflowService workflowService;
  @Autowired
  private WorkflowEventRepository eventRepository;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private static long newEntityId() {
    return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
  }

  @Test
  void approvingAddsExactlyOneEventAndKeepsEarlierOnes() {
    long entityId = newEntityId();
    var created = workflowService.createCase(TYPE, ENTITY, entityId, ENGINEER);
    var before = eventRepository.findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(created.getId());

    var approved = workflowService.recordTransition(TYPE, ENTITY, entityId, WorkflowStatus.APPROVED,
        ENGINEER, null, null);

    var after = eventRepository.findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(created.getId());
    assertThat(approved.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
    assertThat(after).hasSize(before.size() + 1);
    assertThat(after.get(0).getId()).isEqualTo(before.get(0).getId());
    assertThat(after.get(after.size() - 1).getActorUserId()).isEqualTo("eng-1");
  }

  @Test
  void rejectedCaseCanBeResubmittedAndEveryStepStaysInTheLog() {
    long entityId = newEntityId();
    var created = workflowService.createCase(TYPE, ENTITY, entityId, ENGINEER);
    workflowService.recordTransition(TYPE, ENTITY, entityId, WorkflowStatus.REJECTED, ENGINEER,
        "Blurry photo", null);
    workflowService.recordTransition(TYPE, ENTITY, entityId, WorkflowStatus.PENDING, ENGINEER, null,
        null);
    workflowService.recordTransition(TYPE, ENTITY, entityId, WorkflowStatus.APPROVED, OTHER, null,
        null);

    var events = eventRepository.findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(created.getId());
    assertThat(events).extracting(event -> event.getAction().name()).containsExactly("SUBMITTED",
        "REJECTED", "RESUBMITTED", "APPROVED");
    assertThat(events.get(1).getReason()).isEqualTo("Blurry photo");
  }

  @Test
  void finalCaseCannotChangeAgain() {
    long entityId = newEntityId();
    workflowService.createCase(TYPE, ENTITY, entityId, ENGINEER);
    workflowService.recordTransition(TYPE, ENTITY, entityId, WorkflowStatus.APPROVED, ENGINEER,
        null, null);

    assertThatThrownBy(() -> workflowService.recordTransition(TYPE, ENTITY, entityId,
        WorkflowStatus.REJECTED, ENGINEER, "late", null)).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void pendingListMatchesTheCasesThatArePending() {
    long approvedId = newEntityId();
    long pendingId = newEntityId();
    workflowService.createCase(TYPE, ENTITY, approvedId, ENGINEER);
    workflowService.recordTransition(TYPE, ENTITY, approvedId, WorkflowStatus.APPROVED, ENGINEER,
        null, null);
    workflowService.createCase(TYPE, ENTITY, pendingId, ENGINEER);

    var page = workflowService.list(TYPE, WorkflowStatus.PENDING, null, 0, 1000);

    Long pendingInDb = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM workflow_case WHERE case_type = ? AND status = 'PENDING'", Long.class,
        TYPE);
    assertThat(page.totalElements()).isEqualTo(pendingInDb);
    assertThat(page.items()).extracting(item -> item.entityId()).contains(pendingId)
        .doesNotContain(approvedId);
  }

  @Test
  void assigningAnotherEngineersCaseNeedsOverride() {
    long entityId = newEntityId();
    var created = workflowService.createCase(TYPE, ENTITY, entityId, ENGINEER);
    workflowService.assign(created.getId(), ENGINEER, false);

    assertThatThrownBy(() -> workflowService.assign(created.getId(), OTHER, false))
        .isInstanceOf(InvalidStateException.class);
    assertThat(workflowService.assign(created.getId(), OTHER, true).assignedTo())
        .isEqualTo("eng-2");
  }

  @Test
  void eventsCannotBeUpdatedOrDeleted() {
    long entityId = newEntityId();
    var created = workflowService.createCase(TYPE, ENTITY, entityId, ENGINEER);
    WorkflowEvent event =
        eventRepository.findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(created.getId()).get(0);

    assertThatThrownBy(() -> jdbcTemplate
        .update("UPDATE workflow_event SET reason = 'edited' WHERE id = ?", event.getId()))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
        () -> jdbcTemplate.update("DELETE FROM workflow_event WHERE id = ?", event.getId()))
        .isInstanceOf(DataAccessException.class);
  }
}
