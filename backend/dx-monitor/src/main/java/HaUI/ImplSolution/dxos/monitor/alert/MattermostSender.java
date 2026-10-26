// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.alert;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Posts a message to the administrators' chat channel. A failed post is logged and never thrown.
@Component
public class MattermostSender {

  private static final Logger log = LoggerFactory.getLogger(MattermostSender.class);
  private static final String ADMIN_FILE = "admin.url";

  private final MonitorProperties.Mattermost settings;
  private final HttpClient client;

  public MattermostSender(MonitorProperties properties) {
    this.settings = properties.mattermost();
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(settings.connectTimeoutMs())).build();
  }

  public boolean isEnabled() {
    return webhook().isPresent();
  }

  public void send(String text) {
    Optional<String> url = webhook();
    if (url.isEmpty()) {
      return;
    }
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url.get()))
          .timeout(Duration.ofMillis(settings.readTimeoutMs()))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"" + escape(text) + "\"}")).build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 300) {
        log.warn("The chat webhook answered {}", response.statusCode());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException e) {
      log.warn("The chat webhook could not be reached: {}", e.getClass().getSimpleName());
    }
  }

  private Optional<String> webhook() {
    if (present(settings.adminUrl())) {
      return Optional.of(settings.adminUrl().strip());
    }
    if (present(settings.dir())) {
      try {
        Path file = Path.of(settings.dir(), ADMIN_FILE);
        if (Files.isRegularFile(file)) {
          String content = Files.readString(file).strip();
          if (!content.isEmpty()) {
            return Optional.of(content);
          }
        }
      } catch (IOException e) {
        log.warn("The chat webhook file could not be read: {}", e.getClass().getSimpleName());
      }
    }
    return present(settings.url()) ? Optional.of(settings.url().strip()) : Optional.empty();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static String escape(
      String text) {StringBuilder out=new StringBuilder(text.length()+8);for(char c:text.toCharArray()){switch(c){case'"'->out.append("\\\"");case'\\'->out.append("\\\\");case'\n'->out.append("\\n");default->out.append(c<0x20?' ':c);}}return out.toString();}
}
