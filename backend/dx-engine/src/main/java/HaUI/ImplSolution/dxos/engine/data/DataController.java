// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient.Person;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data/{model}/{entity}")
@Tag(name = "Data", description = "Records of any entity of an applied model; the model decides"
    + " the fields, the roles and the rows each role reaches")
public class DataController {

  private static final String REPLAYED = "Idempotent-Replayed";

  private final ModelRegistry registry;
  private final DataService service;
  private final IdempotencyService idempotency;

  public DataController(ModelRegistry registry, DataService service,
      IdempotencyService idempotency) {
    this.registry = registry;
    this.service = service;
    this.idempotency = idempotency;
  }

  @GetMapping
  @Operation(summary = "List records",
      description = "filter.<field>=<value> (also filter.<field>.<op> with op eq, ne, lt, lte,"
          + " gt, gte, like or in; in takes at least one comma-separated value), sort=<field>,"
          + " order=asc|desc, page (from 0) and size (at most 200). A role sees only the rows its"
          + " access reaches; 403 when no role of the caller reaches the entity.")
  public ApiResponse<PageResponse<Map<String, Object>>> list(@PathVariable String model,
      @PathVariable String entity, @RequestParam Map<String, String> params) {
    Model m = registry.require(model);
    return ApiResponse.success(service.list(m, entity(m, entity), params));
  }

  @GetMapping("/stats")
  @Operation(summary = "Count the records the caller can see",
      description = "Takes the filter.<field> parameters of the list. total counts the rows;"
          + " for an entity with a process, byStatus counts them per status and overdue counts"
          + " those whose handle_by is before computedAt; avgDecisionSeconds is the mean time from"
          + " creation to the final state over the records that reached one (a withdrawn record"
          + " counts), avgOpenAgeSeconds and oldestOpenAgeSeconds are the mean and largest age at"
          + " computedAt of the records that did not, each null when there is no such record;"
          + " an entity with a deadline also has closedOnTime and closedLate, the decided records"
          + " (a withdrawn one excluded) without and with an overdue line in their history; a"
          + " record that was overdue and later decided in time still counts as late;"
          + " byUnit counts them per unit when the"
          + " entity has a unit field. The same rows as the list with the same filters, so"
          + " filter.handle_by.lt=<computedAt> lists the overdue ones. A query that runs longer"
          + " than 10 seconds is cut.")
  public ApiResponse<Map<String, Object>> stats(@PathVariable String model,
      @PathVariable String entity, @RequestParam Map<String, String> params) {
    Model m = registry.require(model);
    return ApiResponse.success(service.stats(m, entity(m, entity), params));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get one record")
  public ApiResponse<Map<String, Object>> get(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id) {
    Model m = registry.require(model);
    return ApiResponse.success(service.get(m, entity(m, entity), id));
  }

  @PostMapping
  @Operation(summary = "Create a record",
      description = "400 lists the problem of each field. The status of an entity with a process"
          + " cannot be sent (403); the process sets it. With an Idempotency-Key header a repeat of"
          + " the request gets the first answer, marked by the header Idempotent-Replayed: true,"
          + " and creates nothing; the same key with another body is 409 with data.reason"
          + " IDEMPOTENCY_KEY_REUSED. A request that fails keeps no key, so it may be sent again."
          + " A key is kept for a day.")
  public ApiResponse<Map<String, Object>> create(@PathVariable String model,
      @PathVariable String entity, @RequestBody Map<String, Object> body,
      @RequestHeader(value = IdempotencyService.HEADER, required = false) String key,
      HttpServletResponse response) {
    Model m = registry.require(model);
    return answer(idempotency.once(key, "create", model + "/" + entity, body,
        () -> service.create(m, entity(m, entity), body)), response);
  }

  @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
  @Operation(summary = "Change the given fields of a record",
      description = "The body may carry version, as read with the record: the change then applies"
          + " only to that version, and 409 ERR_CONCURRENT_MODIFICATION answers when the record"
          + " changed in between. Without version the change applies to the current record.")
  public ApiResponse<Map<String, Object>> update(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id, @RequestBody Map<String, Object> body) {
    Model m = registry.require(model);
    return ApiResponse.success(service.update(m, entity(m, entity), id, body));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a record",
      description = "Refused with 409 for an entity with a process: the owner withdraws the record"
          + " with the withdraw action instead, and it stays in a final state.")
  public ApiResponse<Void> delete(@PathVariable String model, @PathVariable String entity,
      @PathVariable long id) {
    Model m = registry.require(model);
    service.delete(m, entity(m, entity), id);
    return ApiResponse.success(null);
  }

  @PostMapping("/{id}/actions/{action}")
  @Operation(summary = "Take an action of the process of a record",
      description = "The metadata lists the actions of an entity, the state each one starts from"
          + " and who may take it. The body may carry {\"note\": \"...\"}, required by the"
          + " actions that say so. 403 when the caller may not take it, or when the record is"
          + " assigned to someone else, with data.reason naming why (for example SELF_DECISION,"
          + " OWNER_ONLY, HANDLER_ONLY, ASSIGNED_TO_OTHER); 409 when the record is in another"
          + " state, with data.reason WRONG_STATE or RECORD_CLOSED. A terminal state"
          + " stores decided_by and decided_at. The owner can take the action withdraw from any"
          + " state that is not final. Optional version and modelVersion reject a changed record"
          + " or model with 409; a changed model has data.reason MODEL_VERSION_CHANGED."
          + " An Idempotency-Key repeat returns the first answer without executing the action again.")
  public ApiResponse<Map<String, Object>> act(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id, @PathVariable String action,
      @RequestBody(required = false) Map<String, String> body,
      @RequestHeader(value = IdempotencyService.HEADER, required = false) String key,
      HttpServletResponse response) {
    Model m = registry.require(model);
    String note = body == null ? null : body.get("note");
    RecordPreconditions conditions = RecordPreconditions.from(body);
    return answer(
        idempotency.once(key, "act", model + "/" + entity + "/" + id + "/" + action, body, () -> {
          registry.checkVersion(model, conditions.modelVersion());
          return service.act(m, entity(m, entity), id, action, note, conditions.version());
        }), response);
  }

  // The answer of a request, marked when it is the one kept for its Idempotency-Key.
  private static ApiResponse<Map<String, Object>> answer(IdempotencyService.Outcome outcome,
      HttpServletResponse response) {
    if (outcome.replayed()) {
      response.setHeader(REPLAYED, "true");
    }
    return ApiResponse.success(outcome.answer());
  }

  @PostMapping("/{id}/assign")
  @Operation(summary = "Assign a record to a user who can handle it",
      description = "The body is {\"assignee\": \"<user id>\"}, with null to release it. The"
          + " handler role and roles writing to all rows or to their units may assign. 400 when"
          + " that user, the caller included, cannot handle the record. Optional version and"
          + " modelVersion reject changed snapshots with 409; a changed model has data.reason"
          + " MODEL_VERSION_CHANGED.")
  public ApiResponse<Map<String, Object>> assign(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id, @RequestBody Map<String, String> body) {
    Model m = registry.require(model);
    RecordPreconditions conditions = RecordPreconditions.from(body);
    registry.checkVersion(model, conditions.modelVersion());
    return ApiResponse.success(
        service.assign(m, entity(m, entity), id, body.get("assignee"), conditions.version()));
  }

  @GetMapping("/{id}/handlers")
  @Operation(summary = "The users a record can be assigned to")
  public ApiResponse<List<Person>> handlers(@PathVariable String model, @PathVariable String entity,
      @PathVariable long id) {
    Model m = registry.require(model);
    return ApiResponse.success(service.handlers(m, entity(m, entity), id));
  }

  @GetMapping("/{id}/events")
  @Operation(summary = "The history of a record, oldest first",
      description = "Who did what and when, with the states and the note of each action. The"
          + " detail of an update is a JSON object with, for each changed field, the value it held"
          + " and the value it holds now, as text cut at 200 characters, or null; an update"
          + " written earlier has the names of the changed fields separated by commas.")
  public ApiResponse<List<Map<String, Object>>> events(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id) {
    Model m = registry.require(model);
    return ApiResponse.success(service.events(m, entity(m, entity), id));
  }

  private static Entity entity(Model model, String name) {
    return model.entity(name)
        .orElseThrow(() -> new ResourceNotFoundException("Entity " + name + " not found"));
  }
}
