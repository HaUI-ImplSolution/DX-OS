// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

import HaUI.ImplSolution.dxos.model.ProcessFamily;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Action;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Actor;
import HaUI.ImplSolution.dxos.model.ProcessFamily.State;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Writes the BPMN of a declared state machine. Each state a record can wait in is a user task, the
// action taken leaves it through a gateway, and the status of the record is written by a service
// task on the way into the next state. Withdrawing is not a step: it ends the process from
// outside. Names hold only letters, digits and underscores, so the dot in a flow id keeps the
// parts apart and the prefix keeps the kinds of flow apart: no two ids can be equal.
public final class ProcessBpmnGenerator {

  private ProcessBpmnGenerator() {}

  public static String generate(String key, String name, ProcessFamily definition) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        .append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n")
        .append("             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        .append("             xmlns:flowable=\"http://flowable.org/bpmn\"\n")
        .append("             targetNamespace=\"http://dxos/processes\">\n")
        .append("  <process id=\"")
        .append(escape(key))
        .append("\" name=\"")
        .append(escape(name))
        .append("\" isExecutable=\"true\">\n")
        .append("    <startEvent id=\"start\"/>\n")
        .append("    <sequenceFlow id=\"f.start\" sourceRef=\"start\" targetRef=\"t_")
        .append(definition.initial())
        .append("\"/>\n");

    Set<String> targets = new LinkedHashSet<>();
    for (State state : definition.states()) {
      List<Action> leaving = leaving(definition, state.name());
      if (state.terminal() || leaving.isEmpty()) {
        continue;
      }
      boolean handled = leaving.stream().anyMatch(action -> action.by() == Actor.HANDLER);
      xml.append("    <userTask id=\"t_")
          .append(state.name())
          .append("\" name=\"")
          .append(escape(state.label()))
          .append("\" ")
          .append(
              handled
                  ? "flowable:candidateGroups=\"${handlerRole}\""
                  : "flowable:assignee=\"${ownerId}\"")
          .append("/>\n");
      if (leaving.size() == 1) {
        // One way out needs no gateway; the rules already check that the action fits the state.
        targets.add(leaving.get(0).to());
        xml.append("    <sequenceFlow id=\"ft.")
            .append(state.name())
            .append("\" sourceRef=\"t_")
            .append(state.name())
            .append("\" targetRef=\"s_")
            .append(leaving.get(0).to())
            .append("\"/>\n");
        continue;
      }
      xml.append("    <sequenceFlow id=\"ft.")
          .append(state.name())
          .append("\" sourceRef=\"t_")
          .append(state.name())
          .append("\" targetRef=\"g_")
          .append(state.name())
          .append("\"/>\n")
          .append("    <exclusiveGateway id=\"g_")
          .append(state.name())
          .append("\"/>\n");
      for (Action action : leaving) {
        targets.add(action.to());
        xml.append("    <sequenceFlow id=\"fg.")
            .append(state.name())
            .append(".")
            .append(action.name())
            .append("\" sourceRef=\"g_")
            .append(state.name())
            .append("\" targetRef=\"s_")
            .append(action.to())
            .append("\">\n")
            .append("      <conditionExpression xsi:type=\"tFormalExpression\">${action == '")
            .append(action.name())
            .append("'}</conditionExpression>\n")
            .append("    </sequenceFlow>\n");
      }
    }
    for (String target : targets) {
      xml.append("    <serviceTask id=\"s_")
          .append(target)
          .append("\" flowable:expression=\"${recordStatus.set(execution, '")
          .append(target)
          .append("')}\"/>\n")
          .append("    <sequenceFlow id=\"fs.")
          .append(target)
          .append("\" sourceRef=\"s_")
          .append(target)
          .append("\" targetRef=\"")
          .append(definition.isTerminal(target) ? "end" : "t_" + target)
          .append("\"/>\n");
    }
    xml.append("    <endEvent id=\"end\"/>\n").append("  </process>\n").append("</definitions>\n");
    return xml.toString();
  }

  private static List<Action> leaving(ProcessFamily definition, String state) {
    return definition.actions().stream()
        .filter(action -> !ProcessFamily.WITHDRAW.equals(action.name()) && action.allowedIn(state))
        .toList();
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
