package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CropSeasonResponse(Long id,String parcelCode,String riceVariety,LocalDate startDate,LocalDate expectedHarvestDate,String status,Instant harvestedAt){}
