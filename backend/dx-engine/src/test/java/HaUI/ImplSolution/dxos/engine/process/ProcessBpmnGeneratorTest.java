// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class ProcessBpmnGeneratorTest {

  private static String bpmn(String family) {
    return ProcessBpmnGenerator.generate(
        "shop.order", "Đơn hàng", ProcessFamily.find(family).orElseThrow());
  }

  private static Document parse(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void theResultIsWellFormedXmlOfOneExecutableProcess() throws Exception {
    Document document = parse(bpmn("approval"));

    assertEquals(1, document.getElementsByTagNameNS("*", "process").getLength());
    assertEquals(
        "shop.order",
        document
            .getElementsByTagNameNS("*", "process")
            .item(0)
            .getAttributes()
            .getNamedItem("id")
            .getNodeValue());
    assertEquals(
        "true",
        document
            .getElementsByTagNameNS("*", "process")
            .item(0)
            .getAttributes()
            .getNamedItem("isExecutable")
            .getNodeValue());
  }

  @Test
  void theProcessStartsInItsInitialState() {
    String xml = bpmn("approval");

    assertTrue(
        xml.contains(
            "<sequenceFlow id=\"f.start\" sourceRef=\"start\" targetRef=\"t_submitted\"/>"),
        xml);
  }

  @Test
  void aStateWithSeveralWaysOutHasAGatewayWithOneConditionPerAction() {
    String xml = bpmn("approval");

    assertTrue(xml.contains("<exclusiveGateway id=\"g_submitted\"/>"), xml);
    assertTrue(xml.contains("${action == 'approve'}"), xml);
    assertTrue(xml.contains("${action == 'reject'}"), xml);
  }

  @Test
  void aStateWithOneWayOutNeedsNoGateway() {
    String xml = bpmn("approval");

    // From a returned record the owner can only submit it again.
    assertTrue(xml.contains("<userTask id=\"t_returned\""), xml);
    assertFalse(xml.contains("<exclusiveGateway id=\"g_returned\""), xml);
    assertTrue(
        xml.contains("id=\"ft.returned\" sourceRef=\"t_returned\" targetRef=\"s_submitted\""), xml);
  }

  @Test
  void aHandlerStateWaitsForTheHandlerRoleAndAnOwnerStateForTheOwner() {
    String xml = bpmn("approval");

    assertTrue(
        xml.contains(
            "<userTask id=\"t_submitted\" name=\"Chờ xử lý\" flowable:candidateGroups=\"${handlerRole}\"/>"),
        xml);
    assertTrue(
        xml.contains(
            "<userTask id=\"t_returned\" name=\"Cần bổ sung\" flowable:assignee=\"${ownerId}\"/>"),
        xml);
  }

  @Test
  void aFinalStateEndsTheProcessAndSettingAStatusIsAServiceTask() {
    String xml = bpmn("approval");

    assertTrue(
        xml.contains(
            "<serviceTask id=\"s_approved\" flowable:expression=\"${recordStatus.set(execution, 'approved')}\"/>"),
        xml);
    assertTrue(xml.contains("id=\"fs.approved\" sourceRef=\"s_approved\" targetRef=\"end\""), xml);
    assertTrue(
        xml.contains("id=\"fs.returned\" sourceRef=\"s_returned\" targetRef=\"t_returned\""), xml);
    assertFalse(xml.contains("t_approved"), "a final state waits for nobody");
  }

  @Test
  void withdrawingIsNotAStepOfTheFlow() {
    assertFalse(bpmn("approval").contains("withdraw"));
    assertFalse(bpmn("work_item").contains("withdraw"));
  }

  @Test
  void everyFamilyMakesWellFormedXml() throws Exception {
    for (String name : ProcessFamily.names()) {
      parse(bpmn(name));
    }
  }

  @Test
  void aNameWithMarkupIsEscaped() throws Exception {
    String xml =
        ProcessBpmnGenerator.generate(
            "k", "A & B <\"x\">", ProcessFamily.find("approval").orElseThrow());

    assertTrue(xml.contains("name=\"A &amp; B &lt;&quot;x&quot;&gt;\""), xml);
    parse(xml);
  }
}
