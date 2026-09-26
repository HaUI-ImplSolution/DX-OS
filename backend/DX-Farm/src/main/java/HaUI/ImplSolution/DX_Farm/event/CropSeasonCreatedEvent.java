package HaUI.ImplSolution.DX_Farm.event;

import java.time.LocalDate;

// Published inside the transaction that stores a crop season request; plain values only.
public record CropSeasonCreatedEvent(String seasonCode,String parcelCode,String farmerName,String riceVariety,LocalDate startDate,LocalDate expectedHarvestDate,boolean resubmitted){}
