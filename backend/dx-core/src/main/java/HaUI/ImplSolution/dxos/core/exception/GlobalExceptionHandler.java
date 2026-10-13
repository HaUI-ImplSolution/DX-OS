// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.exception;

import HaUI.ImplSolution.dxos.core.api.exception.AccountNotLinkedException;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + defaultMessage(error))
        .collect(Collectors.joining(", "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("ERR_VALIDATION", message));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error("ERR_NOT_FOUND", ex.getMessage()));
  }

  // A path no controller of the running app serves, such as a route of another app.
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error("ERR_NOT_FOUND", "Không tìm thấy đường dẫn này"));
  }

  @ExceptionHandler(InvalidStateException.class)
  public ResponseEntity<ApiResponse<Void>> handleInvalidState(InvalidStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("ERR_INVALID_STATE", ex.getMessage()));
  }

  @ExceptionHandler(AiServiceException.class)
  public ResponseEntity<ApiResponse<Void>> handleAiService(AiServiceException ex) {
    log.warn("AI service unavailable: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error("ERR_AI_UNAVAILABLE",
            "AI diagnosis service is temporarily unavailable, please try again later"));
  }

  @ExceptionHandler(PokaYokeViolationException.class)
  public ResponseEntity<ApiResponse<Map<String, Object>>> handlePokaYoke(
      PokaYokeViolationException ex) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("errorCode", ex.getErrorCode());
    data.putAll(ex.getData());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), data));
  }

  @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class,
      PessimisticLockException.class, LockTimeoutException.class})
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("ERR_CONCURRENT_MODIFICATION",
            "The record was modified by someone else, reload and try again"));
  }

  @ExceptionHandler(ActionNotAllowedException.class)
  public ResponseEntity<ApiResponse<Void>> handleActionNotAllowed(ActionNotAllowedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error("ERR_ACTION_NOT_ALLOWED", ex.getMessage()));
  }

  @ExceptionHandler(AccountNotLinkedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccountNotLinked(AccountNotLinkedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error("ERR_ACCOUNT_NOT_LINKED", ex.getMessage()));
  }

  @ExceptionHandler(KeycloakAdminException.class)
  public ResponseEntity<ApiResponse<Void>> handleKeycloakAdmin(KeycloakAdminException ex) {
    log.warn("Keycloak admin call failed: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(ApiResponse.error("ERR_ACCOUNT_SERVICE_UNAVAILABLE",
            "Account service is unavailable, the workspace was not created"));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error("ERR_BAD_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    // @PreAuthorize ném exception này ngay trong lời gọi controller (AOP method security),
    // khác với vi phạm rule ở authorizeHttpRequests (đã xử lý bởi RestAccessDeniedHandler).
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error("ERR_FORBIDDEN", "Không đủ quyền thực hiện thao tác này"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("ERR_BAD_REQUEST", ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("ERR_INTERNAL", "Internal server error"));
  }

  private static String defaultMessage(FieldError error) {
    return error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
  }
}
