// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

// Same envelope as the backend API. errorCode is only present for an error.
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp,
    @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, "OK", data, Instant.now(), null);
  }

  public static ApiResponse<Void> error(String errorCode, String message) {
    return new ApiResponse<>(false, message, null, Instant.now(), errorCode);
  }
}
