package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TreatmentApplicationResponse(Long id,Long cropSeasonId,Long diagnosisId,String pesticideCode,BigDecimal doseAmountSnapshot,String doseUnitSnapshot,Integer phiDaysSnapshot,LocalDate appliedAt,LocalDate harvestLockedUntil){}
