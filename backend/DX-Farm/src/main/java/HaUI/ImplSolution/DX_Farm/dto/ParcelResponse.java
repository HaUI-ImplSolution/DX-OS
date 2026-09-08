package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ParcelResponse(Long id,String code,String farmerName,BigDecimal areaHectares,String location,Instant createdAt,Instant updatedAt){}
