package HaUI.ImplSolution.DX_Farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record DiagnosisJobResponse(@JsonProperty("job_id")String jobId,@JsonProperty("report_id")Long reportId,@JsonProperty("request_id")String requestId,String status,String stage,@JsonProperty("error_message")String errorMessage,@JsonProperty("created_at")Instant createdAt,@JsonProperty("updated_at")Instant updatedAt,@JsonProperty("completed_at")Instant completedAt,DiagnosisResponse diagnosis){}
