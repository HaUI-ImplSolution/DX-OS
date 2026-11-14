// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.documents;

import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

// Lays text out on A4 pages and writes the PDF. The fonts are embedded, so Vietnamese letters print
// the same on every reader; a character the font lacks prints as a question mark.
final class PdfPages implements AutoCloseable {

  static final int MAX_PAGES = 50;

  private static final float MARGIN = 56;
  private static final float BODY = 11;
  private static final float LEADING = 15;
  private static final PDRectangle SIZE = PDRectangle.A4;
  private static final float WIDTH = SIZE.getWidth() - 2 * MARGIN;
  private static final float LABEL_WIDTH = WIDTH * 0.35f;
  private static final float SIGNATURE_SPACE = 64;

  private static volatile byte[] regularBytes;
  private static volatile byte[] boldBytes;

  enum Align {
    LEFT,
    CENTER
  }

  private final PDDocument pdf = new PDDocument();
  private final PDType0Font regular;
  private final PDType0Font bold;
  private final Map<Integer, Boolean> encodable = new HashMap<>();
  private final List<PDPage> pages = new ArrayList<>();
  private PDPageContentStream stream;
  private float y;

  PdfPages(String title) {
    try {
      regular = PDType0Font.load(pdf, new ByteArrayInputStream(font("Regular")), true);
      bold = PDType0Font.load(pdf, new ByteArrayInputStream(font("Bold")), true);
      pdf.getDocumentInformation().setTitle(clean(title, regular));
      pdf.getDocumentInformation().setCreator("DX-OS");
      newPage();
    } catch (IOException | RuntimeException e) {
      close();
      throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
    }
  }

  private static byte[] font(String style) throws IOException {
    byte[] cached = "Bold".equals(style) ? boldBytes : regularBytes;
    if (cached != null) {
      return cached;
    }
    try (InputStream in = PdfPages.class.getResourceAsStream("/fonts/NotoSans-" + style + ".ttf")) {
      if (in == null) {
        throw new IOException("the font NotoSans-" + style + " is missing");
      }
      byte[] bytes = in.readAllBytes();
      if ("Bold".equals(style)) {
        boldBytes = bytes;
      } else {
        regularBytes = bytes;
      }
      return bytes;
    }
  }

  void title(String text) {
    gap(2);
    for (String line : wrap(text, bold, 16, WIDTH)) {
      line(line, bold, 16, 22, Align.CENTER);
    }
    gap(10);
  }

  void heading(String text) {
    gap(8);
    for (String line : wrap(text, bold, 13, WIDTH)) {
      line(line, bold, 13, 18, Align.LEFT);
    }
    gap(3);
  }

  // Each line break of the text is a line break of the page, and a blank line is a gap.
  void text(String text) {
    for (String paragraph : text.split("\n", -1)) {
      if (paragraph.isBlank()) {
        gap(LEADING / 2);
        continue;
      }
      for (String line : wrap(paragraph, regular, BODY, WIDTH)) {
        line(line, regular, BODY, LEADING, Align.LEFT);
      }
    }
    gap(6);
  }

  // A row is a label and a value; the value may take several lines.
  void row(String label, String value) {
    List<String> labels = wrap(label, bold, BODY, LABEL_WIDTH - 8);
    List<String> values = wrap(value, regular, BODY, WIDTH - LABEL_WIDTH);
    int lines = Math.max(labels.size(), values.size());
    for (int i = 0; i < lines; i++) {
      ensure(LEADING);
      if (i < labels.size()) {
        show(labels.get(i), bold, BODY, MARGIN, y);
      }
      if (i < values.size()) {
        show(values.get(i), regular, BODY, MARGIN + LABEL_WIDTH, y);
      }
      y -= LEADING;
    }
    gap(3);
  }

  // The captions side by side, each over a blank space for the signature.
  void signatures(List<String> captions) {
    float column = WIDTH / captions.size();
    int lines = 1;
    List<List<String>> wrapped = new ArrayList<>();
    for (String caption : captions) {
      List<String> parts = wrap(caption, bold, BODY, column - 12);
      wrapped.add(parts);
      lines = Math.max(lines, parts.size());
    }
    gap(10);
    ensure(lines * LEADING + SIGNATURE_SPACE);
    for (int i = 0; i < wrapped.size(); i++) {
      float top = y;
      for (String part : wrapped.get(i)) {
        float width = width(part, bold, BODY);
        show(part, bold, BODY, MARGIN + i * column + (column - width) / 2, top);
        top -= LEADING;
      }
    }
    y -= lines * LEADING + SIGNATURE_SPACE;
  }

  byte[] finish() {
    try {
      closeStream();
      int total = pages.size();
      for (int i = 0; i < total; i++) {
        try (PDPageContentStream footer =
            new PDPageContentStream(
                pdf, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
          String label = (i + 1) + "/" + total;
          footer.beginText();
          footer.setFont(regular, 9);
          footer.newLineAtOffset(SIZE.getWidth() / 2 - width(label, regular, 9) / 2, 28);
          footer.showText(label);
          footer.endText();
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      // Without object streams, so a reader or a check can see the pages in the file.
      pdf.save(out, CompressParameters.NO_COMPRESSION);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    try {
      closeStream();
      pdf.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void gap(float height) {
    y -= height;
  }

  private void line(String text, PDType0Font font, float size, float leading, Align align) {
    ensure(leading);
    float x = align == Align.CENTER ? MARGIN + (WIDTH - width(text, font, size)) / 2 : MARGIN;
    show(text, font, size, x, y);
    y -= leading;
  }

  private void show(String text, PDType0Font font, float size, float x, float baseline) {
    try {
      stream.beginText();
      stream.setFont(font, size);
      stream.newLineAtOffset(x, baseline);
      stream.showText(text);
      stream.endText();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // Starts a page when the next block does not fit above the bottom margin.
  private void ensure(float height) {
    if (y - height < MARGIN) {
      newPage();
    }
  }

  private void newPage() {
    if (pages.size() >= MAX_PAGES) {
      throw new PokaYokeViolationException(
          "ERR_DOCUMENT_TOO_LONG", "The document would have more than " + MAX_PAGES + " pages");
    }
    try {
      closeStream();
      PDPage page = new PDPage(SIZE);
      pdf.addPage(page);
      pages.add(page);
      stream = new PDPageContentStream(pdf, page);
      y = SIZE.getHeight() - MARGIN;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void closeStream() throws IOException {
    if (stream != null) {
      stream.close();
      stream = null;
    }
  }

  // The lines of a text no wider than the width; a word wider than a line is cut.
  private List<String> wrap(String text, PDType0Font font, float size, float width) {
    String cleaned = clean(text, font);
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : cleaned.trim().split("\\s+")) {
      if (word.isEmpty()) {
        continue;
      }
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (width(candidate, font, size) <= width) {
        line = new StringBuilder(candidate);
        continue;
      }
      if (!line.isEmpty()) {
        lines.add(line.toString());
        line = new StringBuilder();
      }
      String rest = word;
      while (width(rest, font, size) > width) {
        int cut = rest.length();
        while (cut > 1 && width(rest.substring(0, cut), font, size) > width) {
          cut--;
        }
        lines.add(rest.substring(0, cut));
        rest = rest.substring(cut);
      }
      line.append(rest);
    }
    if (!line.isEmpty() || lines.isEmpty()) {
      lines.add(line.toString());
    }
    return lines;
  }

  private float width(String text, PDType0Font font, float size) {
    try {
      return font.getStringWidth(text) / 1000 * size;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // Keeps the characters the font can show and tabs and other spaces as one space.
  private String clean(String text, PDType0Font font) {
    StringBuilder out = new StringBuilder();
    text.codePoints()
        .forEach(
            code -> {
              if (Character.isWhitespace(code) || Character.isISOControl(code)) {
                out.append(' ');
              } else if (encodable.computeIfAbsent(code, c -> canShow(font, c))) {
                out.appendCodePoint(code);
              } else {
                out.append('?');
              }
            });
    return out.toString();
  }

  private static boolean canShow(PDType0Font font, int code) {
    try {
      font.encode(new String(Character.toChars(code)));
      return true;
    } catch (IllegalArgumentException | IOException e) {
      return false;
    }
  }
}
