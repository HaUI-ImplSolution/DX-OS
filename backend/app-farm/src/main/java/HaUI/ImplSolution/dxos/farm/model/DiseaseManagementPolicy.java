// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "disease_management_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiseaseManagementPolicy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Mỗi bệnh có tối đa một policy
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_id", nullable = false, unique = true)
  private Disease disease;

  // Trạng thái quản lý bệnh (vd: NO_CURATIVE_TREATMENT)
  @Column(nullable = false)
  private String managementStatus;

  // false: không được suy ra thuốc chữa trực tiếp cho bệnh này
  @Column(nullable = false)
  private boolean directTreatmentRuleAllowed;

  // Phạm vi được phép dùng hoá chất (vd: NONE_FOR_VIRUS)
  @Column(nullable = false)
  private String chemicalRuleScope;

  // Các hành động khuyến nghị, mỗi dòng một hành động
  @Column(columnDefinition = "text")
  private String recommendedActions;

  private String source;

  private String version;
}
