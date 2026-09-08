package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;

public record TreatmentRuleResponse(Long id,String diseaseCode,String pesticideCode,BigDecimal doseAmount,String doseUnit,Integer phiDays,String notes,String source,String version){}
