package HaUI.ImplSolution.DX_Farm.service;

import java.util.List;

// Workflow case and entity types used by the parcel and crop season requests.
public final class ApprovalCaseTypes {

  public static final String PARCEL_APPROVAL = "PARCEL_APPROVAL";
  public static final String PARCEL_ENTITY = "PARCEL";
  public static final String PARCEL_EDIT_REQUEST = "PARCEL_EDIT_REQUEST";
  public static final String PARCEL_EDIT_ENTITY = "PARCEL_EDIT_REQUEST";
  public static final String CROP_SEASON_APPROVAL = "CROP_SEASON_APPROVAL";
  public static final String CROP_SEASON_ENTITY = "CROP_SEASON";
  public static final String DIAGNOSIS_APPROVAL = "DIAGNOSIS_APPROVAL";
  public static final String DIAGNOSIS_ENTITY = "DIAGNOSIS";
  public static final String TREATMENT_CONFIRMATION = "TREATMENT_CONFIRMATION";
  public static final String TREATMENT_ENTITY = "TREATMENT_APPLICATION";

  // Requests that count against a farmer's limit of waiting requests.
  public static final List<String> FARMER_REQUESTS =
      List.of(PARCEL_APPROVAL, PARCEL_EDIT_REQUEST, CROP_SEASON_APPROVAL);

  private ApprovalCaseTypes() {}
}
