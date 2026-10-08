// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.model;

public enum SeasonType {
  // Dong Xuan: winter-spring season
  DONG_XUAN("DX"),
  // He Thu: summer-autumn season
  HE_THU("HT"),
  // Thu Dong: autumn-winter season
  THU_DONG("TD");

  private final String codeSuffix;

  SeasonType(String codeSuffix) {
    this.codeSuffix = codeSuffix;
  }

  // Two letters closing a crop season code (e.g. 2026DX)
  public String codeSuffix() {
    return codeSuffix;
  }
}
