// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Asks a clamd daemon whether bytes are malware (INSTREAM over TCP). Off while storage.scan.host
// is empty.
@Component
public class ClamAvScanner {

  private static final int CHUNK = 8192;

  private final String host;
  private final int port;
  private final int timeoutMs;

  public ClamAvScanner(@Value("${storage.scan.host:}") String host,
      @Value("${storage.scan.port:3310}") int port,
      @Value("${storage.scan.timeout-ms:30000}") int timeoutMs) {
    this.host = host == null ? "" : host.strip();
    this.port = port;
    this.timeoutMs = timeoutMs;
  }

  public boolean enabled() {
    return !host.isEmpty();
  }

  // The name of the signature that matched, or empty for a clean file. Throws when the scan cannot
  // give an answer, so an unchecked file is never taken for a clean one.
  public Optional<String> scan(byte[] data) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeoutMs);
      socket.setSoTimeout(timeoutMs);
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
      for (int offset = 0; offset < data.length; offset += CHUNK) {
        int length = Math.min(CHUNK, data.length - offset);
        out.writeInt(length);
        out.write(data, offset, length);
      }
      out.writeInt(0);
      out.flush();
      String reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII)
          .replace("\0", "").strip();
      if (reply.endsWith(" FOUND")) {
        return Optional.of(reply.substring(reply.indexOf(':') + 1, reply.length() - 6).strip());
      }
      if (reply.endsWith(" OK")) {
        return Optional.empty();
      }
      throw new ScannerUnavailableException("The virus scanner gave no verdict: " + reply, null);
    } catch (IOException e) {
      throw new ScannerUnavailableException("The virus scanner cannot be reached", e);
    }
  }
}
