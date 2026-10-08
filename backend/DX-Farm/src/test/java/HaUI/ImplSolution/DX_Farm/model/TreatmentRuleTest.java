// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TreatmentRuleTest {

  private final TreatmentRule rule = TreatmentRule.builder().doseMin(new BigDecimal("2.00"))
      .doseMax(new BigDecimal("2.50")).build();

  @Test
  void allowsDoseOnlyInsideReviewedRange() {
    assertTrue(rule.allowsDose(new BigDecimal("2.00")));
    assertTrue(rule.allowsDose(new BigDecimal("2.20")));
    assertTrue(rule.allowsDose(new BigDecimal("2.50")));
    assertFalse(rule.allowsDose(new BigDecimal("1.99")));
    assertFalse(rule.allowsDose(new BigDecimal("2.51")));
    assertFalse(rule.allowsDose(null));
  }
}
