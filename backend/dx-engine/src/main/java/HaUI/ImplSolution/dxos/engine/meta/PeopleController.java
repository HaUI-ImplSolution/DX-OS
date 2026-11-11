// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.meta;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient.Account;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient.Person;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/people")
@Tag(name = "People", description = "Find a person to put in a user field, by name")
public class PeopleController {

  static final int MIN_SEARCH = 2;
  static final int MAX_FOUND = 20;
  static final int MAX_IDS = 50;

  private final KeycloakAdminClient keycloak;

  public PeopleController(KeycloakAdminClient keycloak) {
    this.keycloak = keycloak;
  }

  @GetMapping
  @Operation(
      summary = "People by search text or by id, as id and display name only",
      description =
          "q finds up to 20 accounts whose name, username or email contains the text;"
              + " it is empty for text shorter than 2 characters. ids is a comma-separated list of at"
              + " most 50 account ids and returns the accounts that exist. Any signed-in user may"
              + " call it; nothing but id and display name is returned.")
  public ApiResponse<List<Person>> people(
      @RequestParam(required = false) String q, @RequestParam(required = false) String ids) {
    if (ids != null && !ids.isBlank()) {
      List<String> wanted =
          List.of(ids.split(",")).stream()
              .map(String::trim)
              .filter(id -> !id.isEmpty())
              .distinct()
              .toList();
      if (wanted.size() > MAX_IDS) {
        throw new ModelValidationException("ids", "at most " + MAX_IDS + " ids");
      }
      return ApiResponse.success(
          wanted.stream()
              .map(keycloak::findAccount)
              .flatMap(java.util.Optional::stream)
              .map(PeopleController::person)
              .toList());
    }
    String text = q == null ? "" : q.trim();
    if (text.length() < MIN_SEARCH) {
      return ApiResponse.success(List.of());
    }
    return ApiResponse.success(
        keycloak.searchAccounts(text, 0, MAX_FOUND).items().stream()
            .map(PeopleController::person)
            .toList());
  }

  private static Person person(Account account) {
    return new Person(account.id(), account.name());
  }
}
