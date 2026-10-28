// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// A stand-in for clamd that speaks the INSTREAM protocol.
class ClamAvScannerTest {

  private ServerSocket server;
  private final AtomicReference<String> command = new AtomicReference<>();
  private final ByteArrayOutputStream received = new ByteArrayOutputStream();

  private ClamAvScanner scannerAnswering(String reply) throws Exception {
    server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
    Thread thread = new Thread(() -> {
      try (var socket = server.accept()) {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        command.set(new String(in.readNBytes(10), StandardCharsets.US_ASCII));
        for (int length = in.readInt(); length > 0; length = in.readInt()) {
          received.write(in.readNBytes(length));
        }
        socket.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
      } catch (Exception ignored) {
        // The client side of the test reports any problem.
      }
    });
    thread.setDaemon(true);
    thread.start();
    return new ClamAvScanner("127.0.0.1", server.getLocalPort(), 3000);
  }

  @AfterEach
  void close() throws Exception {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void cleanFileIsSentInChunksAndAccepted() throws Exception {
    byte[] data = new byte[20000];
    java.util.Arrays.fill(data, (byte) 'a');

    assertThat(scannerAnswering("stream: OK").scan(data)).isEmpty();

    assertThat(command.get()).isEqualTo("zINSTREAM\0");
    assertThat(received.toByteArray()).isEqualTo(data);
  }

  @Test
  void infectedFileGivesTheSignatureName() throws Exception {
    assertThat(scannerAnswering("stream: Win.Test.EICAR_HDB-1 FOUND").scan(new byte[] {1}))
        .contains("Win.Test.EICAR_HDB-1");
  }

  @Test
  void errorReplyIsNotTakenForAVerdict() throws Exception {
    ClamAvScanner scanner = scannerAnswering("INSTREAM size limit exceeded. ERROR");

    assertThatThrownBy(() -> scanner.scan(new byte[] {1}))
        .isInstanceOf(ScannerUnavailableException.class);
  }

  @Test
  void unreachableScannerThrowsInsteadOfPassingTheFile() {
    ClamAvScanner scanner = new ClamAvScanner("127.0.0.1", 1, 500);

    assertThatThrownBy(() -> scanner.scan(new byte[] {1}))
        .isInstanceOf(ScannerUnavailableException.class);
  }

  @Test
  void isOffWithoutAHost() {
    assertThat(new ClamAvScanner("", 3310, 1000).enabled()).isFalse();
    assertThat(new ClamAvScanner("clamav", 3310, 1000).enabled()).isTrue();
  }
}
