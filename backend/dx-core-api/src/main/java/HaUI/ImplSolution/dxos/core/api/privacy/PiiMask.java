// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.privacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Hides personal data in free text before it leaves the system: e-mail addresses and runs of nine
// or more digits (phone, citizen ID, student ID, bank account), which may be split by single
// spaces, dots or hyphens. IPv4 addresses, codes such as TK-2026-000123 and URLs are left alone.
public final class PiiMask {

  public static final String MASK = "[đã ẩn]";

  private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
  private static final Pattern DIGITS =
      Pattern.compile("(?<![\\w-])\\d(?:[ .-]?\\d){8,}(?![\\w-])");
  private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
  private static final Pattern URL = Pattern.compile("^https?://.*");

  private PiiMask() {}

  public static String mask(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String withoutEmail = EMAIL.matcher(text).replaceAll(Matcher.quoteReplacement(MASK));
    return DIGITS.matcher(withoutEmail).replaceAll(match -> {
      String run = match.group();
      return IPV4.matcher(run).matches() ? run : Matcher.quoteReplacement(MASK);
    });
  }

  // Masks every string of a message payload (nested maps and lists included); URLs stay as they
  // are, so links keep working. The payload itself is not changed.
  public static Map<String, Object> maskPayload(Map<String, Object> payload) {
    Map<String, Object> masked = new LinkedHashMap<>();
    payload.forEach((key, value) -> masked.put(key, maskValue(value)));
    return masked;
  }

  @SuppressWarnings("unchecked")
  private static Object maskValue(Object value) {
    if (value instanceof String text) {
      return URL.matcher(text).matches() ? text : mask(text);
    }
    if (value instanceof Map<?, ?> map) {
      return maskPayload((Map<String, Object>) map);
    }
    if (value instanceof List<?> list) {
      List<Object> items = new ArrayList<>();
      list.forEach(item -> items.add(maskValue(item)));
      return items;
    }
    return value;
  }
}
