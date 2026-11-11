// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/inbox")
@Tag(name = "Inbox", description = "The records waiting for the caller, on every applied model")
public class InboxStreamController {

  private final InboxSignal signal;
  private final UserContext user;

  public InboxStreamController(InboxSignal signal, UserContext user) {
    this.signal = signal;
    this.user = user;
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "A stream that says when the inboxes changed",
      description = "Server-sent events for any signed-in user. After a change to any record the"
          + " stream sends the frame \"event: changed\" with the data {}, which holds no content"
          + " and is the same for everyone; read the inbox again to see what changed. A comment"
          + " line \": ping\" comes every 20 seconds, and the server closes the stream after 10"
          + " minutes, so connect again with a fresh token. A person holds up to 8 streams, and"
          + " opening another closes the oldest. 503 when the server already holds as many streams"
          + " as it serves.")
  public ResponseEntity<SseEmitter> stream() {
    return signal.open(user.currentUserId())
        .map(stream -> ResponseEntity.ok().cacheControl(CacheControl.noCache())
            // Tells a proxy in front not to hold the frames back.
            .header("X-Accel-Buffering", "no").body(stream))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
  }
}
