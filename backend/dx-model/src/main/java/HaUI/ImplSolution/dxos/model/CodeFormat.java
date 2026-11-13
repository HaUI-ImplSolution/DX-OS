// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The shape of a readable record code, such as HS-{year}-{seq:5}: fixed text, the year the record
// is created in, and the sequence number written with a fixed number of digits.
public record CodeFormat(String text, List<Part> parts, int width, boolean yearly) {

  public static final int MAX_LENGTH = 60;
  public static final int MAX_WIDTH = 9;

  // One piece of a code: fixed text, the year, or the sequence number.
  public sealed interface Part permits Literal, Year, Sequence {}

  public record Literal(String text) implements Part {}

  public record Year() implements Part {}

  public record Sequence(int width) implements Part {}

  private static final Pattern TOKEN = Pattern.compile("\\{(year|seq:([0-9]+))}");
  private static final Pattern FIXED = Pattern.compile("[A-Za-z0-9._/-]*");

  // The format of a text; an IllegalArgumentException says what is wrong with it.
  public static CodeFormat parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("a code field needs a format such as HS-{year}-{seq:5}");
    }
    List<Part> parts = new ArrayList<>();
    Matcher token = TOKEN.matcher(text);
    int at = 0;
    int width = 0;
    boolean yearly = false;
    int longest = 0;
    while (token.find()) {
      longest += literal(text.substring(at, token.start()), parts);
      at = token.end();
      if (token.group(1).equals("year")) {
        if (yearly) {
          throw new IllegalArgumentException("the format has {year} twice");
        }
        yearly = true;
        parts.add(new Year());
        longest += 4;
      } else {
        if (width != 0) {
          throw new IllegalArgumentException("the format has {seq:N} twice");
        }
        width = Integer.parseInt(token.group(2));
        if (width < 1 || width > MAX_WIDTH) {
          throw new IllegalArgumentException(
              "{seq:N} writes 1 to " + MAX_WIDTH + " digits, not " + token.group(2));
        }
        parts.add(new Sequence(width));
        longest += width;
      }
    }
    longest += literal(text.substring(at), parts);
    if (width == 0) {
      throw new IllegalArgumentException("the format needs {seq:N}, the sequence number");
    }
    if (longest > MAX_LENGTH) {
      throw new IllegalArgumentException("a code is at most " + MAX_LENGTH + " characters");
    }
    return new CodeFormat(text, List.copyOf(parts), width, yearly);
  }

  private static int literal(String text, List<Part> parts) {
    if (text.isEmpty()) {
      return 0;
    }
    if (!FIXED.matcher(text).matches()) {
      throw new IllegalArgumentException(
          "the fixed text of a code holds letters, digits and . _ / - only, and a placeholder is"
              + " {year} or {seq:N}");
    }
    parts.add(new Literal(text));
    return text.length();
  }

  // The largest sequence number the format can write.
  public long last() {
    return (long) Math.pow(10, width) - 1;
  }

  // The code of a sequence number in a year.
  public String render(int year, long sequence) {
    StringBuilder code = new StringBuilder();
    for (Part part : parts) {
      switch (part) {
        case Literal literal -> code.append(literal.text());
        case Year ignored -> code.append(String.format("%04d", year));
        case Sequence seq -> code.append(String.format("%0" + seq.width() + "d", sequence));
      }
    }
    return code.toString();
  }

  // The counter the code draws from: one for each year, or one for all when the format has no year.
  public String period(int year) {
    return yearly ? String.valueOf(year) : "-";
  }
}
