// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.model.Model;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

// The addresses events can be sent to. A target has a name, an address and an optional secret for
// the signature. The names and addresses live in the file named by engine.webhook.file, which stays
// outside git because an address may hold a key, and a model file only names a target. The file is
// read again when it changes. A model that names no target uses the default one, made of
// engine.webhook.url and engine.webhook.secret.
//
// The file is a mapping with a "targets" key; each target maps a name to url, secret and format
// (json when left out):
// targets:
// ops:
// url: https://chat.example.org/hooks/abc
// secret: change-me
@Component
public class WebhookTargets {

  // format is json (the message as it is) or chat (a {"text": ...} body that Mattermost and Slack
  // accept).
  public record Target(String url, String secret, String format) {}

  private static final Logger log = LoggerFactory.getLogger(WebhookTargets.class);
  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,39}");

  private final Path file;
  private final Target fallback;
  private Map<String, Target> named = Map.of();
  private FileTime readAt;

  public WebhookTargets(@Value("${engine.webhook.file:}") String file,
      @Value("${engine.webhook.url:}") String url,
      @Value("${engine.webhook.secret:}") String secret,
      @Value("${engine.webhook.format:json}") String format) {
    this.file = file == null || file.isBlank() ? null : Path.of(file.strip());
    this.fallback =
        usable(url) && formatOk(format) ? new Target(url.strip(), secret, format.strip()) : null;
    if (this.fallback == null && !url.isBlank()) {
      log.error("ENGINE_WEBHOOK_URL or ENGINE_WEBHOOK_FORMAT is not valid, so the default target is"
          + " not used");
    }
  }

  // An http or https address with a host; the address itself is never logged since it may hold a
  // key.
  static boolean usable(String address) {
    try {
      URI uri = URI.create(address.strip());
      return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
          || "https".equalsIgnoreCase(uri.getScheme()));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static boolean formatOk(String format) {
    return "json".equals(format.strip()) || "chat".equals(format.strip());
  }

  // True when a message that belongs to a model, or to no model, has somewhere to go. Without a
  // model the default target is used.
  public boolean sendsFor(Optional<Model> model) {
    return model.map(this::sends).orElse(fallback != null);
  }

  public Optional<Target> targetFor(Optional<Model> model) {
    return model.map(this::of).orElseGet(() -> Optional.ofNullable(fallback));
  }

  // True when the history of the model is meant to be sent: it names a target or the default
  // target exists. A named target that is missing keeps its messages in the outbox, where they stop
  // and wait for a retry once the file has it again.
  public boolean sends(Model model) {
    return model.webhook() != null || fallback != null;
  }

  // The target the history of a model goes to, empty when the model has none to send to.
  public Optional<Target> of(Model model) {
    return model.webhook() == null ? Optional.ofNullable(fallback) : named(model.webhook());
  }

  // The reason a model cannot be applied, empty when its target exists or it names none.
  public Optional<String> missing(Model model) {
    if (model.webhook() == null || named(model.webhook()).isPresent()) {
      return Optional.empty();
    }
    return Optional.of("webhook target " + model.webhook() + " is not configured");
  }

  private synchronized Optional<Target> named(String name) {
    refresh();
    return Optional.ofNullable(named.get(name));
  }

  private void refresh() {
    if (file == null) {
      return;
    }
    FileTime modified;
    try {
      modified = Files.getLastModifiedTime(file);
    } catch (IOException e) {
      named = Map.of();
      readAt = null;
      return;
    }
    if (modified.equals(readAt)) {
      return;
    }
    named = read();
    readAt = modified;
  }

  private Map<String, Target> read() {
    Map<String, Target> targets = new LinkedHashMap<>();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Object root = new Yaml().load(reader);
      Object list = root instanceof Map<?, ?> map ? map.get("targets") : null;
      if (list instanceof Map<?, ?> entries) {
        entries.forEach((key, value) -> add(targets, String.valueOf(key), value));
      }
    } catch (IOException | RuntimeException e) {
      log.error("The webhook file could not be read ({})", e.getClass().getSimpleName());
    }
    return targets;
  }

  private void add(Map<String, Target> targets, String name, Object value) {
    String url =
        value instanceof Map<?, ?> map && map.get("url") != null ? String.valueOf(map.get("url"))
            : "";
    Object format =
        value instanceof Map<?, ?> map && map.get("format") != null ? map.get("format") : "json";
    if (!NAME.matcher(name).matches() || !usable(url) || !formatOk(String.valueOf(format))) {
      log.error("The webhook target {} is skipped: it needs a name matching [a-z][a-z0-9_]*, an "
          + "http or https url and a format of json or chat", name);
      return;
    }
    Object secret = ((Map<?, ?>) value).get("secret");
    targets.put(name, new Target(url.strip(), secret == null ? "" : String.valueOf(secret),
        String.valueOf(format).strip()));
  }
}
