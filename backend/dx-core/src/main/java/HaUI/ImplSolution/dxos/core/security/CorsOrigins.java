// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CorsOrigins {

  private CorsOrigins() {}

  static List<String> validate(List<String> configured) {
    Set<String> origins = new LinkedHashSet<>();
    for (String configuredOrigin : configured) {
      String origin = configuredOrigin == null ? "" : configuredOrigin.trim();
      if (origin.isEmpty()) {
        continue;
      }
      origins.add(validate(origin));
    }
    return List.copyOf(origins);
  }

  static String validate(String origin) {
    if (origin.contains("*")) {
      throw invalid(origin, "wildcards are not allowed");
    }
    final URI uri;
    try {
      uri = new URI(origin);
    } catch (URISyntaxException exception) {
      throw invalid(origin, "it is not a valid URI");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw invalid(origin, "the scheme must be http or https");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank() || uri.getRawUserInfo() != null) {
      throw invalid(origin, "a host without user information is required");
    }
    if ((uri.getRawPath() != null && !uri.getRawPath().isEmpty()) || uri.getRawQuery() != null
        || uri.getRawFragment() != null) {
      throw invalid(origin, "an origin must not contain a path, query or fragment");
    }
    if (uri.getPort() == 0 || uri.getPort() > 65535) {
      throw invalid(origin, "the port must be between 1 and 65535");
    }
    String lowerHost = host.toLowerCase(Locale.ROOT);
    if (scheme.equals("http") && !isLoopback(lowerHost)) {
      throw invalid(origin, "http is allowed only for localhost or a loopback address");
    }
    return scheme + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT);
  }

  private static boolean isLoopback(String host) {
    return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")
        || host.equals("::1");
  }

  private static IllegalArgumentException invalid(String origin, String reason) {
    return new IllegalArgumentException("Invalid CORS origin '" + origin + "': " + reason);
  }
}
