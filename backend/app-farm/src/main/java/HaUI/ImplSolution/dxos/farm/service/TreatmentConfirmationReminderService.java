// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.event.TreatmentConfirmationOverdueEvent;
import HaUI.ImplSolution.dxos.farm.model.TreatmentApplication;
import HaUI.ImplSolution.dxos.farm.model.TreatmentConfirmationStatus;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentApplicationRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reminds the engineer channel of spraying reports that have waited longer than
// app.treatment-confirmation-wait-hours; a report is posted again on every run until it is decided.
@Service
public class TreatmentConfirmationReminderService {

  private final TreatmentApplicationRepository applicationRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final Duration wait;

  public TreatmentConfirmationReminderService(TreatmentApplicationRepository applicationRepository,
      ApplicationEventPublisher events, Clock clock,
      @Value("${app.treatment-confirmation-wait-hours:24}") int waitHours) {
    this.applicationRepository = applicationRepository;
    this.events = events;
    this.clock = clock;
    this.wait = Duration.ofHours(waitHours);
  }

  // Returns how many reports were posted.
  @Transactional(readOnly = true)
  public int remindOverdue() {
    int posted = 0;
    for (TreatmentApplication application : applicationRepository
        .findByConfirmationStatusAndReportedAtBefore(
            TreatmentConfirmationStatus.PENDING_CONFIRMATION, clock.instant().minus(wait))) {
      var parcel = application.getCropSeason().getParcel();
      events.publishEvent(new TreatmentConfirmationOverdueEvent(
          application.getDiagnosis().getDiseaseReport().getId(), parcel.getCode(),
          parcel.getFarmerName(), application.getPesticide().getName(),
          application.getReportedAt()));
      posted++;
    }
    return posted;
  }
}
