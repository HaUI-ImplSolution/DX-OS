// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.documents;

import HaUI.ImplSolution.dxos.model.Model.Document;
import java.util.function.Function;
import java.util.regex.Matcher;

// Turns a print template and the values of one record into a PDF. Placeholders are replaced by the
// text the record shows; nothing else in a template is interpreted.
final class DocumentRenderer {

  // Shown in a table for a value the record does not have.
  static final String EMPTY = "—";

  private DocumentRenderer() {}

  // value gives the text of a field or column of the record, empty when it has none; label gives
  // the heading of a field in a table.
  static byte[] render(
      Document document, Function<String, String> value, Function<String, String> label) {
    try (PdfPages pages = new PdfPages(document.label())) {
      if (document.title() != null && !document.title().isBlank()) {
        pages.title(fill(document.title(), value));
      }
      for (Document.Block block : document.body()) {
        switch (block) {
          case Document.Heading heading -> pages.heading(fill(heading.text(), value));
          case Document.Text text -> pages.text(fill(text.text(), value));
          case Document.Fields fields ->
              fields
                  .fields()
                  .forEach(
                      field -> {
                        String shown = value.apply(field);
                        pages.row(label.apply(field), shown.isBlank() ? EMPTY : shown);
                      });
          case Document.Signatures signatures ->
              pages.signatures(signatures.captions().stream().map(c -> fill(c, value)).toList());
        }
      }
      return pages.finish();
    }
  }

  // The text with each {{name}} replaced by the value of the record.
  static String fill(String template, Function<String, String> value) {
    Matcher matcher = Document.PLACEHOLDER.matcher(template);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(value.apply(matcher.group(1))));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
