// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseTypeDefinition;
import HaUI.ImplSolution.dxos.farm.service.ApprovalCaseTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Every Farm request follows the standard approval flow.
@Configuration
public class FarmWorkflowConfig {

  @Bean
  public CaseTypeDefinition parcelApprovalCaseType() {
    return CaseTypeDefinition.approval(ApprovalCaseTypes.PARCEL_APPROVAL);
  }

  @Bean
  public CaseTypeDefinition parcelEditRequestCaseType() {
    return CaseTypeDefinition.approval(ApprovalCaseTypes.PARCEL_EDIT_REQUEST);
  }

  @Bean
  public CaseTypeDefinition cropSeasonApprovalCaseType() {
    return CaseTypeDefinition.approval(ApprovalCaseTypes.CROP_SEASON_APPROVAL);
  }

  @Bean
  public CaseTypeDefinition diagnosisApprovalCaseType() {
    return CaseTypeDefinition.approval(ApprovalCaseTypes.DIAGNOSIS_APPROVAL);
  }

  @Bean
  public CaseTypeDefinition treatmentConfirmationCaseType() {
    return CaseTypeDefinition.approval(ApprovalCaseTypes.TREATMENT_CONFIRMATION);
  }
}
