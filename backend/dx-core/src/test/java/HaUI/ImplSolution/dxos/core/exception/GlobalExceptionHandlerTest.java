// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.exception;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void pokaYokeKeepsItsOwnCodeAtTopLevelAndInData() {
    LocalDate until = LocalDate.of(2026, 11, 1);
    ResponseEntity<ApiResponse<Map<String, Object>>> response =
        handler.handlePokaYoke(new PokaYokeViolationException("POKA_YOKE_VIETGAP_VIOLATION",
            "isolated", Map.of("harvestLockedUntil", until)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().errorCode()).isEqualTo("POKA_YOKE_VIETGAP_VIOLATION");
    assertThat(response.getBody().data()).containsEntry("errorCode", "POKA_YOKE_VIETGAP_VIOLATION")
        .containsEntry("harvestLockedUntil", until);
  }

  @Test
  void commonFailuresCarryAStableCode() {
    assertThat(handler.handleNotFound(new ResourceNotFoundException("x")).getBody().errorCode())
        .isEqualTo("ERR_NOT_FOUND");
    assertThat(handler.handleInvalidState(new InvalidStateException("x")).getBody().errorCode())
        .isEqualTo("ERR_INVALID_STATE");
    assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getBody().errorCode())
        .isEqualTo("ERR_FORBIDDEN");
    assertThat(handler.handleUnexpected(new RuntimeException("x")).getBody().errorCode())
        .isEqualTo("ERR_INTERNAL");
  }

  @Test
  void clientRequestErrorsKeepTheirStatusInsteadOfBecomingServerErrors() {
    var unsupported = handler.handleUnsupportedMediaType(
        new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of()));
    assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(unsupported.getBody().errorCode()).isEqualTo("ERR_UNSUPPORTED_MEDIA_TYPE");

    var method = handler.handleMethodNotAllowed(
        new HttpRequestMethodNotSupportedException(HttpMethod.DELETE.name()));
    assertThat(method.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(method.getBody().errorCode()).isEqualTo("ERR_METHOD_NOT_ALLOWED");

    var missingPart =
        handler.handleUnreadableRequest(new MissingServletRequestPartException("image"));
    assertThat(missingPart.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(missingPart.getBody().errorCode()).isEqualTo("ERR_BAD_REQUEST");

    var brokenJson = handler.handleUnreadableRequest(
        new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])));
    assertThat(brokenJson.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void successResponsesHaveNoErrorCode() {
    assertThat(ApiResponse.success("data").errorCode()).isNull();
  }
}
