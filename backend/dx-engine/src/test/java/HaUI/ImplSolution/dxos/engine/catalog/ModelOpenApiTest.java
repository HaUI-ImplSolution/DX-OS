// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.meta.MetaController;
import HaUI.ImplSolution.dxos.engine.meta.MetaController.EntityMeta;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import HaUI.ImplSolution.dxos.model.Scope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelOpenApiTest {

  private static final String MODEL =
      """
      format: 1
      name: shop
      roles:
        - name: BUYER
        - name: SELLER
          staff: true
      entities:
        order:
          label: Đơn hàng
          fields:
            title: {type: text, label: Tiêu đề, required: true}
            amount: {type: number, label: Số tiền, min: 0}
            kind: {type: choice, label: Loại, options: [Lẻ, Sỉ]}
            scan: {type: file, label: Tệp}
          ai:
            tasks:
              classify: {engine: classifier, classifier: orders, input_fields: [title], output_field: kind}
              summarize: {input_fields: [title], output_field: title, instruction: Summarize, sources: [guide]}
          access: {BUYER: own, SELLER: all}
          process: {family: approval, handler: SELLER}
        note:
          fields:
            text: {type: text}
          access: {SELLER: all}
      """;

  private final ModelOpenApi openApi = new ModelOpenApi();

  private List<EntityMeta> metadata(Model model) {
    ModelRegistry registry = mock(ModelRegistry.class);
    when(registry.require("shop")).thenReturn(model);
    when(registry.version("shop")).thenReturn(4L);
    DataService data = mock(DataService.class);
    when(data.access(any(), any()))
        .thenReturn(new DataService.Access(Set.of(Scope.ALL), Set.of(Scope.ALL)));
    when(data.canHandle(any(), any())).thenReturn(true);
    UserContext user = mock(UserContext.class);
    when(user.clientRoles(anyString())).thenReturn(Set.of("SELLER"));
    MetaController meta = new MetaController(registry, data, user);
    return model.entities().keySet().stream()
        .map(name -> meta.entity("shop", name).data())
        .toList();
  }

  private JsonNode document() throws Exception {
    Model model = ModelParser.parse(MODEL);
    OpenAPI api = openApi.build(model, metadata(model));
    return new ObjectMapper().readTree(openApi.toJson(api));
  }

  @Test
  void entityMetadataExposesTheEngineAndClassifier() {
    var order =
        metadata(ModelParser.parse(MODEL)).stream()
            .filter(entity -> entity.name().equals("order"))
            .findFirst()
            .orElseThrow();
    JsonNode tasks = new ObjectMapper().valueToTree(order.aiTasks());

    assertEquals("classifier", tasks.at("/classify/engine").asText());
    assertEquals("orders", tasks.at("/classify/classifier").asText());
    assertEquals("llm", tasks.at("/summarize/engine").asText());
    assertTrue(tasks.at("/summarize/classifier").isNull());
  }

  @Test
  void theDocumentNamesTheModelAndAsksForABearerToken() throws Exception {
    JsonNode document = document();

    assertEquals("DX-OS model shop", document.at("/info/title").asText());
    assertEquals("v1", document.at("/info/version").asText());
    assertEquals("bearer", document.at("/components/securitySchemes/bearerAuth/scheme").asText());
    assertTrue(document.at("/security/0").has("bearerAuth"));
  }

  @Test
  void everyEntityHasItsCollectionAndItsRecordPaths() throws Exception {
    JsonNode paths = document().get("paths");

    assertTrue(paths.has("/api/v1/data/shop/order"), paths.fieldNames().toString());
    assertTrue(paths.has("/api/v1/data/shop/order/{id}"));
    assertTrue(paths.has("/api/v1/data/shop/note"));
    assertTrue(paths.get("/api/v1/data/shop/order").has("get"));
    assertTrue(paths.get("/api/v1/data/shop/order").has("post"));
    assertTrue(paths.get("/api/v1/data/shop/order/{id}").has("patch"));
  }

  @Test
  void aProcessEntityHasItsActionPaths() throws Exception {
    JsonNode paths = document().get("paths");

    assertTrue(
        paths.fieldNames().hasNext()
            && java.util.stream.StreamSupport.stream(
                    java.util.Spliterators.spliteratorUnknownSize(paths.fieldNames(), 0), false)
                .anyMatch(p -> p.contains("/order/{id}/actions/")),
        paths.fieldNames().toString());
    assertFalse(
        java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(paths.fieldNames(), 0), false)
            .anyMatch(p -> p.contains("/note/{id}/actions/")));
  }

  @Test
  void theSchemaOfAnEntityListsItsFieldsWithTheirTypes() throws Exception {
    JsonNode schemas = document().at("/components/schemas");
    String name =
        java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(schemas.fieldNames(), 0), false)
            .filter(n -> n.toLowerCase().contains("order"))
            .findFirst()
            .orElseThrow();

    JsonNode properties = schemas.get(name).get("properties");
    assertNotNull(properties, name);
    assertTrue(properties.has("title"));
    assertTrue(properties.has("amount"));
    assertEquals("number", properties.get("amount").get("type").asText());
    assertTrue(properties.has("kind"));
  }

  @Test
  void theErrorCodesOfTheCoreAreDocumented() throws Exception {
    String text = document().toString();

    for (String code : ModelOpenApi.ERROR_CODES) {
      assertTrue(text.contains(code), code);
    }
  }
}
