// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.health;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Asks one service whether it answers.
@Component
public class ServiceProbe {

  private static final String DATABASE = "jdbc";

  private final JdbcTemplate jdbc;
  private final HttpClient client;
  private final Duration timeout;

  public ServiceProbe(JdbcTemplate jdbc, MonitorProperties properties) {
    this.jdbc = jdbc;
    this.timeout = Duration.ofMillis(properties.probe().timeoutMs());
    this.client = HttpClient.newBuilder().connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  // up: the service answered as expected; detail: why not, otherwise null.
  public record Result(boolean up, String detail) {}

  public Result probe(MonitorProperties.Probe.Target target) {
    try {
      if (DATABASE.equals(target.url())) {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return new Result(true, null);
      }
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(target.url())).timeout(timeout).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status < 200 || status >= 400) {
        return new Result(false, "HTTP " + status);
      }
      String expected = target.bodyContains();
      if (expected != null && !expected.isEmpty() && !response.body().contains(expected)) {
        return new Result(false, "Unexpected answer");
      }
      return new Result(true, null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new Result(false, "Interrupted");
    } catch (Exception e) {
      return new Result(false, describe(e));
    }
  }

  private static String describe(Exception e) {
    if (e instanceof java.net.http.HttpTimeoutException || e instanceof TimeoutException) {
      return "Timeout";
    }
    return e.getClass().getSimpleName();
  }
}
