// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

// The AI service answered with a 4xx: it understood the request and refused it (for example an
// image that failed its quality gate). detail is the explanation the service gave, if any.
public class AiRequestRejectedException extends AiServiceException {

  private final int status;
  private final String detail;

  public AiRequestRejectedException(int status, String detail, Throwable cause) {
    super("AI service rejected the request: HTTP " + status, cause);
    this.status = status;
    this.detail = detail;
  }

  public int status() {
    return status;
  }

  public String detail() {
    return detail;
  }
}
