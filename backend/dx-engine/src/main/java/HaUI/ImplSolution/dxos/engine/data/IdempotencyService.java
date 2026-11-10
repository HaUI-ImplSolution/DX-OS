// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

// Does the work of a request once per Idempotency-Key. The key is kept with the work in one
// transaction, so a request that fails leaves no key behind and may be sent again, and two requests
// with the same key at the same time do the work once: the second waits for the first and then gets
// its answer. A key belongs to the person who sent it and to one request, so it cannot show someone
// else's answer or stand for a different request.
@Service
public class IdempotencyService {

  public static final String HEADER = "Idempotency-Key";
  private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

  // The answer, and whether it is the one kept from an earlier request with the same key.
  public record Outcome(Map<String, Object> answer, boolean replayed) {}

  private final JdbcTemplate jdbc;
  private final UserContext user;
  private final ObjectMapper json;

  public IdempotencyService(JdbcTemplate jdbc, UserContext user, ObjectMapper json) {
    this.jdbc = jdbc;
    this.user = user;
    this.json = json;
  }

  // Runs the work, or returns the answer kept for the key. kind and target name the request, body
  // is what the caller sent; without a key the work simply runs.
  @Transactional
  public Outcome once(String key, String kind, String target, Object body,
      Supplier<Map<String, Object>> work) {
    if (key == null) {
      return new Outcome(work.get(), false);
    }
    if (!KEY.matcher(key).matches()) {
      throw new ModelValidationException(HEADER,
          "must be 1 to 128 letters, digits, dots, dashes, underscores or colons");
    }
    String subject = user.currentUserId();
    String request = kind + " " + target + " " + hash(body);
    // A conflicting row of another transaction makes this wait until that one commits or rolls
    // back.
    int inserted = jdbc.update("INSERT INTO engine.idempotency (subject, key, request) "
        + "VALUES (?, ?, ?) ON CONFLICT (subject, key) DO NOTHING", subject, key, request);
    if (inserted == 0) {
      Map<String, Object> kept =
          jdbc.queryForMap("SELECT request, response::text AS response FROM engine.idempotency "
              + "WHERE subject = ? AND key = ?", subject, key);
      if (!request.equals(kept.get("request"))) {
        throw new InvalidStateException("The Idempotency-Key was used for a different request",
            "IDEMPOTENCY_KEY_REUSED");
      }
      return new Outcome(read((String) kept.get("response")), true);
    }
    Map<String, Object> answer = work.get();
    jdbc.update("UPDATE engine.idempotency SET response = ?::jsonb WHERE subject = ? AND key = ?",
        json.writeValueAsString(answer), subject, key);
    return new Outcome(answer, false);
  }

  // Numbers keep their written form, so the kept answer reads as the first one did.
  @SuppressWarnings("unchecked")
  private Map<String, Object> read(String text) {
    return json.readerFor(Map.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .readValue(text);
  }

  // The same body hashes the same however its fields are ordered.
  private String hash(Object body) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(json.writeValueAsString(sorted(body)).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Object sorted(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> ordered = new TreeMap<>();
      map.forEach((name, item) -> ordered.put(String.valueOf(name), sorted(item)));
      return ordered;
    }
    if (value instanceof List<?> list) {
      List<Object> items = new ArrayList<>();
      list.forEach(item -> items.add(sorted(item)));
      return items;
    }
    return value;
  }
}
