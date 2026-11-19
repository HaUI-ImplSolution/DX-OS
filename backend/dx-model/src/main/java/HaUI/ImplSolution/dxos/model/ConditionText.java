// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import HaUI.ImplSolution.dxos.model.ProcessFamily.Condition;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Group;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// Reads a condition made of comparisons joined by and, or and not, with parentheses. not binds
// tighter than and, and and tighter than or. A comparison is a field name, an operator and a value
// that is one word or is in quotes. The comparisons themselves are checked by the caller.
final class ConditionText {

  interface Leaf {
    ProcessFamily.Comparison read(String field, String op, String value);
  }

  static final class Syntax extends RuntimeException {
    Syntax(String message) {
      super(message);
    }
  }

  private enum Kind {
    WORD,
    QUOTED,
    OPERATOR,
    OPEN,
    CLOSE
  }

  private record Token(Kind kind, String text) {}

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

  private static final int MAX_COMPARISONS = 8;
  private static final int MAX_NESTING = 3;

  private final List<Token> tokens;
  private int at;
  private int comparisons;
  private int nesting;
  private Leaf leaf;

  ConditionText(String text) {
    this.tokens = tokenize(text);
  }

  Condition parse(Leaf leaf) {
    this.leaf = leaf;
    Condition condition = or();
    if (at < tokens.size()) {
      throw new Syntax("unexpected '" + tokens.get(at).text() + "'");
    }
    return condition;
  }

  private Condition or() {
    List<Condition> parts = new ArrayList<>();
    parts.add(and());
    while (isWord("or")) {
      at++;
      parts.add(and());
    }
    return parts.size() == 1 ? parts.get(0) : new Group("or", parts);
  }

  private Condition and() {
    List<Condition> parts = new ArrayList<>();
    parts.add(not());
    while (isWord("and")) {
      at++;
      parts.add(not());
    }
    return parts.size() == 1 ? parts.get(0) : new Group("and", parts);
  }

  private Condition not() {
    if (isWord("not") && !(at + 1 < tokens.size() && tokens.get(at + 1).kind() == Kind.OPERATOR)) {
      at++;
      return new Group("not", List.of(not()));
    }
    if (at < tokens.size() && tokens.get(at).kind() == Kind.OPEN) {
      at++;
      if (++nesting > MAX_NESTING) {
        throw new Syntax("parentheses nest deeper than " + MAX_NESTING);
      }
      Condition inner = or();
      if (at >= tokens.size() || tokens.get(at).kind() != Kind.CLOSE) {
        throw new Syntax("a parenthesis is not closed");
      }
      at++;
      nesting--;
      return inner;
    }
    return comparison();
  }

  private Condition comparison() {
    String field = take(Kind.WORD, "a field name");
    if (!NAME.matcher(field).matches()) {
      throw new Syntax("'" + field + "' is not a field name");
    }
    String op = take(Kind.OPERATOR, "an operator after " + field);
    if (at >= tokens.size()
        || (tokens.get(at).kind() != Kind.WORD && tokens.get(at).kind() != Kind.QUOTED)) {
      throw new Syntax("a value is missing after " + field + " " + op);
    }
    String value = tokens.get(at++).text();
    if (++comparisons > MAX_COMPARISONS) {
      throw new Syntax("there are more than " + MAX_COMPARISONS + " comparisons");
    }
    return leaf.read(field, op, value);
  }

  private String take(Kind kind, String what) {
    if (at >= tokens.size() || tokens.get(at).kind() != kind) {
      String found = at < tokens.size() ? "'" + tokens.get(at).text() + "'" : "the end";
      throw new Syntax("expected " + what + ", found " + found);
    }
    return tokens.get(at++).text();
  }

  private boolean isWord(String word) {
    return at < tokens.size()
        && tokens.get(at).kind() == Kind.WORD
        && tokens.get(at).text().equals(word);
  }

  private static List<Token> tokenize(String text) {
    List<Token> out = new ArrayList<>();
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '(') {
        out.add(new Token(Kind.OPEN, "("));
        i++;
      } else if (c == ')') {
        out.add(new Token(Kind.CLOSE, ")"));
        i++;
      } else if (c == '"' || c == '\'') {
        int end = text.indexOf(c, i + 1);
        if (end < 0) {
          throw new Syntax("a quote is not closed");
        }
        out.add(new Token(Kind.QUOTED, text.substring(i + 1, end)));
        i = end + 1;
      } else if (operatorAt(text, i) != null) {
        String op = operatorAt(text, i);
        out.add(new Token(Kind.OPERATOR, op));
        i += op.length();
      } else {
        boolean value = !out.isEmpty() && out.get(out.size() - 1).kind() == Kind.OPERATOR;
        int end = i;
        while (end < text.length()) {
          char d = text.charAt(end);
          if (Character.isWhitespace(d) || d == '(' || d == ')') {
            break;
          }
          if (!value && operatorAt(text, end) != null) {
            break;
          }
          end++;
        }
        out.add(new Token(Kind.WORD, text.substring(i, end)));
        i = end;
      }
    }
    return out;
  }

  private static String operatorAt(String text, int i) {
    for (String op : Condition.OPERATORS) {
      if (text.startsWith(op, i)) {
        return op;
      }
    }
    return null;
  }
}
