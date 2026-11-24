// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelExtendsTest {

  private static final String TEMPLATE =
      """
      format: 1
      name: base
      roles:
        - name: USER
          description: Người dùng
        - name: STAFF
          staff: true
      entities:
        item:
          label: Mục
          fields:
            title: {type: text, label: Tên, required: true}
            note: {type: text, label: Ghi chú}
          access: {USER: own, STAFF: all}
      """;

  private static String message(Runnable action) {
    return assertThrows(ModelException.class, action::run).getMessage();
  }

  @Test
  void aFileThatExtendsNoneHasNoTemplate() {
    assertEquals(Optional.empty(), ModelExtends.templateOf("format: 1\nname: x\n"));
    assertEquals(Optional.empty(), ModelExtends.templateOf("- not\n- a mapping\n"));
    assertEquals(Optional.empty(), ModelExtends.templateOf(": : broken [\n"));
  }

  @Test
  void theTemplateAFileExtendsIsNamed() {
    assertEquals(
        Optional.of("support_desk"),
        ModelExtends.templateOf("extends: support_desk\nname: mine\n"));
  }

  @Test
  void extendsIsATextOfTheNameOfATemplate() {
    assertTrue(
        message(() -> ModelExtends.templateOf("extends: [a, b]\nname: x\n")).contains("extends"));
  }

  @Test
  void aFileThatExtendsATemplateNeedsItsOwnName() {
    assertTrue(
        message(() -> ModelExtends.check("extends: support_desk\n"))
            .contains("name is required in a file that extends a template"));
    assertEquals(
        Optional.of("support_desk"), ModelExtends.check("extends: support_desk\nname: mine\n"));
    assertEquals(Optional.empty(), ModelExtends.check("format: 1\nname: x\n"));
  }

  @Test
  void theFileIsLaidOverTheTemplateKeyByKey() {
    String file =
        """
        extends: base
        name: shop
        entities:
          item:
            label: Hàng
            fields:
              price: {type: number, label: Giá}
        """;

    Model model = ModelParser.parse(ModelExtends.merge(TEMPLATE, file));

    assertEquals("shop", model.name());
    Model.Entity item = model.entities().get("item");
    assertEquals("Hàng", item.label());
    assertEquals(List.of("title", "note", "price"), List.copyOf(item.fields().keySet()));
    assertEquals(Scope.OWN, item.access().get("USER"));
  }

  @Test
  void aKeySetToNullRemovesTheKeyOfTheTemplate() {
    String file =
        """
        extends: base
        name: shop
        entities:
          item:
            fields:
              note: null
        """;

    Model model = ModelParser.parse(ModelExtends.merge(TEMPLATE, file));

    assertEquals(List.of("title"), List.copyOf(model.entities().get("item").fields().keySet()));
  }

  @Test
  void rolesMergeByNameAndOtherListsAreReplaced() {
    String file =
        """
        extends: base
        name: shop
        roles:
          - name: STAFF
            description: Nhân viên cửa hàng
          - name: GUEST
        """;

    Model model = ModelParser.parse(ModelExtends.merge(TEMPLATE, file));

    assertEquals(
        List.of("USER", "STAFF", "GUEST"), model.roles().stream().map(Model.Role::name).toList());
    assertEquals("Nhân viên cửa hàng", model.roles().get(1).description());
  }

  @Test
  void aWholeEntityOfTheFileIsAdded() {
    String file =
        """
        extends: base
        name: shop
        entities:
          other:
            fields:
              a: {type: text}
            access: {STAFF: all}
        """;

    Model model = ModelParser.parse(ModelExtends.merge(TEMPLATE, file));

    assertEquals(List.of("item", "other"), List.copyOf(model.entities().keySet()));
  }

  @Test
  void aKeyWrittenTwiceInTheFileIsRefusedInsteadOfKeepingTheLastOne() {
    String file =
        """
        extends: base
        name: shop
        entities:
          item:
            access: {USER: own, USER: all}
        """;

    assertTrue(
        message(() -> ModelExtends.merge(TEMPLATE, file)).contains("key USER is written twice"));
  }

  @Test
  void theMergedFileKeepsNoExtendsKey() {
    String merged = ModelExtends.merge(TEMPLATE, "extends: base\nname: shop\n");

    assertFalse(merged.contains("extends"), merged);
  }

  @Test
  void aFileOfAnotherFormatThanTheTemplateIsRefused() {
    assertTrue(
        message(() -> ModelExtends.merge(TEMPLATE, "extends: base\nname: shop\nformat: 0\n"))
            .contains("format 0 differs from the template's 1"));
  }

  @Test
  void theFileNeedsANameAndTheTemplateMustBeReadable() {
    assertTrue(
        message(() -> ModelExtends.merge(TEMPLATE, "extends: base\n"))
            .contains("name is required"));
    assertTrue(
        message(() -> ModelExtends.merge("- not a model\n", "extends: base\nname: x\n"))
            .contains("the template is not a model file that can be read"));
    assertThrows(ModelException.class, () -> ModelExtends.merge(TEMPLATE, "- not a mapping\n"));
  }
}
