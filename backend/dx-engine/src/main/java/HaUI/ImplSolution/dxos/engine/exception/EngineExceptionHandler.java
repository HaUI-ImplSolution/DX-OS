// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.exception;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.model.ModelException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Errors of the model-driven endpoints; it runs before the general handler of the core.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EngineExceptionHandler {

  @ExceptionHandler(ModelValidationException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
      ModelValidationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("ERR_VALIDATION", ex.getMessage(), ex.fieldErrors()));
  }

  @ExceptionHandler(ModelException.class)
  public ResponseEntity<ApiResponse<List<String>>> handleModel(ModelException ex) {
    List<String> problems = ex.problems().stream().map(Object::toString).toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("ERR_MODEL_INVALID", "The model file has problems", problems));
  }
}
