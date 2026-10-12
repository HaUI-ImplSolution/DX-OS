// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.exception;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps the errors that only Farm raises; the shared handler covers the rest.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FarmExceptionHandler {

  @ExceptionHandler(AiTriageException.class)
  public ResponseEntity<ApiResponse<Void>> handleAiTriage(AiTriageException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ApiResponse.error("ERR_AI_TRIAGE", ex.getMessage()));
  }
}
