// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.model.Model.Document;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class DocumentRendererTest {

  private static final Map<String, String> RECORD =
      Map.of("title", "Hoãn thi", "amount", "8.000.000", "empty", "");

  private static String text(byte[] pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document);
    }
  }

  private static Document document(List<Document.Block> body, String title) {
    return new Document("result", "Kết quả", "ket-qua", List.of(), title, body);
  }

  private static byte[] render(Document document) {
    return DocumentRenderer.render(
        document, name -> RECORD.getOrDefault(name, ""), name -> "Label " + name);
  }

  @Test
  void aPlaceholderIsReplacedByTheValueOfTheRecord() {
    assertEquals(
        "Hồ sơ Hoãn thi trị giá 8.000.000 đồng",
        DocumentRenderer.fill(
            "Hồ sơ {{title}} trị giá {{amount}} đồng", name -> RECORD.getOrDefault(name, "")));
  }

  @Test
  void aValueThatLooksLikeAReplacementIsWrittenAsItIs() {
    assertEquals("cost $1 and \\2", DocumentRenderer.fill("cost {{v}}", name -> "$1 and \\2"));
  }

  @Test
  void aPlaceholderWithoutAValueIsEmpty() {
    assertEquals("a  b", DocumentRenderer.fill("a {{missing}} b", name -> ""));
  }

  @Test
  void aTextWithoutPlaceholdersStaysAsItIs() {
    assertEquals("plain {text}", DocumentRenderer.fill("plain {text}", name -> "x"));
  }

  @Test
  void theResultIsAPdfWithTheTitleHeadingTextAndFields() throws IOException {
    Document document =
        document(
            List.of(
                new Document.Heading("Thông tin"),
                new Document.Text("Hồ sơ {{title}}"),
                new Document.Fields(List.of("amount", "empty")),
                new Document.Signatures(List.of("Người nộp", "Cán bộ"))),
            "GIẤY XÁC NHẬN {{title}}");

    byte[] pdf = render(document);

    assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    String text = text(pdf);
    assertTrue(text.contains("GIẤY XÁC NHẬN Hoãn thi"), text);
    assertTrue(text.contains("Thông tin"), text);
    assertTrue(text.contains("Hồ sơ Hoãn thi"), text);
    assertTrue(text.contains("Label amount"), text);
    assertTrue(text.contains("8.000.000"), text);
    assertTrue(text.contains("Người nộp"), text);
    assertTrue(text.contains("Cán bộ"), text);
  }

  @Test
  void aFieldWithoutAValueShowsADash() throws IOException {
    String text = text(render(document(List.of(new Document.Fields(List.of("empty"))), null)));

    assertTrue(text.contains(DocumentRenderer.EMPTY), text);
  }

  @Test
  void aDocumentWithoutATitleStillRenders() throws IOException {
    String text = text(render(document(List.of(new Document.Text("just text")), "  ")));

    assertTrue(text.contains("just text"), text);
  }
}
