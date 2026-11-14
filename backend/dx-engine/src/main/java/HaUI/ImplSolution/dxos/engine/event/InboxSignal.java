// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Tells the pages that are open that something changed in the inboxes. Each open page holds one
// stream; after a line of any record history has committed, every stream gets an empty
// "changed" frame and the page reads its own inbox again, so a frame carries no content and is the
// same for everyone. A comment frame goes out every 20 seconds so that a gateway or a browser does
// not drop a quiet stream, and a stream is closed after 10 minutes, when the page connects again
// with a fresh token. A person holds a few streams at once; opening another closes the oldest, so
// one account cannot take every place. The streams live in this process, so it serves one running
// backend.
@Component
public class InboxSignal {

  static final long LIFETIME_MS = TimeUnit.MINUTES.toMillis(10);
  static final int MAX_STREAMS = 2000;
  static final int MAX_PER_PERSON = 8;
  private static final long PING_SECONDS = 20;

  // Each open stream with the person it belongs to, and each person's streams oldest first.
  private final Map<SseEmitter, String> streams = new ConcurrentHashMap<>();
  private final Map<String, ArrayDeque<SseEmitter>> byPerson = new HashMap<>();
  private final AtomicBoolean queued = new AtomicBoolean();
  // One thread writes to every stream, so a slow page never holds up the transaction that changed
  // a record.
  private final ScheduledExecutorService writer =
      Executors.newSingleThreadScheduledExecutor(
          task -> {
            Thread thread = new Thread(task, "inbox-signal");
            thread.setDaemon(true);
            return thread;
          });

  public InboxSignal() {
    writer.scheduleWithFixedDelay(
        () -> sendToAll(SseEmitter.event().comment("ping")),
        PING_SECONDS,
        PING_SECONDS,
        TimeUnit.SECONDS);
  }

  // A new stream for the person, or empty when this process already holds the most it serves.
  public synchronized Optional<SseEmitter> open(String person) {
    ArrayDeque<SseEmitter> mine = byPerson.computeIfAbsent(person, key -> new ArrayDeque<>());
    while (mine.size() >= MAX_PER_PERSON) {
      mine.peekFirst().complete();
      forget(mine.peekFirst());
    }
    if (streams.size() >= MAX_STREAMS) {
      if (mine.isEmpty()) {
        byPerson.remove(person);
      }
      return Optional.empty();
    }
    SseEmitter stream = new SseEmitter(LIFETIME_MS);
    Runnable closed = () -> forget(stream);
    stream.onCompletion(closed);
    stream.onTimeout(closed);
    stream.onError(error -> closed.run());
    streams.put(stream, person);
    mine.addLast(stream);
    // The first frame sends the headers, so the page knows at once that the stream is open.
    send(stream, SseEmitter.event().comment("open"));
    return Optional.of(stream);
  }

  private synchronized void forget(SseEmitter stream) {
    String person = streams.remove(stream);
    if (person != null) {
      ArrayDeque<SseEmitter> mine = byPerson.get(person);
      mine.remove(stream);
      if (mine.isEmpty()) {
        byPerson.remove(person);
      }
    }
  }

  // Called where a line of a record history is written; the frames go out once that transaction
  // has committed, or at once when there is no transaction.
  public void changed() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              announce();
            }
          });
    } else {
      announce();
    }
  }

  // Changes that arrive before the writer runs share one frame.
  private void announce() {
    if (streams.isEmpty() || !queued.compareAndSet(false, true)) {
      return;
    }
    writer.execute(
        () -> {
          queued.set(false);
          sendToAll(SseEmitter.event().name("changed").data("{}"));
        });
  }

  private void sendToAll(SseEmitter.SseEventBuilder frame) {
    streams.keySet().forEach(stream -> send(stream, frame));
  }

  private void send(SseEmitter stream, SseEmitter.SseEventBuilder frame) {
    try {
      stream.send(frame);
    } catch (IOException | IllegalStateException gone) {
      forget(stream);
    }
  }

  @PreDestroy
  void stop() {
    writer.shutdownNow();
    streams.keySet().forEach(SseEmitter::complete);
  }
}
