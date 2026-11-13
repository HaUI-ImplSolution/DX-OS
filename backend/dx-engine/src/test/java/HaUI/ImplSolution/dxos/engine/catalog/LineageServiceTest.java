// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.engine.catalog.LineageService.Edge;
import HaUI.ImplSolution.dxos.engine.catalog.LineageService.FieldLineage;
import HaUI.ImplSolution.dxos.engine.catalog.LineageService.Lineage;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.util.List;
import org.junit.jupiter.api.Test;

// The lineage is read from the model file alone, so it runs without a database.
class LineageServiceTest {

  private static final String MODEL =
      """
      format: 0
      name: support
      webhook: chat
      roles:
        - name: REQUESTER
        - name: AGENT
          staff: true
      units:
        - name: NORTH
          label: North
      entities:
        ticket:
          fields:
            title: {type: text, required: true}
            kind: {type: choice, options: [bug, ask], default: ask}
            note: {type: text, required_when: "kind == bug"}
            site: {type: unit}
            evidence: {type: file}
            fix: {type: text, renamed_from: solution}
          access: {REQUESTER: own, AGENT: unit}
          process:
            family: work_item
            handler: AGENT
            actions:
              resolve: {require: [fix]}
        comment:
          fields:
            ticket: {type: reference, to: ticket, required: true}
            text: {type: text}
          access: {REQUESTER: own, AGENT: read}
          owner_via: ticket
      """;

  private final Lineage lineage = LineageService.lineage(ModelParser.parse(MODEL), 3);

  private FieldLineage field(String entity, String name) {
    return lineage.fields().stream()
        .filter(f -> f.entity().equals(entity) && f.field().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static boolean has(List<Edge> edges, String kind, String name) {
    return edges.stream().anyMatch(e -> e.kind().equals(kind) && e.name().equals(name));
  }

  @Test
  void everyFieldCarriesTheModelAndItsVersion() {
    assertThat(lineage.model()).isEqualTo("support");
    assertThat(lineage.version()).isEqualTo(3);
    assertThat(lineage.fields()).hasSize(8);
  }

  @Test
  void onlyTheRolesThatMayWriteAreOrigins() {
    List<Edge> origins = field("comment", "text").origins();
    assertThat(has(origins, "input", "REQUESTER")).isTrue();
    assertThat(has(origins, "input", "AGENT")).isFalse();
  }

  @Test
  void aDefaultAReferenceAndARenameAreOrigins() {
    assertThat(field("ticket", "kind").origins()).contains(new Edge("default", "ask", null));
    assertThat(has(field("comment", "ticket").origins(), "reference", "ticket")).isTrue();
    assertThat(has(field("ticket", "fix").origins(), "renamed_from", "solution")).isTrue();
  }

  @Test
  void theRulesThatReadAFieldAreItsUses() {
    assertThat(field("ticket", "kind").uses())
        .contains(new Edge("required_when", "note", "== bug"));
    assertThat(has(field("ticket", "fix").uses(), "require", "resolve")).isTrue();
    assertThat(has(field("comment", "ticket").uses(), "owner", "ticket")).isTrue();
    assertThat(has(field("ticket", "site").uses(), "unit_scope", "AGENT")).isTrue();
  }

  @Test
  void aValueGoesToHistoryTheFeedTheWebhookAndTheArchive() {
    List<Edge> out = field("ticket", "title").destinations();
    assertThat(has(out, "history", "updated")).isTrue();
    assertThat(has(out, "feed", "events")).isTrue();
    assertThat(has(out, "webhook", "chat")).isTrue();
    assertThat(has(out, "archive", "dxos archive")).isTrue();
  }

  @Test
  void aFileHasNoHistoryLineAndAnEntityWithoutProcessIsNotArchived() {
    assertThat(has(field("ticket", "evidence").destinations(), "history", "updated")).isFalse();
    assertThat(has(field("comment", "text").destinations(), "archive", "dxos archive")).isFalse();
  }
}
